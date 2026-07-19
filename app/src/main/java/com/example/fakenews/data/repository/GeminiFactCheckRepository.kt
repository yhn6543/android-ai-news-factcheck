package com.example.fakenews.data.repository

import android.util.Log
import com.example.fakenews.BuildConfig
import com.example.fakenews.data.model.FactCheckResult
import com.example.fakenews.data.remote.GeminiApi
import com.example.fakenews.data.remote.GeminiApiClient
import com.example.fakenews.data.remote.GeminiConfig
import com.example.fakenews.data.remote.dto.GeminiContentDto
import com.example.fakenews.data.remote.dto.GeminiPartDto
import com.example.fakenews.data.remote.dto.GeminiRequestDto
import com.example.fakenews.data.remote.dto.googleSearchTools
import com.example.fakenews.util.FactCheckPostProcessor
import com.example.fakenews.util.GeminiFactCheckResultParser
import com.example.fakenews.util.GeminiPromptBuilder
import java.io.IOException
import kotlinx.coroutines.delay
import kotlinx.serialization.SerializationException

class GeminiFactCheckRepository(
    private val apiKey: String = BuildConfig.GEMINI_API_KEY,
    private val api: GeminiApi = GeminiApiClient.api,
    private val modelName: String = GeminiConfig.DEFAULT_MODEL,
    private val fallbackModelName: String = GeminiConfig.FALLBACK_MODEL,
    private val retryDelay: suspend (Long) -> Unit = { delay(it) }
) : FactCheckRepository {
    override suspend fun analyze(text: String): FactCheckResult {
        val trimmedApiKey = apiKey.trim()
        if (trimmedApiKey.isBlank()) {
            throw FactCheckException.MissingApiKey
        }

        val prompt = GeminiPromptBuilder.buildFactCheckPrompt(text)
        val request = GeminiRequestDto(
            contents = listOf(
                GeminiContentDto(
                    parts = listOf(GeminiPartDto(text = prompt))
                )
            ),
            tools = googleSearchTools()
        )

        val models = listOf(modelName, fallbackModelName)
            .filter { model -> model.isNotBlank() }
            .distinct()
            .ifEmpty { GeminiConfig.MODEL_FALLBACK_CHAIN }
        var lastError: FactCheckException = FactCheckException.Unknown

        models.forEachIndexed { modelIndex, model ->
            val usingFallback = modelIndex > 0
            if (usingFallback) {
                logGemini("fallback_model model=$model")
            }

            runCatching {
                return analyzeWithRetry(
                    model = model,
                    apiKey = trimmedApiKey,
                    request = request,
                    originalText = text,
                    usingFallback = usingFallback
                )
            }.onFailure { error ->
                val mappedError = GeminiErrorMapper.fromThrowable(error)
                lastError = mappedError
                if (
                    GeminiRetryPolicy.shouldFallbackToNextModel(mappedError) &&
                    modelIndex < models.lastIndex
                ) {
                    logGemini(
                        "fallback_scheduled fromModel=$model toModel=${models[modelIndex + 1]} " +
                            "errorType=${GeminiErrorMapper.errorType(mappedError)}"
                    )
                    return@forEachIndexed
                }
                throw mappedError
            }
        }

        throw lastError
    }

    private suspend fun analyzeWithRetry(
        model: String,
        apiKey: String,
        request: GeminiRequestDto,
        originalText: String,
        usingFallback: Boolean
    ): FactCheckResult {
        var retryCount = 0
        while (true) {
            try {
                logGemini(
                    "request model=$model retryAttempt=$retryCount fallback=$usingFallback"
                )
                return analyzeOnce(
                    model = model,
                    apiKey = apiKey,
                    request = request,
                    originalText = originalText
                )
            } catch (error: Throwable) {
                val mappedError = GeminiErrorMapper.fromThrowable(error)
                logGemini(
                    "failure model=$model retryAttempt=$retryCount fallback=$usingFallback " +
                        "errorType=${GeminiErrorMapper.errorType(mappedError)} " +
                        "httpStatus=${httpStatusCode(error)} rawError=${rawErrorPreview(error)}",
                    error
                )

                if (
                    retryCount >= GeminiRetryPolicy.MAX_RETRY_COUNT ||
                    !GeminiRetryPolicy.shouldRetry(mappedError)
                ) {
                    throw mappedError
                }

                val delayMillis = GeminiRetryPolicy.backoffDelayMillis(retryCount)
                retryCount += 1
                logGemini(
                    "retry_scheduled model=$model retryAttempt=$retryCount " +
                        "delayMillis=$delayMillis errorType=${GeminiErrorMapper.errorType(mappedError)}"
                )
                retryDelay(delayMillis)
            }
        }
    }

    private suspend fun analyzeOnce(
        model: String,
        apiKey: String,
        request: GeminiRequestDto,
        originalText: String
    ): FactCheckResult {
        return try {
            val response = api.generateContent(
                model = model,
                apiKey = apiKey,
                request = request
            )

            val rawErrorBody = if (response.isSuccessful) {
                null
            } else {
                response.errorBody()?.string()
            }
            logGemini(
                "response model=$model httpStatus=${response.code()} " +
                    "rawError=${rawErrorBody?.sanitizeForLog().orEmpty()}"
            )

            if (!response.isSuccessful) {
                throw GeminiErrorMapper.fromHttpStatus(
                    statusCode = response.code(),
                    rawErrorBody = rawErrorBody
                )
            }

            val body = response.body() ?: throw FactCheckException.ParseFailure
            val responseText = body.firstTextOrNull()?.takeIf { it.isNotBlank() }
                ?: throw FactCheckException.ParseFailure
            val groundingMetadata = body.firstGroundingMetadataOrEmpty()

            val parsedResult = try {
                GeminiFactCheckResultParser.parse(rawText = responseText)
            } catch (error: SerializationException) {
                logGemini("parse_failed responseLength=${responseText.length}", error)
                throw FactCheckException.ParseFailure
            } catch (error: IllegalArgumentException) {
                logGemini("parse_failed responseLength=${responseText.length}", error)
                throw FactCheckException.ParseFailure
            }

            val processedResult = FactCheckPostProcessor.process(
                result = parsedResult,
                groundingMetadata = groundingMetadata,
                originalText = originalText
            )
            logConfidence(
                parsedResult = parsedResult,
                processedResult = processedResult,
                groundingMetadata = groundingMetadata
            )
            processedResult
        } catch (error: FactCheckException) {
            throw error
        } catch (error: IOException) {
            throw GeminiErrorMapper.fromThrowable(error)
        } catch (error: Exception) {
            val mappedError = GeminiErrorMapper.fromThrowable(error)
            logGemini(
                "unknown_failure errorType=${GeminiErrorMapper.errorType(mappedError)}",
                error
            )
            throw mappedError
        }
    }

    private fun logConfidence(
        parsedResult: FactCheckResult,
        processedResult: FactCheckResult,
        groundingMetadata: com.example.fakenews.data.model.GeminiGroundingMetadata
    ) {
        if (!BuildConfig.DEBUG) return
        runCatching {
            Log.d(
                TAG_FACT_CONFIDENCE,
                "rawConfidenceFromGemini=${parsedResult.rawConfidenceFromGemini} " +
                    "rawConfidenceType=${parsedResult.rawConfidenceType} " +
                    "normalizedConfidence=${parsedResult.confidenceScore} " +
                    "ambiguousLowInteger=${parsedResult.ambiguousLowInteger} " +
                    "lowIntegerConfidenceWarning=${parsedResult.ambiguousLowInteger && parsedResult.confidenceScore <= 10} " +
                    "confidenceBeforePostProcess=${processedResult.confidenceBeforePostProcess} " +
                    "confidenceAfterPostProcess=${processedResult.confidenceAfterPostProcess} " +
                    "finalConfidence=${processedResult.confidenceScore} " +
                    "verdictBeforePostProcess=${parsedResult.verdict} " +
                    "verdictAfterPostProcess=${processedResult.verdict} " +
                    "rawSourceCount=${groundingMetadata.rawSourceCount} " +
                    "uniqueSourceCount=${groundingMetadata.uniqueSourceCount} " +
                    "uniqueUriSourceCount=${groundingMetadata.uniqueUriSourceCount} " +
                    "groundingWebSearchQueriesCount=${groundingMetadata.webSearchQueries.size} " +
                    "evidenceDirectness=${processedResult.evidenceDirectness} " +
                    "claimCategory=${processedResult.claimCategory} " +
                    "timeSensitivity=${processedResult.timeSensitivity} " +
                    "needsTimeContext=${processedResult.needsTimeContext} " +
                    "confidenceAdjustmentReason=${processedResult.confidenceAdjustmentReason}"
            )
        }
    }

    private fun httpStatusCode(error: Throwable): String =
        when (error) {
            is FactCheckException.HttpError -> error.code.toString()
            else -> ""
        }

    private fun rawErrorPreview(error: Throwable): String =
        when (error) {
            is FactCheckException.HttpError -> error.rawErrorBody?.sanitizeForLog().orEmpty()
            else -> ""
        }

    private fun String.sanitizeForLog(): String =
        replace(Regex("\\s+"), " ")
            .take(DEBUG_TEXT_LIMIT)

    private fun logGemini(
        message: String,
        error: Throwable? = null
    ) {
        runCatching {
            if (error == null) {
                Log.d(TAG_GEMINI_API, message)
            } else {
                Log.d(TAG_GEMINI_API, message, error)
            }
        }
    }
    private companion object {
        const val TAG_GEMINI_API = "GEMINI_API"
        const val TAG_FACT_CONFIDENCE = "FACT_CONFIDENCE"
        const val DEBUG_TEXT_LIMIT = 500
    }
}

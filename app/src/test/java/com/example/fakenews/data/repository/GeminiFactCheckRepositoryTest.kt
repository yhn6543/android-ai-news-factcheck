package com.example.fakenews.data.repository

import com.example.fakenews.data.remote.GeminiApi
import com.example.fakenews.data.remote.GeminiConfig
import com.example.fakenews.data.model.FactCheckVerdict
import com.example.fakenews.data.remote.dto.GeminiCandidateDto
import com.example.fakenews.data.remote.dto.GeminiRequestDto
import com.example.fakenews.data.remote.dto.GeminiResponseContentDto
import com.example.fakenews.data.remote.dto.GeminiResponseDto
import com.example.fakenews.data.remote.dto.GeminiPartTextDto
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response

class GeminiFactCheckRepositoryTest {
    @Test
    fun missingApiKeyThrowsMissingApiKeyErrorBeforeNetworkCall() = runTest {
        val repository = GeminiFactCheckRepository(apiKey = "")

        val error = runCatching {
            repository.analyze("검증할 문장")
        }.exceptionOrNull()

        assertEquals(FactCheckException.MissingApiKey, error)
    }

    @Test
    fun serverOverloadRetriesPrimaryModelThenUsesFallbackModel() = runTest {
        val api = FallbackAfterPrimary503Api()
        val repository = GeminiFactCheckRepository(
            apiKey = "test-key",
            api = api,
            retryDelay = { _ -> }
        )

        val result = repository.analyze("검증할 문장")

        assertEquals("요약", result.finalSummary)
        assertEquals(
            List(GeminiRetryPolicy.MAX_RETRY_COUNT + 1) { GeminiConfig.DEFAULT_MODEL } +
                GeminiConfig.FALLBACK_MODEL,
            api.requestedModels
        )
        assertTrue(api.requestedModels.contains(GeminiConfig.FALLBACK_MODEL))
    }

    @Test
    fun timeSensitiveDefinitiveVerdictWithoutGroundingNeedsMoreContext() = runTest {
        val repository = GeminiFactCheckRepository(
            apiKey = "test-key",
            api = TimeSensitiveWithoutGroundingApi(),
            retryDelay = { _ -> }
        )

        val result = repository.analyze("삼성전자 주가 40만원 돌파")

        assertEquals(FactCheckVerdict.NEEDS_MORE_CONTEXT, result.verdict)
        assertEquals(0, result.relatedArticleCount)
        assertTrue(result.missingContext.any { context -> context.contains("날짜") })
        assertEquals(30, result.confidenceScore)
    }

    private class FallbackAfterPrimary503Api : GeminiApi {
        val requestedModels = mutableListOf<String>()

        override suspend fun generateContent(
            model: String,
            apiKey: String,
            request: GeminiRequestDto
        ): Response<GeminiResponseDto> {
            requestedModels += model
            return if (model == GeminiConfig.DEFAULT_MODEL) {
                Response.error(
                    503,
                    """{"error":{"message":"busy"}}"""
                        .toResponseBody("application/json".toMediaType())
                )
            } else {
                Response.success(successResponse())
            }
        }

        private fun successResponse(): GeminiResponseDto =
            GeminiResponseDto(
                candidates = listOf(
                    GeminiCandidateDto(
                        content = GeminiResponseContentDto(
                            parts = listOf(
                                GeminiPartTextDto(
                                    text = """
                                    {
                                      "finalSummary": "요약",
                                      "riskLevel": "LOW",
                                      "fakeNewsPossibility": 10,
                                      "logicAnalysis": "논리",
                                      "emotionalBiasAnalysis": "감정",
                                      "sourceReliabilityAnalysis": "출처",
                                      "exaggerationAnalysis": "과장",
                                      "factsVsOpinions": "구분",
                                      "additionalChecks": ["확인"],
                                      "recommendedSearchKeywords": ["경제"]
                                    }
                                    """.trimIndent()
                                )
                            )
                        )
                    )
                )
            )
    }

    private class TimeSensitiveWithoutGroundingApi : GeminiApi {
        override suspend fun generateContent(
            model: String,
            apiKey: String,
            request: GeminiRequestDto
        ): Response<GeminiResponseDto> =
            Response.success(
                GeminiResponseDto(
                    candidates = listOf(
                        GeminiCandidateDto(
                            content = GeminiResponseContentDto(
                                parts = listOf(
                                    GeminiPartTextDto(
                                        text = """
                                        {
                                          "verdict": "TRUE",
                                          "confidenceScore": 95,
                                          "finalSummary": "사실입니다.",
                                          "reason": "모델 판단",
                                          "evidenceSummary": "",
                                          "missingContext": [],
                                          "recommendedChecks": [],
                                          "searchQueriesUsed": ["삼성전자 주가"],
                                          "claimCategory": "FINANCE",
                                          "timeSensitivity": "HIGH",
                                          "needsTimeContext": true
                                        }
                                        """.trimIndent()
                                    )
                                )
                            )
                        )
                    )
                )
            )
    }
}

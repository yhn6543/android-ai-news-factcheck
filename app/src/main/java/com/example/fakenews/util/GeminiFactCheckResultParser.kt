package com.example.fakenews.util

import com.example.fakenews.data.model.ClaimAnalysis
import com.example.fakenews.data.model.FactCheckClaimCategory
import com.example.fakenews.data.model.FactCheckResult
import com.example.fakenews.data.model.FactCheckRiskLevel
import com.example.fakenews.data.model.FactCheckTimeSensitivity
import com.example.fakenews.data.model.FactCheckVerdict
import com.example.fakenews.data.model.NewsArticle
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

object GeminiFactCheckResultParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun parse(
        rawText: String,
        relatedArticles: List<NewsArticle> = emptyList(),
        claimAnalysis: ClaimAnalysis? = null
    ): FactCheckResult {
        val cleanedJson = JsonCleaner.clean(rawText)
        val dto = json.decodeFromString<GeminiFactCheckJsonDto>(cleanedJson)
        val verdict = parseVerdict(dto.verdict, dto.riskLevel)
        val confidenceParseResult = ConfidenceNormalizer.normalize(
            rawElement = dto.confidenceScore,
            defaultValue = defaultConfidenceFor(verdict)
        )
        val confidenceScore = confidenceParseResult.normalizedValue
        val fakeNewsPossibility = dto.fakeNewsPossibility.coerceInt(defaultValue = defaultPossibilityFor(verdict))
        val riskLevel = parseRiskLevel(dto.riskLevel, verdict)
        val recommendedChecks = dto.recommendedChecks.toStringList()
        val additionalChecks = dto.additionalChecks.toStringList().ifEmpty { recommendedChecks }
        val searchQueriesUsed = dto.searchQueriesUsed.toStringList()
        val recommendedSearchKeywords = dto.recommendedSearchKeywords.toStringList()
            .ifEmpty { searchQueriesUsed }

        return FactCheckResult(
            finalSummary = dto.finalSummary,
            riskLevel = riskLevel,
            fakeNewsPossibility = fakeNewsPossibility,
            logicAnalysis = dto.logicAnalysis.ifBlank { dto.reason },
            emotionalBiasAnalysis = dto.emotionalBiasAnalysis,
            sourceReliabilityAnalysis = dto.sourceReliabilityAnalysis.ifBlank { dto.evidenceSummary },
            exaggerationAnalysis = dto.exaggerationAnalysis,
            factsVsOpinions = dto.factsVsOpinions,
            additionalChecks = additionalChecks,
            recommendedSearchKeywords = recommendedSearchKeywords,
            relatedArticleCount = relatedArticles.size,
            relatedArticles = relatedArticles,
            verdict = verdict,
            confidenceScore = confidenceScore,
            reason = dto.reason.ifBlank { dto.logicAnalysis },
            evidenceSummary = dto.evidenceSummary.ifBlank { dto.sourceReliabilityAnalysis },
            contradictingEvidence = dto.contradictingEvidence.toStringList(),
            missingContext = dto.missingContext.toStringList(),
            recommendedChecks = recommendedChecks.ifEmpty { additionalChecks },
            claimAnalysis = claimAnalysis,
            searchQueriesUsed = searchQueriesUsed,
            usedInternalKnowledge = dto.usedInternalKnowledge.coerceBoolean(defaultValue = false),
            claimCategory = parseClaimCategory(dto.claimCategory),
            timeSensitivity = parseTimeSensitivity(dto.timeSensitivity),
            needsTimeContext = dto.needsTimeContext.coerceBoolean(defaultValue = false),
            confidenceParseSuccess = confidenceParseResult.parseSuccess,
            rawConfidenceFromGemini = confidenceParseResult.rawValue,
            rawConfidenceType = confidenceParseResult.rawType,
            ambiguousLowInteger = confidenceParseResult.ambiguousLowInteger,
            confidenceAdjustmentReason = confidenceParseResult.parseReason
        )
    }

    private fun parseVerdict(
        rawVerdict: String,
        rawRiskLevel: String
    ): FactCheckVerdict =
        runCatching {
            FactCheckVerdict.valueOf(rawVerdict.trim().uppercase())
        }.getOrElse {
            when (parseRiskLevel(rawRiskLevel, FactCheckVerdict.UNVERIFIABLE)) {
                FactCheckRiskLevel.LOW -> FactCheckVerdict.TRUE
                FactCheckRiskLevel.MEDIUM -> FactCheckVerdict.PARTLY_TRUE
                FactCheckRiskLevel.HIGH -> FactCheckVerdict.MISLEADING
                FactCheckRiskLevel.UNKNOWN -> FactCheckVerdict.UNVERIFIABLE
            }
        }

    private fun parseRiskLevel(
        rawRiskLevel: String,
        verdict: FactCheckVerdict
    ): FactCheckRiskLevel =
        runCatching {
            FactCheckRiskLevel.valueOf(rawRiskLevel.trim().uppercase())
        }.getOrDefault(riskLevelFor(verdict))

    private fun parseClaimCategory(rawCategory: String): FactCheckClaimCategory =
        runCatching {
            FactCheckClaimCategory.valueOf(rawCategory.trim().uppercase())
        }.getOrDefault(FactCheckClaimCategory.UNKNOWN)

    private fun parseTimeSensitivity(rawTimeSensitivity: String): FactCheckTimeSensitivity =
        runCatching {
            FactCheckTimeSensitivity.valueOf(rawTimeSensitivity.trim().uppercase())
        }.getOrDefault(FactCheckTimeSensitivity.UNKNOWN)

    private fun riskLevelFor(verdict: FactCheckVerdict): FactCheckRiskLevel =
        when (verdict) {
            FactCheckVerdict.TRUE -> FactCheckRiskLevel.LOW
            FactCheckVerdict.PARTLY_TRUE,
            FactCheckVerdict.NEEDS_MORE_CONTEXT -> FactCheckRiskLevel.MEDIUM
            FactCheckVerdict.FALSE,
            FactCheckVerdict.MISLEADING -> FactCheckRiskLevel.HIGH
            FactCheckVerdict.UNVERIFIABLE -> FactCheckRiskLevel.UNKNOWN
        }

    private fun defaultPossibilityFor(verdict: FactCheckVerdict): Int =
        when (verdict) {
            FactCheckVerdict.TRUE -> 5
            FactCheckVerdict.PARTLY_TRUE -> 45
            FactCheckVerdict.FALSE -> 90
            FactCheckVerdict.MISLEADING -> 75
            FactCheckVerdict.UNVERIFIABLE,
            FactCheckVerdict.NEEDS_MORE_CONTEXT -> 50
        }

    private fun defaultConfidenceFor(verdict: FactCheckVerdict): Int =
        when (verdict) {
            FactCheckVerdict.UNVERIFIABLE,
            FactCheckVerdict.NEEDS_MORE_CONTEXT -> 30
            else -> 70
        }

    private fun JsonElement?.coerceInt(defaultValue: Int): Int {
        val rawValue = this?.jsonPrimitive ?: return defaultValue.coerceIn(0, 100)
        return (rawValue.intOrNull ?: rawValue.content.toIntOrNull() ?: defaultValue).coerceIn(0, 100)
    }

    private fun JsonElement?.coerceBoolean(defaultValue: Boolean): Boolean {
        val primitive = this?.jsonPrimitive ?: return defaultValue
        return primitive.booleanOrNull
            ?: primitive.contentOrNull?.toBooleanStrictOrNull()
            ?: defaultValue
    }

    private fun JsonElement?.toStringList(): List<String> =
        when (this) {
            null,
            JsonNull -> emptyList()
            is JsonArray -> mapNotNull { element ->
                (element as? JsonPrimitive)
                    ?.contentOrNull
                    ?.trim()
                    ?.takeIf { value -> value.isNotBlank() }
            }
            is JsonPrimitive -> contentOrNull
                ?.trim()
                ?.takeIf { value -> value.isNotBlank() }
                ?.let(::listOf)
                .orEmpty()
            else -> emptyList()
        }
}

@Serializable
private data class GeminiFactCheckJsonDto(
    val verdict: String = "",
    val confidenceScore: JsonElement? = null,
    val finalSummary: String = "",
    val reason: String = "",
    val evidenceSummary: String = "",
    val contradictingEvidence: JsonElement? = null,
    val missingContext: JsonElement? = null,
    val recommendedChecks: JsonElement? = null,
    val searchQueriesUsed: JsonElement? = null,
    val usedInternalKnowledge: JsonElement? = null,
    val claimCategory: String = "UNKNOWN",
    val timeSensitivity: String = "UNKNOWN",
    val needsTimeContext: JsonElement? = null,
    val riskLevel: String = "UNKNOWN",
    val fakeNewsPossibility: JsonElement? = null,
    val logicAnalysis: String = "",
    val emotionalBiasAnalysis: String = "",
    val sourceReliabilityAnalysis: String = "",
    val exaggerationAnalysis: String = "",
    val factsVsOpinions: String = "",
    val additionalChecks: JsonElement? = null,
    val recommendedSearchKeywords: JsonElement? = null
)

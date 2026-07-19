package com.example.fakenews.util

import com.example.fakenews.data.model.EvidenceDirectness
import com.example.fakenews.data.model.FactCheckClaimCategory
import com.example.fakenews.data.model.GeminiGroundingMetadata
import java.util.Locale

object EvidenceDirectnessEvaluator {
    fun evaluate(
        originalText: String,
        groundingMetadata: GeminiGroundingMetadata,
        evidenceSummary: String,
        reason: String,
        finalSummary: String,
        claimCategory: FactCheckClaimCategory
    ): EvidenceDirectness {
        if (groundingMetadata.rawSourceCount == 0) return EvidenceDirectness.NONE

        val haystack = buildString {
            groundingMetadata.groundingChunks.forEach { chunk ->
                append(' ')
                append(chunk.web?.title.orEmpty())
                append(' ')
                append(chunk.web?.uri.orEmpty())
            }
            append(' ')
            append(evidenceSummary)
            append(' ')
            append(reason)
            append(' ')
            append(finalSummary)
        }.normalizedText()

        val claimTokens = originalText.extractMeaningfulTokens()
        if (claimTokens.isEmpty()) return EvidenceDirectness.WEAK

        val matchedTokenCount = claimTokens.count { token -> haystack.contains(token.normalizedText()) }
        val coverage = matchedTokenCount.toDouble() / claimTokens.size
        val hasUriSource = groundingMetadata.uniqueUriSourceCount > 0
        val actionMatch = actionSynonymGroups.any { group ->
            group.any { synonym -> originalText.contains(synonym, ignoreCase = true) } &&
                group.any { synonym -> haystack.contains(synonym.normalizedText()) }
        }
        val numberMatch = originalText.extractNumbers().any { number -> haystack.contains(number) }

        val evaluated = when (claimCategory) {
            FactCheckClaimCategory.SPORTS -> evaluateSports(coverage, actionMatch, hasUriSource)
            FactCheckClaimCategory.FINANCE -> evaluateFinance(coverage, numberMatch, actionMatch, hasUriSource)
            FactCheckClaimCategory.RECENT_EVENT,
            FactCheckClaimCategory.POLITICS,
            FactCheckClaimCategory.SOCIAL -> evaluateRecentOrSocial(coverage, hasUriSource)
            else -> evaluateGeneral(coverage, hasUriSource)
        }

        return if (!hasUriSource && evaluated == EvidenceDirectness.DIRECT) {
            EvidenceDirectness.PARTIAL
        } else {
            evaluated
        }
    }

    private fun evaluateSports(
        coverage: Double,
        actionMatch: Boolean,
        hasUriSource: Boolean
    ): EvidenceDirectness =
        when {
            hasUriSource && coverage >= 0.55 && actionMatch -> EvidenceDirectness.DIRECT
            coverage >= 0.35 && actionMatch -> EvidenceDirectness.PARTIAL
            coverage > 0.0 -> EvidenceDirectness.WEAK
            else -> EvidenceDirectness.NONE
        }

    private fun evaluateFinance(
        coverage: Double,
        numberMatch: Boolean,
        actionMatch: Boolean,
        hasUriSource: Boolean
    ): EvidenceDirectness =
        when {
            hasUriSource && coverage >= 0.5 && (numberMatch || actionMatch) -> EvidenceDirectness.DIRECT
            coverage >= 0.3 && (numberMatch || actionMatch) -> EvidenceDirectness.PARTIAL
            coverage > 0.0 -> EvidenceDirectness.WEAK
            else -> EvidenceDirectness.NONE
        }

    private fun evaluateRecentOrSocial(
        coverage: Double,
        hasUriSource: Boolean
    ): EvidenceDirectness =
        when {
            hasUriSource && coverage >= 0.55 -> EvidenceDirectness.DIRECT
            coverage >= 0.35 -> EvidenceDirectness.PARTIAL
            coverage > 0.0 -> EvidenceDirectness.WEAK
            else -> EvidenceDirectness.NONE
        }

    private fun evaluateGeneral(
        coverage: Double,
        hasUriSource: Boolean
    ): EvidenceDirectness =
        when {
            hasUriSource && coverage >= 0.55 -> EvidenceDirectness.DIRECT
            coverage >= 0.3 -> EvidenceDirectness.PARTIAL
            coverage > 0.0 -> EvidenceDirectness.WEAK
            else -> EvidenceDirectness.NONE
        }

    private fun String.extractMeaningfulTokens(): List<String> =
        tokenPattern.findAll(this)
            .map { match -> match.value.trim() }
            .filter { token -> token.length >= 2 }
            .filterNot { token -> token in stopWords }
            .distinctBy { token -> token.normalizedText() }
            .toList()

    private fun String.extractNumbers(): List<String> =
        numberPattern.findAll(this)
            .map { match -> match.value }
            .toList()

    private fun String.normalizedText(): String =
        lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")

    private val tokenPattern = Regex("[가-힣A-Za-z0-9]+")
    private val numberPattern = Regex("\\d+(?:\\.\\d+)?")
    private val stopWords = setOf(
        "뉴스",
        "기사",
        "관련",
        "주장",
        "결과",
        "내용"
    )

    private val actionSynonymGroups = listOf(
        listOf("승리", "이김", "이겼다", "꺾었다", "제압"),
        listOf("패배", "졌다", "졌음", "석패"),
        listOf("돌파", "넘었다", "상회"),
        listOf("하락", "떨어졌다", "급락")
    )
}

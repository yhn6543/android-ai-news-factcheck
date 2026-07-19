package com.example.fakenews.util

import com.example.fakenews.data.model.FactCheckClaimCategory
import com.example.fakenews.data.model.FactCheckResult
import com.example.fakenews.data.model.FactCheckTimeSensitivity
import com.example.fakenews.data.model.FactCheckVerdict
import com.example.fakenews.data.model.GeminiGroundingMetadata
import com.example.fakenews.data.model.EvidenceDirectness

object FactCheckPostProcessor {
    fun process(
        result: FactCheckResult,
        groundingMetadata: GeminiGroundingMetadata,
        originalText: String
    ): FactCheckResult {
        val rawSourceCount = groundingMetadata.rawSourceCount
        val uniqueSourceCount = groundingMetadata.uniqueSourceCount
        val uniqueUriSourceCount = groundingMetadata.uniqueUriSourceCount
        val evidenceDirectness = EvidenceDirectnessEvaluator.evaluate(
            originalText = originalText,
            groundingMetadata = groundingMetadata,
            evidenceSummary = result.evidenceSummary,
            reason = result.reason,
            finalSummary = result.finalSummary,
            claimCategory = result.claimCategory
        )
        val groundingQueries = groundingMetadata.webSearchQueries
            .mapNotNull { query -> query.trim().takeIf { trimmed -> trimmed.isNotBlank() } }
        val effectiveQueries = groundingQueries.ifEmpty { result.searchQueriesUsed }
        var processed = result.copy(
            sourceCount = uniqueSourceCount,
            rawSourceCount = rawSourceCount,
            uniqueSourceCount = uniqueSourceCount,
            uniqueUriSourceCount = uniqueUriSourceCount,
            evidenceDirectness = evidenceDirectness,
            sources = groundingMetadata.sources,
            searchQueriesUsed = effectiveQueries,
            recommendedSearchKeywords = effectiveQueries.ifEmpty { result.recommendedSearchKeywords },
            relatedArticleCount = uniqueSourceCount,
            relatedArticles = emptyList(),
            originalText = originalText,
            confidenceBeforePostProcess = result.confidenceScore,
            usedInternalKnowledge = shouldUseInternalKnowledge(
                rawSourceCount = rawSourceCount,
                result = result
            )
        )

        if (rawSourceCount > 0) {
            processed = processed.copy(usedInternalKnowledge = false)
        }

        if (rawSourceCount == 0 && shouldRequireMoreTimeContext(processed, originalText)) {
            processed = processed.copy(
                verdict = FactCheckVerdict.NEEDS_MORE_CONTEXT,
                confidenceScore = processed.confidenceScore.coerceAtMost(MAX_UNGROUNDED_TIME_SENSITIVE_CONFIDENCE),
                usedInternalKnowledge = false,
                missingContext = mergeMissingContext(
                    current = processed.missingContext,
                    additions = missingContextQuestionsFor(processed.claimCategory)
                ),
                evidenceSummary = processed.evidenceSummary.ifBlank {
                    "검색 근거가 없고 기준 시점이나 맥락이 부족해 단정할 수 없습니다."
                },
                recommendedChecks = processed.recommendedChecks.ifEmpty {
                    listOf("기준 시점과 대상을 구체화한 뒤 공식 자료나 최신 보도를 확인하세요.")
                },
                confidenceAdjustmentReason = "time_context_missing_without_sources"
            )
        }

        if (
            rawSourceCount == 0 &&
            processed.timeSensitivity == FactCheckTimeSensitivity.HIGH &&
            processed.verdict in definitiveVerdicts
        ) {
            processed = processed.copy(
                verdict = FactCheckVerdict.UNVERIFIABLE,
                confidenceScore = processed.confidenceScore.coerceAtMost(MAX_UNGROUNDED_TIME_SENSITIVE_CONFIDENCE),
                usedInternalKnowledge = false,
                evidenceSummary = processed.evidenceSummary.ifBlank {
                    "최신 정보가 필요한 주장이나 검색 근거를 확인하지 못했습니다."
                },
                confidenceAdjustmentReason = "high_time_sensitivity_without_sources"
            )
        }

        return adjustConfidence(processed).let { adjusted ->
            adjusted.copy(
                confidenceAfterPostProcess = adjusted.confidenceScore,
                confidenceAdjustmentReason = adjusted.confidenceAdjustmentReason.ifBlank {
                    "none"
                }
            )
        }
    }

    private fun shouldUseInternalKnowledge(
        rawSourceCount: Int,
        result: FactCheckResult
    ): Boolean =
        rawSourceCount == 0 &&
            result.verdict in definitiveVerdicts &&
            result.claimCategory in internalKnowledgeCategories

    private fun adjustConfidence(result: FactCheckResult): FactCheckResult {
        if (result.verdict in insufficientEvidenceVerdicts) {
            val capped = result.confidenceScore.coerceAtMost(MAX_INSUFFICIENT_EVIDENCE_CONFIDENCE)
            return result.copy(
                confidenceScore = capped,
                confidenceAdjustmentReason = if (capped != result.confidenceScore) {
                    "capped_insufficient_evidence_verdict"
                } else {
                    result.confidenceAdjustmentReason
                }
            )
        }

        if (!result.confidenceParseSuccess) {
            val defaultConfidence = defaultConfidence(result)
            return result.copy(
                confidenceScore = defaultConfidence,
                confidenceAdjustmentReason = "parse_failed_default_${result.evidenceDirectness.name.lowercase()}"
            )
        }

        val withStableFactMaximum = raiseStableFactConfidenceToMaximum(result)
        val cappedMaximum = capDisallowedMaximumConfidence(withStableFactMaximum)

        if (!canRaiseLowDefinitiveConfidence(cappedMaximum)) return cappedMaximum

        val minimumConfidence = when (cappedMaximum.evidenceDirectness) {
            EvidenceDirectness.DIRECT -> 70
            EvidenceDirectness.PARTIAL,
            EvidenceDirectness.WEAK -> 60
            EvidenceDirectness.NONE -> return cappedMaximum
        }
        if (cappedMaximum.confidenceScore >= minimumConfidence) return cappedMaximum

        val raised = cappedMaximum.copy(
            confidenceScore = minimumConfidence,
            confidenceAdjustmentReason = if (cappedMaximum.ambiguousLowInteger) {
                "raised_ambiguous_low_integer_${cappedMaximum.evidenceDirectness.name.lowercase()}"
            } else {
                "raised_low_definitive_confidence_${cappedMaximum.evidenceDirectness.name.lowercase()}"
            }
        )
        return capDisallowedMaximumConfidence(raised)
    }

    private fun raiseStableFactConfidenceToMaximum(result: FactCheckResult): FactCheckResult {
        if (!canUseMaximumConfidenceForStableFact(result)) return result
        if (result.confidenceScore == MAX_CONFIDENCE) return result
        return result.copy(
            confidenceScore = MAX_CONFIDENCE,
            confidenceAdjustmentReason = "raised_obvious_stable_fact_to_100"
        )
    }

    private fun canUseMaximumConfidenceForStableFact(result: FactCheckResult): Boolean =
        result.verdict in definitiveVerdicts &&
            result.claimCategory in internalKnowledgeCategories &&
            result.timeSensitivity == FactCheckTimeSensitivity.LOW &&
            !result.needsTimeContext &&
            !result.hasUncertaintyLanguage() &&
            result.contradictingEvidence.isEmpty()

    private fun capDisallowedMaximumConfidence(result: FactCheckResult): FactCheckResult {
        val cap = maximumAllowedConfidence(result)
        if (result.confidenceScore <= cap) return result
        return result.copy(
            confidenceScore = cap,
            confidenceAdjustmentReason = when {
                result.verdict in insufficientEvidenceVerdicts -> "capped_insufficient_evidence_verdict"
                result.needsTimeContext -> "capped_missing_time_context"
                result.hasUncertaintyLanguage() -> "capped_uncertainty_language"
                result.claimCategory in maximumConfidenceRestrictedCategories -> "capped_time_sensitive_or_contextual_category"
                result.timeSensitivity != FactCheckTimeSensitivity.LOW -> "capped_time_sensitive_claim"
                else -> "capped_disallowed_maximum_confidence"
            }
        )
    }

    private fun maximumAllowedConfidence(result: FactCheckResult): Int =
        when {
            result.verdict in insufficientEvidenceVerdicts -> MAX_INSUFFICIENT_EVIDENCE_CONFIDENCE
            result.needsTimeContext -> MAX_CONTEXT_MISSING_CONFIDENCE
            result.hasUncertaintyLanguage() -> MAX_CONTEXTUAL_DEFINITIVE_CONFIDENCE
            result.claimCategory in maximumConfidenceRestrictedCategories -> MAX_CONTEXTUAL_DEFINITIVE_CONFIDENCE
            result.timeSensitivity != FactCheckTimeSensitivity.LOW -> MAX_CONTEXTUAL_DEFINITIVE_CONFIDENCE
            else -> MAX_CONFIDENCE
        }

    private fun canRaiseLowDefinitiveConfidence(result: FactCheckResult): Boolean {
        if (result.verdict !in definitiveVerdicts) return false
        if (result.rawSourceCount == 0) return false
        if (result.uniqueUriSourceCount == 0) return false
        if (result.contradictingEvidence.isNotEmpty()) return false
        if (result.hasUncertaintyLanguage()) return false
        return result.ambiguousLowInteger || result.confidenceScore < LOW_DEFINITIVE_CONFIDENCE_THRESHOLD
    }

    private fun defaultConfidence(result: FactCheckResult): Int =
        when (result.verdict) {
            FactCheckVerdict.TRUE,
            FactCheckVerdict.FALSE -> definitiveDefaultConfidence(result)
            FactCheckVerdict.MISLEADING,
            FactCheckVerdict.PARTLY_TRUE -> 65
            FactCheckVerdict.UNVERIFIABLE -> 40
            FactCheckVerdict.NEEDS_MORE_CONTEXT -> 45
        }

    private fun definitiveDefaultConfidence(result: FactCheckResult): Int =
        when {
            result.rawSourceCount > 0 && result.evidenceDirectness == EvidenceDirectness.DIRECT -> 75
            result.rawSourceCount > 0 && result.evidenceDirectness == EvidenceDirectness.PARTIAL -> 60
            result.rawSourceCount == 0 &&
                result.claimCategory in internalKnowledgeCategories &&
                !result.hasUncertaintyLanguage() -> 85
            else -> 50
        }

    private fun FactCheckResult.hasUncertaintyLanguage(): Boolean {
        val text = listOf(finalSummary, reason, evidenceSummary)
            .joinToString(" ")
            .lowercase()
        return uncertaintyPhrases.any { phrase -> text.contains(phrase) }
    }

    private fun shouldRequireMoreTimeContext(
        result: FactCheckResult,
        originalText: String
    ): Boolean {
        if (result.verdict !in definitiveVerdicts) return false
        if (result.needsTimeContext) return true
        if (
            result.timeSensitivity == FactCheckTimeSensitivity.HIGH &&
            result.claimCategory in timeContextCategories
        ) {
            return true
        }
        return hasTimeSensitiveKeyword(originalText) && !hasExplicitTimeHint(originalText)
    }

    private fun missingContextQuestionsFor(category: FactCheckClaimCategory): List<String> =
        when (category) {
            FactCheckClaimCategory.FINANCE -> listOf(
                "어느 날짜 또는 장 마감 기준의 수치인가요?",
                "어느 시장이나 종목을 기준으로 확인해야 하나요?"
            )
            FactCheckClaimCategory.SPORTS -> listOf(
                "어느 연도, 대회, 경기의 결과를 말하나요?",
                "대상 팀이나 경기 날짜를 더 구체적으로 알려줄 수 있나요?"
            )
            FactCheckClaimCategory.ELECTION -> listOf(
                "어느 국가, 지역, 선거 연도 또는 투표일 기준인가요?"
            )
            FactCheckClaimCategory.RECENT_EVENT,
            FactCheckClaimCategory.POLITICS,
            FactCheckClaimCategory.SOCIAL,
            FactCheckClaimCategory.UNKNOWN -> listOf(
                "판단 기준이 되는 날짜, 지역, 대상은 무엇인가요?"
            )
            else -> listOf("판단 기준이 되는 시점이나 맥락을 더 구체적으로 알려줄 수 있나요?")
        }

    private fun mergeMissingContext(
        current: List<String>,
        additions: List<String>
    ): List<String> =
        (current + additions)
            .mapNotNull { item -> item.trim().takeIf { trimmed -> trimmed.isNotBlank() } }
            .distinct()

    private fun hasTimeSensitiveKeyword(text: String): Boolean =
        timeSensitiveKeywords.any { keyword -> text.contains(keyword, ignoreCase = true) }

    private fun hasExplicitTimeHint(text: String): Boolean =
        explicitTimeHintPattern.containsMatchIn(text)

    private const val MAX_UNGROUNDED_TIME_SENSITIVE_CONFIDENCE = 30
    private const val MAX_INSUFFICIENT_EVIDENCE_CONFIDENCE = 60
    private const val MAX_CONTEXT_MISSING_CONFIDENCE = 60
    private const val MAX_CONTEXTUAL_DEFINITIVE_CONFIDENCE = 98
    private const val MAX_CONFIDENCE = 100
    private const val LOW_DEFINITIVE_CONFIDENCE_THRESHOLD = 30

    private val definitiveVerdicts = setOf(
        FactCheckVerdict.TRUE,
        FactCheckVerdict.FALSE
    )

    private val insufficientEvidenceVerdicts = setOf(
        FactCheckVerdict.UNVERIFIABLE,
        FactCheckVerdict.NEEDS_MORE_CONTEXT
    )

    private val internalKnowledgeCategories = setOf(
        FactCheckClaimCategory.COMMON_KNOWLEDGE,
        FactCheckClaimCategory.HISTORICAL,
        FactCheckClaimCategory.SCIENCE
    )

    private val timeContextCategories = setOf(
        FactCheckClaimCategory.FINANCE,
        FactCheckClaimCategory.SPORTS,
        FactCheckClaimCategory.ELECTION,
        FactCheckClaimCategory.RECENT_EVENT,
        FactCheckClaimCategory.POLITICS,
        FactCheckClaimCategory.SOCIAL,
        FactCheckClaimCategory.UNKNOWN
    )

    private val maximumConfidenceRestrictedCategories = setOf(
        FactCheckClaimCategory.FINANCE,
        FactCheckClaimCategory.SPORTS,
        FactCheckClaimCategory.ELECTION,
        FactCheckClaimCategory.RECENT_EVENT,
        FactCheckClaimCategory.POLITICS,
        FactCheckClaimCategory.SOCIAL,
        FactCheckClaimCategory.UNKNOWN
    )

    private val timeSensitiveKeywords = listOf(
        "주가",
        "환율",
        "월드컵",
        "올림픽",
        "선거",
        "당선",
        "최근",
        "오늘",
        "어제",
        "발표",
        "실적",
        "정책",
        "사고",
        "재난"
    )

    private val explicitTimeHintPattern = Regex(
        pattern = "\\d{4}년|\\d{1,2}월\\s*\\d{1,2}일|\\d{4}-\\d{1,2}-\\d{1,2}|오늘|어제|그제|올해|작년|지난\\s*\\d{4}",
        option = RegexOption.IGNORE_CASE
    )

    private val uncertaintyPhrases = listOf(
        "불확실",
        "확인 필요",
        "근거 부족",
        "명확하지 않음",
        "명확하지 않습니다",
        "모호",
        "추가 정보 필요",
        "단정하기 어렵",
        "판단하기 어렵",
        "검증 불가",
        "확인할 수 없",
        "맥락 부족",
        "불확실",
        "확인 필요",
        "근거 부족",
        "모호",
        "단정할 수",
        "추가 정보",
        "맥락 부족",
        "unverifiable",
        "uncertain",
        "insufficient",
        "needs more context"
    )
}

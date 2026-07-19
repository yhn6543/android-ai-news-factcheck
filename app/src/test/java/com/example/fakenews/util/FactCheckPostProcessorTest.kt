package com.example.fakenews.util

import com.example.fakenews.data.model.FactCheckClaimCategory
import com.example.fakenews.data.model.FactCheckResult
import com.example.fakenews.data.model.FactCheckRiskLevel
import com.example.fakenews.data.model.FactCheckTimeSensitivity
import com.example.fakenews.data.model.FactCheckVerdict
import com.example.fakenews.data.model.GeminiGroundingMetadata
import com.example.fakenews.data.model.GroundingChunk
import com.example.fakenews.data.model.GroundingWebSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FactCheckPostProcessorTest {
    @Test
    fun sourceZeroTrueCommonKnowledgeUsesInternalKnowledge() {
        val processed = FactCheckPostProcessor.process(
            result = result(
                verdict = FactCheckVerdict.TRUE,
                confidenceScore = 95,
                claimCategory = FactCheckClaimCategory.COMMON_KNOWLEDGE,
                timeSensitivity = FactCheckTimeSensitivity.LOW
            ),
            groundingMetadata = GeminiGroundingMetadata.Empty,
            originalText = "조선은 이성계가 건국했다"
        )

        assertEquals(FactCheckVerdict.TRUE, processed.verdict)
        assertEquals(100, processed.confidenceScore)
        assertTrue(processed.usedInternalKnowledge)
        assertEquals(0, processed.sourceCount)
    }

    @Test
    fun sourceZeroFalseScienceUsesInternalKnowledge() {
        val processed = FactCheckPostProcessor.process(
            result = result(
                verdict = FactCheckVerdict.FALSE,
                confidenceScore = 92,
                claimCategory = FactCheckClaimCategory.SCIENCE,
                timeSensitivity = FactCheckTimeSensitivity.LOW
            ),
            groundingMetadata = GeminiGroundingMetadata.Empty,
            originalText = "지구가 평평하다"
        )

        assertEquals(FactCheckVerdict.FALSE, processed.verdict)
        assertEquals(100, processed.confidenceScore)
        assertTrue(processed.usedInternalKnowledge)
    }

    @Test
    fun sourceZeroTrueHistoricalLowCanUseMaximumConfidence() {
        val processed = FactCheckPostProcessor.process(
            result = result(
                verdict = FactCheckVerdict.TRUE,
                confidenceScore = 96,
                claimCategory = FactCheckClaimCategory.HISTORICAL,
                timeSensitivity = FactCheckTimeSensitivity.LOW,
                reason = "명확한 역사 사실입니다.",
                finalSummary = "조선은 이성계가 건국했습니다."
            ),
            groundingMetadata = GeminiGroundingMetadata.Empty,
            originalText = "조선은 이성계가 건국했다"
        )

        assertEquals(FactCheckVerdict.TRUE, processed.verdict)
        assertEquals(100, processed.confidenceScore)
        assertTrue(processed.usedInternalKnowledge)
    }

    @Test
    fun financeClaimWithSourcesCannotUseMaximumConfidence() {
        val processed = FactCheckPostProcessor.process(
            result = result(
                verdict = FactCheckVerdict.TRUE,
                confidenceScore = 100,
                claimCategory = FactCheckClaimCategory.FINANCE,
                timeSensitivity = FactCheckTimeSensitivity.HIGH,
                reason = "공식 출처로 확인했습니다.",
                finalSummary = "삼성전자 주가가 기준 시점에 해당 가격을 돌파했습니다."
            ),
            groundingMetadata = source(
                title = "삼성전자 주가 공시",
                uri = "https://example.com/stock"
            ),
            originalText = "삼성전자 주가가 오늘 40만원을 돌파했다"
        )

        assertEquals(FactCheckVerdict.TRUE, processed.verdict)
        assertEquals(98, processed.confidenceScore)
    }

    @Test
    fun sourceOneOrMoreDisablesInternalKnowledgeAndUsesMetadataCounts() {
        val processed = FactCheckPostProcessor.process(
            result = result(
                verdict = FactCheckVerdict.TRUE,
                confidenceScore = 88,
                claimCategory = FactCheckClaimCategory.COMMON_KNOWLEDGE,
                timeSensitivity = FactCheckTimeSensitivity.LOW,
                usedInternalKnowledge = true
            ),
            groundingMetadata = GeminiGroundingMetadata(
                webSearchQueries = listOf("검색어"),
                groundingChunks = listOf(
                    GroundingChunk(
                        web = GroundingWebSource(
                            title = "출처 제목",
                            uri = "https://example.com/source"
                        )
                    )
                )
            ),
            originalText = "근거 있는 주장"
        )

        assertFalse(processed.usedInternalKnowledge)
        assertEquals(1, processed.sourceCount)
        assertEquals(1, processed.relatedArticleCount)
        assertEquals(listOf("검색어"), processed.searchQueriesUsed)
        assertEquals("https://example.com/source", processed.sources.single().uri)
    }

    @Test
    fun highTimeSensitivityWithoutSourceBlocksDefinitiveVerdict() {
        val processed = FactCheckPostProcessor.process(
            result = result(
                verdict = FactCheckVerdict.TRUE,
                confidenceScore = 91,
                claimCategory = FactCheckClaimCategory.RECENT_EVENT,
                timeSensitivity = FactCheckTimeSensitivity.HIGH
            ),
            groundingMetadata = GeminiGroundingMetadata.Empty,
            originalText = "최근 큰 사고가 발생했다"
        )

        assertEquals(FactCheckVerdict.NEEDS_MORE_CONTEXT, processed.verdict)
        assertEquals(30, processed.confidenceScore)
        assertFalse(processed.usedInternalKnowledge)
    }

    @Test
    fun trueWithSourcesAndAmbiguousLowIntegerRaisesConfidence() {
        val processed = FactCheckPostProcessor.process(
            result = result(
                verdict = FactCheckVerdict.TRUE,
                confidenceScore = 7,
                claimCategory = FactCheckClaimCategory.FINANCE,
                timeSensitivity = FactCheckTimeSensitivity.HIGH,
                ambiguousLowInteger = true,
                reason = "삼성전자 주가 40만원 돌파를 직접 근거로 확인했습니다.",
                finalSummary = "근거와 일치합니다."
            ),
            groundingMetadata = source(
                title = "삼성전자 주가 40만원 돌파 확인",
                uri = "https://example.com/stock"
            ),
            originalText = "삼성전자 주가 40만원 돌파"
        )

        assertEquals(FactCheckVerdict.TRUE, processed.verdict)
        assertTrue(processed.confidenceScore >= 70)
    }

    @Test
    fun falseWithSourcesAndAmbiguousLowIntegerRaisesConfidence() {
        val processed = FactCheckPostProcessor.process(
            result = result(
                verdict = FactCheckVerdict.FALSE,
                confidenceScore = 8,
                claimCategory = FactCheckClaimCategory.SPORTS,
                timeSensitivity = FactCheckTimeSensitivity.HIGH,
                ambiguousLowInteger = true,
                reason = "한국 월드컵 체코전 패배 주장은 공식 경기 결과와 맞지 않습니다.",
                finalSummary = "거짓으로 판단됩니다."
            ),
            groundingMetadata = source(
                title = "한국 월드컵 체코전 패배 여부 공식 결과",
                uri = "https://example.com/sports"
            ),
            originalText = "한국 월드컵 체코한테 패배"
        )

        assertEquals(FactCheckVerdict.FALSE, processed.verdict)
        assertTrue(processed.confidenceScore >= 60)
    }

    @Test
    fun uncertaintyLanguageDoesNotRaiseLowConfidence() {
        val processed = FactCheckPostProcessor.process(
            result = result(
                verdict = FactCheckVerdict.TRUE,
                confidenceScore = 7,
                claimCategory = FactCheckClaimCategory.FINANCE,
                timeSensitivity = FactCheckTimeSensitivity.HIGH,
                ambiguousLowInteger = true,
                reason = "근거 부족으로 불확실합니다.",
                finalSummary = "확인 필요"
            ),
            groundingMetadata = source(
                title = "삼성전자 주가 40만원 돌파 확인",
                uri = "https://example.com/stock"
            ),
            originalText = "삼성전자 주가 40만원 돌파"
        )

        assertEquals(7, processed.confidenceScore)
    }

    @Test
    fun contradictingEvidenceDoesNotRaiseLowConfidence() {
        val processed = FactCheckPostProcessor.process(
            result = result(
                verdict = FactCheckVerdict.TRUE,
                confidenceScore = 7,
                claimCategory = FactCheckClaimCategory.FINANCE,
                timeSensitivity = FactCheckTimeSensitivity.HIGH,
                ambiguousLowInteger = true,
                contradictingEvidence = listOf("다른 공식 자료와 충돌")
            ),
            groundingMetadata = source(
                title = "삼성전자 주가 40만원 돌파 확인",
                uri = "https://example.com/stock"
            ),
            originalText = "삼성전자 주가 40만원 돌파"
        )

        assertEquals(7, processed.confidenceScore)
    }

    @Test
    fun unverifiableConfidenceIsCapped() {
        val processed = FactCheckPostProcessor.process(
            result = result(
                verdict = FactCheckVerdict.UNVERIFIABLE,
                confidenceScore = 95,
                claimCategory = FactCheckClaimCategory.UNKNOWN,
                timeSensitivity = FactCheckTimeSensitivity.HIGH
            ),
            groundingMetadata = source("관련 출처", "https://example.com/source"),
            originalText = "확인하기 어려운 주장"
        )

        assertEquals(60, processed.confidenceScore)
    }

    @Test
    fun needsMoreContextConfidenceIsCapped() {
        val processed = FactCheckPostProcessor.process(
            result = result(
                verdict = FactCheckVerdict.NEEDS_MORE_CONTEXT,
                confidenceScore = 90,
                claimCategory = FactCheckClaimCategory.SPORTS,
                timeSensitivity = FactCheckTimeSensitivity.HIGH
            ),
            groundingMetadata = source("관련 출처", "https://example.com/source"),
            originalText = "한국 월드컵 체코한테 패배"
        )

        assertEquals(60, processed.confidenceScore)
    }

    @Test
    fun uncertaintyLanguagePreventsMaximumConfidence() {
        val processed = FactCheckPostProcessor.process(
            result = result(
                verdict = FactCheckVerdict.TRUE,
                confidenceScore = 100,
                claimCategory = FactCheckClaimCategory.SCIENCE,
                timeSensitivity = FactCheckTimeSensitivity.LOW,
                reason = "근거 부족으로 확인 필요합니다.",
                finalSummary = "명확하지 않습니다."
            ),
            groundingMetadata = source("과학 참고 자료", "https://example.com/science"),
            originalText = "과학 주장"
        )

        assertEquals(98, processed.confidenceScore)
    }

    @Test
    fun titleOnlySourceDoesNotRaiseConfidenceTooMuch() {
        val processed = FactCheckPostProcessor.process(
            result = result(
                verdict = FactCheckVerdict.TRUE,
                confidenceScore = 7,
                claimCategory = FactCheckClaimCategory.FINANCE,
                timeSensitivity = FactCheckTimeSensitivity.HIGH,
                ambiguousLowInteger = true
            ),
            groundingMetadata = source(
                title = "삼성전자 주가 40만원 돌파 확인",
                uri = ""
            ),
            originalText = "삼성전자 주가 40만원 돌파"
        )

        assertEquals(7, processed.confidenceScore)
        assertEquals(0, processed.uniqueUriSourceCount)
    }

    @Test
    fun samsungStockWithoutTimeContextNeedsMoreContext() {
        val processed = FactCheckPostProcessor.process(
            result = result(
                verdict = FactCheckVerdict.TRUE,
                confidenceScore = 80,
                claimCategory = FactCheckClaimCategory.FINANCE,
                timeSensitivity = FactCheckTimeSensitivity.HIGH,
                needsTimeContext = true
            ),
            groundingMetadata = GeminiGroundingMetadata.Empty,
            originalText = "삼성전자 주가 40만원 돌파"
        )

        assertEquals(FactCheckVerdict.NEEDS_MORE_CONTEXT, processed.verdict)
        assertTrue(processed.missingContext.any { question -> question.contains("날짜") })
        assertEquals(30, processed.confidenceScore)
    }

    @Test
    fun ambiguousWorldCupClaimNeedsMoreContext() {
        val processed = FactCheckPostProcessor.process(
            result = result(
                verdict = FactCheckVerdict.FALSE,
                confidenceScore = 78,
                claimCategory = FactCheckClaimCategory.SPORTS,
                timeSensitivity = FactCheckTimeSensitivity.HIGH,
                needsTimeContext = true
            ),
            groundingMetadata = GeminiGroundingMetadata.Empty,
            originalText = "한국 월드컵 체코한테 패배"
        )

        assertEquals(FactCheckVerdict.NEEDS_MORE_CONTEXT, processed.verdict)
        assertTrue(processed.missingContext.any { question -> question.contains("대회") })
        assertEquals(30, processed.confidenceScore)
    }

    private fun result(
        verdict: FactCheckVerdict,
        confidenceScore: Int,
        claimCategory: FactCheckClaimCategory,
        timeSensitivity: FactCheckTimeSensitivity,
        needsTimeContext: Boolean = false,
        usedInternalKnowledge: Boolean = false,
        ambiguousLowInteger: Boolean = false,
        reason: String = "",
        finalSummary: String = "요약",
        contradictingEvidence: List<String> = emptyList()
    ): FactCheckResult =
        FactCheckResult(
            finalSummary = finalSummary,
            riskLevel = FactCheckRiskLevel.UNKNOWN,
            fakeNewsPossibility = 50,
            logicAnalysis = "",
            emotionalBiasAnalysis = "",
            sourceReliabilityAnalysis = "",
            exaggerationAnalysis = "",
            factsVsOpinions = "",
            additionalChecks = emptyList(),
            recommendedSearchKeywords = emptyList(),
            relatedArticleCount = 0,
            relatedArticles = emptyList(),
            verdict = verdict,
            confidenceScore = confidenceScore,
            reason = reason,
            evidenceSummary = reason,
            contradictingEvidence = contradictingEvidence,
            claimCategory = claimCategory,
            timeSensitivity = timeSensitivity,
            needsTimeContext = needsTimeContext,
            usedInternalKnowledge = usedInternalKnowledge,
            ambiguousLowInteger = ambiguousLowInteger
        )

    private fun source(
        title: String,
        uri: String
    ): GeminiGroundingMetadata =
        GeminiGroundingMetadata(
            groundingChunks = listOf(
                GroundingChunk(
                    web = GroundingWebSource(
                        title = title,
                        uri = uri
                    )
                )
            )
        )
}

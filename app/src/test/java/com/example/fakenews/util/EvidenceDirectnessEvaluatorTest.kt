package com.example.fakenews.util

import com.example.fakenews.data.model.EvidenceDirectness
import com.example.fakenews.data.model.FactCheckClaimCategory
import com.example.fakenews.data.model.GeminiGroundingMetadata
import com.example.fakenews.data.model.GroundingChunk
import com.example.fakenews.data.model.GroundingWebSource
import org.junit.Assert.assertEquals
import org.junit.Test

class EvidenceDirectnessEvaluatorTest {
    @Test
    fun sportsResultDirectEvidenceIsDirect() {
        val directness = evaluate(
            originalText = "한국 월드컵 체코전 승리",
            title = "한국 월드컵 체코전 승리 공식 결과",
            uri = "https://example.com/sports",
            category = FactCheckClaimCategory.SPORTS,
            evidenceSummary = "한국이 체코를 이겼다는 경기 결과를 확인했습니다."
        )

        assertEquals(EvidenceDirectness.DIRECT, directness)
    }

    @Test
    fun financeNumberDirectEvidenceIsDirect() {
        val directness = evaluate(
            originalText = "삼성전자 주가 40만원 돌파",
            title = "삼성전자 주가 40만원 돌파 여부",
            uri = "https://example.com/finance",
            category = FactCheckClaimCategory.FINANCE,
            evidenceSummary = "삼성전자 주가 40만원 수치를 확인했습니다."
        )

        assertEquals(EvidenceDirectness.DIRECT, directness)
    }

    @Test
    fun unrelatedSourceIsNone() {
        val directness = evaluate(
            originalText = "삼성전자 주가 40만원 돌파",
            title = "날씨와 축제 일정",
            uri = "https://example.com/weather",
            category = FactCheckClaimCategory.FINANCE
        )

        assertEquals(EvidenceDirectness.NONE, directness)
    }

    @Test
    fun partialKeywordOverlapIsNotDirect() {
        val directness = evaluate(
            originalText = "삼성전자 주가 40만원 돌파",
            title = "삼성전자 실적 발표",
            uri = "https://example.com/company",
            category = FactCheckClaimCategory.FINANCE
        )

        assertEquals(EvidenceDirectness.WEAK, directness)
    }

    @Test
    fun titleOnlySourceIsConservative() {
        val directness = evaluate(
            originalText = "삼성전자 주가 40만원 돌파",
            title = "삼성전자 주가 40만원 돌파 여부",
            uri = "",
            category = FactCheckClaimCategory.FINANCE,
            evidenceSummary = "삼성전자 주가 40만원 돌파 여부"
        )

        assertEquals(EvidenceDirectness.PARTIAL, directness)
    }

    private fun evaluate(
        originalText: String,
        title: String,
        uri: String,
        category: FactCheckClaimCategory,
        evidenceSummary: String = ""
    ): EvidenceDirectness =
        EvidenceDirectnessEvaluator.evaluate(
            originalText = originalText,
            groundingMetadata = GeminiGroundingMetadata(
                groundingChunks = listOf(
                    GroundingChunk(
                        web = GroundingWebSource(
                            title = title,
                            uri = uri
                        )
                    )
                )
            ),
            evidenceSummary = evidenceSummary,
            reason = evidenceSummary,
            finalSummary = evidenceSummary,
            claimCategory = category
        )
}

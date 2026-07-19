package com.example.fakenews.util

import com.example.fakenews.data.model.FactCheckClaimCategory
import com.example.fakenews.data.model.FactCheckRiskLevel
import com.example.fakenews.data.model.FactCheckTimeSensitivity
import org.junit.Assert.assertEquals
import org.junit.Test

class GeminiFactCheckResultParserTest {
    @Test
    fun parsesNormalJsonToFactCheckResult() {
        val result = GeminiFactCheckResultParser.parse(validJson(riskLevel = "LOW", possibility = 42))

        assertEquals("핵심 요약", result.finalSummary)
        assertEquals(FactCheckRiskLevel.LOW, result.riskLevel)
        assertEquals(42, result.fakeNewsPossibility)
        assertEquals(listOf("경제", "정책"), result.searchQueriesUsed)
        assertEquals(listOf("경제", "정책"), result.recommendedSearchKeywords)
    }

    @Test
    fun unknownRiskLevelFallsBackToUnknown() {
        val result = GeminiFactCheckResultParser.parse(validJson(riskLevel = "STRANGE", possibility = 20))

        assertEquals(FactCheckRiskLevel.UNKNOWN, result.riskLevel)
    }

    @Test
    fun fakeNewsPossibilityIsClampedToZeroToOneHundred() {
        val high = GeminiFactCheckResultParser.parse(validJson(riskLevel = "HIGH", possibility = 150))
        val low = GeminiFactCheckResultParser.parse(validJson(riskLevel = "LOW", possibility = -10))

        assertEquals(100, high.fakeNewsPossibility)
        assertEquals(0, low.fakeNewsPossibility)
    }

    @Test
    fun listFieldsAcceptArraysStringsAndNulls() {
        val result = GeminiFactCheckResultParser.parse(
            """
            {
              "verdict": "NEEDS_MORE_CONTEXT",
              "confidenceScore": 40,
              "finalSummary": "맥락 부족",
              "reason": "기준 시점이 없습니다.",
              "evidenceSummary": "",
              "missingContext": "기준 날짜가 무엇인가요?",
              "recommendedChecks": null,
              "searchQueriesUsed": "삼성전자 주가",
              "claimCategory": "FINANCE",
              "timeSensitivity": "HIGH",
              "needsTimeContext": true
            }
            """.trimIndent()
        )

        assertEquals(listOf("기준 날짜가 무엇인가요?"), result.missingContext)
        assertEquals(emptyList<String>(), result.recommendedChecks)
        assertEquals(listOf("삼성전자 주가"), result.searchQueriesUsed)
        assertEquals(FactCheckClaimCategory.FINANCE, result.claimCategory)
        assertEquals(FactCheckTimeSensitivity.HIGH, result.timeSensitivity)
        assertEquals(true, result.needsTimeContext)
    }

    @Test
    fun unknownClaimCategoryAndTimeSensitivityFallBackToUnknown() {
        val result = GeminiFactCheckResultParser.parse(
            validJson(
                riskLevel = "LOW",
                possibility = 10,
                claimCategory = "ALIEN",
                timeSensitivity = "SOMEDAY"
            )
        )

        assertEquals(FactCheckClaimCategory.UNKNOWN, result.claimCategory)
        assertEquals(FactCheckTimeSensitivity.UNKNOWN, result.timeSensitivity)
    }

    private fun validJson(
        riskLevel: String,
        possibility: Int,
        claimCategory: String = "SOCIAL",
        timeSensitivity: String = "MEDIUM"
    ): String =
        """
        {
          "finalSummary": "핵심 요약",
          "riskLevel": "$riskLevel",
          "fakeNewsPossibility": $possibility,
          "logicAnalysis": "논리 분석",
          "emotionalBiasAnalysis": "감정 분석",
          "sourceReliabilityAnalysis": "출처 분석",
          "exaggerationAnalysis": "과장 분석",
          "factsVsOpinions": "사실 의견 구분",
          "additionalChecks": ["추가 확인"],
          "searchQueriesUsed": ["경제", "정책"],
          "claimCategory": "$claimCategory",
          "timeSensitivity": "$timeSensitivity",
          "needsTimeContext": false
        }
        """.trimIndent()
}

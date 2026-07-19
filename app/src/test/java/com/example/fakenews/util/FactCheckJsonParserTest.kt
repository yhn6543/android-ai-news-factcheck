package com.example.fakenews.util

import com.example.fakenews.data.model.FactCheckRiskLevel
import com.example.fakenews.data.model.FactCheckVerdict
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Test

class FactCheckJsonParserTest {
    @Test
    fun parsesValidJson() {
        val result = GeminiFactCheckResultParser.parse(validJson())

        assertEquals("핵심 요약", result.finalSummary)
        assertEquals(FactCheckRiskLevel.MEDIUM, result.riskLevel)
        assertEquals(35, result.fakeNewsPossibility)
        assertEquals(listOf("경제", "정책"), result.recommendedSearchKeywords)
    }

    @Test
    fun parsesJsonInsideCodeBlock() {
        val result = GeminiFactCheckResultParser.parse(
            """
            ```json
            ${validJson(riskLevel = "LOW", possibility = 7)}
            ```
            """.trimIndent()
        )

        assertEquals(FactCheckRiskLevel.LOW, result.riskLevel)
        assertEquals(7, result.fakeNewsPossibility)
    }

    @Test
    fun invalidRiskLevelFallsBackToUnknown() {
        val result = GeminiFactCheckResultParser.parse(validJson(riskLevel = "INVALID"))

        assertEquals(FactCheckRiskLevel.UNKNOWN, result.riskLevel)
    }

    @Test
    fun fakeNewsPossibilityIsClamped() {
        val high = GeminiFactCheckResultParser.parse(validJson(possibility = 120))
        val low = GeminiFactCheckResultParser.parse(validJson(possibility = -5))

        assertEquals(100, high.fakeNewsPossibility)
        assertEquals(0, low.fakeNewsPossibility)
    }

    @Test
    fun parsesVerdictConfidenceAndMissingContext() {
        val result = GeminiFactCheckResultParser.parse(
            """
            {
              "verdict": "NEEDS_MORE_CONTEXT",
              "confidenceScore": 140,
              "finalSummary": "월드컵 시점이 불명확합니다.",
              "reason": "어느 경기인지 특정할 수 없습니다.",
              "evidenceSummary": "제공된 근거가 없습니다.",
              "contradictingEvidence": ["공식 결과 근거 없음"],
              "missingContext": ["대회 연도", "경기 날짜"],
              "recommendedChecks": ["공식 경기 결과 확인"],
              "recommendedSearchKeywords": ["한국 체코 월드컵"]
            }
            """.trimIndent()
        )

        assertEquals(FactCheckVerdict.NEEDS_MORE_CONTEXT, result.verdict)
        assertEquals(100, result.confidenceScore)
        assertEquals(listOf("대회 연도", "경기 날짜"), result.missingContext)
        assertEquals(listOf("공식 경기 결과 확인"), result.recommendedChecks)
    }

    @Test(expected = SerializationException::class)
    fun invalidJsonThrowsParsingError() {
        GeminiFactCheckResultParser.parse("{ not-json ")
    }

    private fun validJson(
        riskLevel: String = "MEDIUM",
        possibility: Int = 35
    ): String =
        """
        {
          "finalSummary": "핵심 요약",
          "riskLevel": "$riskLevel",
          "fakeNewsPossibility": $possibility,
          "logicAnalysis": "논리성 분석",
          "emotionalBiasAnalysis": "감정적 편향성 분석",
          "sourceReliabilityAnalysis": "출처 신뢰성 분석",
          "exaggerationAnalysis": "과장 표현 분석",
          "factsVsOpinions": "사실과 의견 구분",
          "additionalChecks": ["공식 자료 확인"],
          "recommendedSearchKeywords": ["경제", "정책"]
        }
        """.trimIndent()
}

package com.example.fakenews.data.repository

import com.example.fakenews.data.model.FactCheckClaimCategory
import com.example.fakenews.data.model.FactCheckResult
import com.example.fakenews.data.model.FactCheckRiskLevel
import com.example.fakenews.data.model.FactCheckTimeSensitivity
import com.example.fakenews.data.model.FactCheckVerdict
import kotlinx.coroutines.delay

class MockFactCheckRepository : FactCheckRepository {
    override suspend fun analyze(text: String): FactCheckResult {
        delay(500L)

        return FactCheckResult(
            finalSummary = "입력한 문장을 mock 기준으로 점검했습니다. 실제 Gemini 분석이 아닌 화면 검증용 결과입니다.",
            riskLevel = FactCheckRiskLevel.UNKNOWN,
            fakeNewsPossibility = 50,
            verdict = FactCheckVerdict.UNVERIFIABLE,
            confidenceScore = 25,
            reason = "Mock 모드는 Gemini Google Search Grounding을 호출하지 않습니다.",
            evidenceSummary = "검색 출처가 없어 실제 사실 여부를 단정하지 않습니다.",
            contradictingEvidence = emptyList(),
            missingContext = listOf("실제 판별에는 Gemini API Key와 네트워크 호출이 필요합니다."),
            logicAnalysis = "Mock 결과입니다.",
            emotionalBiasAnalysis = "",
            sourceReliabilityAnalysis = "Mock 결과에는 실제 검색 출처가 포함되지 않습니다.",
            exaggerationAnalysis = "",
            factsVsOpinions = "",
            additionalChecks = listOf("Gemini API Key를 설정한 뒤 실제 분석을 실행하세요."),
            recommendedChecks = listOf("Gemini API Key를 설정한 뒤 실제 분석을 실행하세요."),
            recommendedSearchKeywords = emptyList(),
            relatedArticleCount = 0,
            relatedArticles = emptyList(),
            sourceCount = 0,
            sources = emptyList(),
            searchQueriesUsed = emptyList(),
            usedInternalKnowledge = false,
            claimCategory = FactCheckClaimCategory.UNKNOWN,
            timeSensitivity = FactCheckTimeSensitivity.UNKNOWN,
            needsTimeContext = false,
            originalText = text
        )
    }
}

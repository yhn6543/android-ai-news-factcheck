package com.example.fakenews.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiPromptBuilderTest {
    @Test
    fun promptContainsJsonContract() {
        val prompt = GeminiPromptBuilder.buildFactCheckPrompt("테스트 입력")

        assertTrue(prompt.contains("\"finalSummary\""))
        assertTrue(prompt.contains("\"verdict\""))
        assertTrue(prompt.contains("\"confidenceScore\""))
        assertTrue(prompt.contains("\"searchQueriesUsed\""))
        assertTrue(prompt.contains("\"claimCategory\""))
        assertTrue(prompt.contains("\"timeSensitivity\""))
        assertTrue(prompt.contains("JSON"))
    }

    @Test
    fun promptContainsOriginalUserTextVerbatim() {
        val input = "  삼성전자 주가 40만원 돌파\n원문 두 번째 줄  "
        val prompt = GeminiPromptBuilder.buildFactCheckPrompt(input)

        assertTrue(prompt.endsWith(input))
    }

    @Test
    fun promptDoesNotUseClaimExtractionOrEvidenceArticleBlocks() {
        val prompt = GeminiPromptBuilder.buildFactCheckPrompt("삼성전자 주가 40만원 돌파")

        assertFalse(prompt.contains("topicType"))
        assertFalse(prompt.contains("searchQueries:"))
    }

    @Test
    fun promptClarifiesConfidenceScoreScaleAndMeaning() {
        val prompt = GeminiPromptBuilder.buildFactCheckPrompt("검증할 문장")

        assertTrue(prompt.contains("confidenceScore는 반드시 0~100 사이의 정수"))
        assertTrue(prompt.contains("최종 verdict 판정에 대한 신뢰도"))
        assertTrue(prompt.contains("주장이 사실일 확률"))
        assertTrue(prompt.contains("\"confidenceScore\": 8이 아니라 반드시 \"confidenceScore\": 80"))
        assertTrue(prompt.contains("TRUE/FALSE로 판단하고 직접적인 근거가 있는 경우 confidenceScore는 일반적으로 70 이상"))
        assertTrue(prompt.contains("UNVERIFIABLE 또는 NEEDS_MORE_CONTEXT"))
    }

    @Test
    fun promptAllowsMaximumConfidenceForObviousStableFacts() {
        val prompt = GeminiPromptBuilder.buildFactCheckPrompt("지구는 평평하다")

        assertTrue(prompt.contains("명백한 일반상식, 과학 사실, 역사 사실"))
        assertTrue(prompt.contains("confidenceScore 100을 사용할 수 있습니다"))
        assertTrue(prompt.contains("\"지구는 평평하다\""))
        assertTrue(prompt.contains("verdict = FALSE, confidenceScore = 100"))
        assertTrue(prompt.contains("\"조선은 이성계가 건국했다\""))
    }

    @Test
    fun promptProhibitsMaximumConfidenceForTimeSensitiveClaims() {
        val prompt = GeminiPromptBuilder.buildFactCheckPrompt("삼성전자 주가가 오늘 40만원을 돌파했다")

        assertTrue(prompt.contains("금융, 주가, 스포츠 결과, 선거, 최근 사건, 정치 이슈"))
        assertTrue(prompt.contains("confidenceScore 100을 반환하지 마세요"))
        assertTrue(prompt.contains("95~98 이하"))
        assertTrue(prompt.contains("기준 시점, 날짜, 대회, 대상, 지역"))
        assertTrue(prompt.contains("불확실, 확인 필요, 근거 부족"))
    }
}

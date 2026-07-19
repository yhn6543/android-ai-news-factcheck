package com.example.fakenews.util

import com.example.fakenews.data.model.ClaimTopicType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClaimSearchQueryBuilderTest {
    @Test
    fun samsungStockClaimBuildsFinanceQueriesWithNumericValue() {
        val analysis = ClaimSearchQueryBuilder.analyze("삼성전자 주가 40만원 돌파")

        assertEquals(ClaimTopicType.FINANCE, analysis.topicType)
        assertEquals("400000", analysis.numericValue)
        assertTrue(analysis.entities.contains("삼성전자"))
        assertTrue(analysis.searchQueries.contains("삼성전자 주가 40만원 돌파"))
        assertTrue(analysis.searchQueries.contains("삼성전자 현재 주가"))
        assertTrue(analysis.searchQueries.contains("삼성전자 주가 400000원"))
    }

    @Test
    fun koreaCzechWorldCupClaimBuildsSportsQueries() {
        val analysis = ClaimSearchQueryBuilder.analyze("한국 월드컵 체코한테 패배")

        assertEquals(ClaimTopicType.SPORTS, analysis.topicType)
        assertTrue(analysis.entities.contains("한국"))
        assertTrue(analysis.entities.contains("체코"))
        assertTrue(analysis.entities.contains("월드컵"))
        assertTrue(analysis.searchQueries.contains("한국 체코 월드컵 패배"))
        assertTrue(analysis.searchQueries.contains("대한민국 체코 월드컵 경기 결과"))
        assertTrue(analysis.ambiguityQuestions.any { question -> question.contains("어느 월드컵") })
    }
}

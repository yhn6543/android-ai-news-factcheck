package com.example.fakenews.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleBoilerplateCleanerTest {
    @Test
    fun removesShareAndRecommendationPhrases() {
        assertTrue(ArticleBoilerplateCleaner.isBoilerplateParagraph("공유하기"))
        assertTrue(ArticleBoilerplateCleaner.isBoilerplateParagraph("추천뉴스"))
        assertTrue(ArticleBoilerplateCleaner.isBoilerplateParagraph("공지사항"))
        assertTrue(ArticleBoilerplateCleaner.isBoilerplateParagraph("관련뉴스"))
        assertTrue(ArticleBoilerplateCleaner.isBoilerplateParagraph("많이 본 뉴스"))
        assertTrue(ArticleBoilerplateCleaner.isBoilerplateParagraph("SNS 공유"))
        assertTrue(ArticleBoilerplateCleaner.isBoilerplateParagraph("URL 복사"))
    }

    @Test
    fun removesCopyrightPhrase() {
        assertTrue(ArticleBoilerplateCleaner.isBoilerplateParagraph("무단 전재 및 재배포 금지"))
    }

    @Test
    fun keepsLongArticleSentenceEvenWhenReporterWordAppears() {
        assertFalse(
            ArticleBoilerplateCleaner.isBoilerplateParagraph(
                "현장 기자들이 확인한 자료에 따르면 주민들의 생활 변화가 장기간 이어지고 있습니다."
            )
        )
    }

    @Test
    fun removesReactionAndRatingWidgetPhrases() {
        assertTrue(ArticleBoilerplateCleaner.isBoilerplateParagraph("공감"))
        assertTrue(ArticleBoilerplateCleaner.isBoilerplateParagraph("평가하기"))
        assertTrue(ArticleBoilerplateCleaner.isBoilerplateParagraph("사용자 평가"))
        assertTrue(ArticleBoilerplateCleaner.isBoilerplateParagraph("댓글"))
        assertTrue(ArticleBoilerplateCleaner.isBoilerplateParagraph("싫어요"))
    }

    @Test
    fun removesSbsAppAndVideoCallToActionPhrases() {
        assertTrue(ArticleBoilerplateCleaner.isBoilerplateParagraph("SBS뉴스 앱 다운로드"))
        assertTrue(ArticleBoilerplateCleaner.isBoilerplateParagraph("SBS 뉴스 앱 다운로드"))
        assertTrue(ArticleBoilerplateCleaner.isBoilerplateParagraph("뉴스에 지식을 담다 - 스브스프리미엄 앱 다운로드"))
        assertTrue(ArticleBoilerplateCleaner.isBoilerplateParagraph("영상 시청"))
    }

    @Test
    fun keepsLongArticleSentenceContainingEvaluationWord() {
        assertFalse(
            ArticleBoilerplateCleaner.isBoilerplateParagraph(
                "전문가들은 정책 평가 과정에서 시민 의견을 충분히 반영해야 한다고 설명했습니다."
            )
        )
    }
}

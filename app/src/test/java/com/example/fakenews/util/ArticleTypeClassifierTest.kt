package com.example.fakenews.util

import com.example.fakenews.data.model.ArticleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleTypeClassifierTest {
    @Test
    fun liveBroadcastTitleIsExcluded() {
        val type = ArticleTypeClassifier.classify(
            title = "실시간 방송 보기",
            originalUrl = "https://news.sbs.co.kr/live"
        )

        assertEquals(ArticleType.LIVE_STREAM, type)
    }

    @Test
    fun schedulePageIsExcluded() {
        val type = ArticleTypeClassifier.classify(
            title = "오늘의 방송 편성표",
            originalUrl = "https://news.kbs.co.kr/schedule"
        )

        assertEquals(ArticleType.SCHEDULE_PAGE, type)
    }

    @Test
    fun replayPageIsExcluded() {
        val type = ArticleTypeClassifier.classify(
            title = "뉴스 다시보기",
            originalUrl = "https://imnews.imbc.com/replay"
        )

        assertEquals(ArticleType.REPLAY_PAGE, type)
    }

    @Test
    fun videoNewsArticleIsIncluded() {
        val type = ArticleTypeClassifier.classify(
            title = "영상 뉴스: 폭우 피해 현장",
            originalUrl = "https://news.sbs.co.kr/news/endPage.do?news_id=1",
            bodyText = "폭우 피해 현장을 취재한 실제 기사 본문입니다. 주민 대피 상황과 복구 계획을 설명합니다."
        )

        assertEquals(ArticleType.VIDEO_NEWS_ARTICLE, type)
        assertTrue(ArticleTypeClassifier.isNewsArticle(type))
    }

    @Test
    fun playableVideoUrlDoesNotExcludeNewsArticle() {
        val type = ArticleTypeClassifier.classify(
            title = "현장 영상으로 보는 오늘의 사건",
            originalUrl = "https://www.yna.co.kr/view/AKR202606100001",
            bodyText = "현장에서 확인된 내용을 정리한 기사 본문입니다. 영상은 참고 자료로 함께 제공됩니다.",
            videoUrl = "https://example.com/video.mp4"
        )

        assertEquals(ArticleType.VIDEO_NEWS_ARTICLE, type)
        assertTrue(ArticleTypeClassifier.isNewsArticle(type))
    }

    @Test
    fun kbsViewDoUrlIsTreatedAsNewsArticle() {
        val type = ArticleTypeClassifier.classify(
            title = "KBS view URL 기사",
            originalUrl = "https://news.kbs.co.kr/news/pc/view/view.do?ncd=1234567",
            bodyText = "KBS view URL에 포함된 실제 기사 본문입니다. 현장 취재 내용과 관계자 설명을 담고 있습니다."
        )

        assertEquals(ArticleType.NEWS_ARTICLE, type)
        assertTrue(ArticleTypeClassifier.isNewsArticle(type))
    }
}

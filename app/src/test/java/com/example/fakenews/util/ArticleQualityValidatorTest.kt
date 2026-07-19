package com.example.fakenews.util

import com.example.fakenews.data.model.ArticleType
import com.example.fakenews.data.model.NewsArticle
import com.example.fakenews.data.model.NewsPress
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleQualityValidatorTest {
    @Test
    fun emptyOriginalUrlIsInvalid() {
        val result = ArticleQualityValidator.validate(article(originalUrl = ""))

        assertFalse(result.isValid)
        assertTrue(result.reasons.contains("original_url_empty"))
    }

    @Test
    fun domainMismatchIsInvalid() {
        val result = ArticleQualityValidator.validate(
            article(press = NewsPress.MBC, originalUrl = "https://news.sbs.co.kr/news/endPage.do?news_id=1")
        )

        assertFalse(result.isValid)
        assertTrue(result.reasons.contains("domain_mismatch"))
    }

    @Test
    fun liveArticleIsInvalid() {
        val result = ArticleQualityValidator.validate(
            article(
                title = "실시간 방송 보기",
                originalUrl = "https://news.sbs.co.kr/live",
                press = NewsPress.SBS,
                articleType = ArticleType.LIVE_STREAM
            )
        )

        assertFalse(result.isValid)
        assertTrue(result.reasons.any { reason -> reason.startsWith("non_article_type") })
    }

    @Test
    fun titleContainingBodyCreatesWarningButCanBeCleaned() {
        val check = ArticleQualityValidator.cleanAndValidate(
            article(
                title = "정상 제목\n본문이 이어 붙은 잘못된 제목입니다.",
                originalUrl = "https://news.sbs.co.kr/news/endPage.do?news_id=1",
                press = NewsPress.SBS
            )
        )

        assertTrue(check.result.isValid)
        assertFalse(check.article.title.contains('\n'))
    }

    @Test
    fun boilerplateOnlyBodyIsInvalid() {
        val result = ArticleQualityValidator.validate(
            article(
                bodyParagraphs = listOf("공유하기", "추천뉴스", "댓글"),
                content = "공유하기\n추천뉴스\n댓글",
                summary = "공유하기",
                originalUrl = "https://news.sbs.co.kr/news/endPage.do?news_id=1",
                press = NewsPress.SBS
            )
        )

        assertFalse(result.isValid)
        assertTrue(result.reasons.contains("body_only_boilerplate"))
    }

    @Test
    fun reactionUiParagraphsAreRemovedDuringCleaning() {
        val check = ArticleQualityValidator.cleanAndValidate(
            article(
                press = NewsPress.MBC,
                originalUrl = "https://imnews.imbc.com/news/2026/society/article.html",
                bodyParagraphs = listOf(
                    "실제 취재 내용을 담은 충분한 길이의 첫 번째 기사 본문입니다.",
                    "자료사진",
                    "좋아요",
                    "훈훈해요",
                    "슬퍼요",
                    "화나요",
                    "후속요청",
                    "두 번째 기사 문단도 문맥을 이어가며 충분한 정보를 제공합니다."
                )
            )
        )

        val joined = check.article.bodyParagraphs.joinToString(" ")
        assertTrue(check.result.isValid)
        assertFalse(joined.contains("자료사진"))
        assertFalse(joined.contains("좋아요"))
        assertFalse(joined.contains("훈훈해요"))
        assertFalse(joined.contains("슬퍼요"))
        assertFalse(joined.contains("화나요"))
        assertFalse(joined.contains("후속요청"))
    }

    @Test
    fun videoNewsWithBodyIsValid() {
        val check = ArticleQualityValidator.cleanAndValidate(
            article(
                title = "영상 뉴스: 현장 취재",
                originalUrl = "https://www.yna.co.kr/view/AKR202606100001",
                press = NewsPress.YONHAP,
                videoUrl = "https://example.com/video.mp4",
                isVideoNews = true
            )
        )

        assertTrue(check.result.isValid)
        assertTrue(check.article.isVideoNews)
    }

    private fun article(
        title: String = "경제 뉴스 점검",
        press: NewsPress = NewsPress.SBS,
        originalUrl: String = "https://news.sbs.co.kr/news/endPage.do?news_id=1",
        summary: String = "기사 요약입니다. 핵심 내용을 간단히 설명합니다.",
        content: String = "첫 번째 기사 본문입니다. 독자가 이해할 수 있는 충분한 설명을 제공합니다.",
        bodyParagraphs: List<String> = listOf("첫 번째 기사 본문입니다.", "두 번째 기사 본문입니다."),
        videoUrl: String? = null,
        articleType: ArticleType = ArticleType.NEWS_ARTICLE,
        isVideoNews: Boolean = false
    ): NewsArticle =
        NewsArticle(
            id = ArticleIdentity.idFor(press, originalUrl, title),
            title = title,
            press = press,
            publishedAt = 1L,
            summary = summary,
            content = content,
            bodyParagraphs = bodyParagraphs,
            videoUrl = videoUrl,
            originalUrl = originalUrl,
            articleType = articleType,
            isVideoNews = isVideoNews
        )
}

package com.example.fakenews.util

import com.example.fakenews.data.model.NewsArticle
import com.example.fakenews.data.model.NewsPress
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YtnArticleValidationTest {
    @Test
    fun titleWithOnlyPressNameIsInvalid() {
        val result = ArticleQualityValidator.validate(article(title = "YTN"))

        assertFalse(result.isValid)
        assertTrue(result.reasons.contains("title_not_article_like"))
    }

    @Test
    fun googleRedirectOriginalUrlIsInvalidWithoutResolvedArticleUrl() {
        val result = ArticleQualityValidator.validate(
            article(originalUrl = "https://news.google.com/rss/articles/sample")
        )

        assertFalse(result.isValid)
        assertTrue(result.reasons.contains("domain_mismatch"))
    }

    @Test
    fun otherPressDomainIsInvalid() {
        val result = ArticleQualityValidator.validate(
            article(originalUrl = "https://imnews.imbc.com/news/2026/society/article.html")
        )

        assertFalse(result.isValid)
        assertTrue(result.reasons.contains("domain_mismatch"))
    }

    @Test
    fun ytnListPageIsInvalid() {
        val result = ArticleQualityValidator.validate(
            article(originalUrl = "https://www.ytn.co.kr/news/list.php")
        )

        assertFalse(result.isValid)
        assertTrue(result.reasons.any { reason -> reason.startsWith("non_article_type") })
    }

    private fun article(
        title: String = "정책 현장 점검 결과를 설명한 YTN 기사",
        originalUrl: String = "https://www.ytn.co.kr/_ln/0101_202606110001"
    ): NewsArticle {
        val paragraphs = listOf(
            "취재진이 현장에서 확인한 내용을 바탕으로 주요 사실을 정리한 기사 본문입니다.",
            "관계자 설명과 추가 검증 내용을 이어서 설명하는 두 번째 기사 문단입니다."
        )
        return NewsArticle(
            id = ArticleIdentity.idFor(NewsPress.YTN, originalUrl, title),
            title = title,
            press = NewsPress.YTN,
            publishedAt = 1L,
            publishedAtSource = "rss:pubDate",
            summary = paragraphs.first(),
            content = paragraphs.joinToString("\n\n"),
            bodyParagraphs = paragraphs,
            originalUrl = originalUrl
        )
    }
}

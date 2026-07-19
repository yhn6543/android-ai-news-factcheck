package com.example.fakenews.util

import com.example.fakenews.data.model.NewsArticle
import com.example.fakenews.data.model.NewsPress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsDeduplicatorTest {
    @Test
    fun removesDuplicatesWithSameOriginalUrl() {
        val result = NewsDeduplicator.deduplicate(
            listOf(
                article(id = "old", publishedAt = 1L, originalUrl = "https://example.com/news/1"),
                article(id = "new", publishedAt = 2L, originalUrl = "https://example.com/news/1")
            )
        )

        assertEquals(listOf("new"), result.map { article -> article.id })
    }

    @Test
    fun removesDuplicatesWithOnlyTrackingParameterDifference() {
        val result = NewsDeduplicator.deduplicate(
            listOf(
                article(id = "a", originalUrl = "https://example.com/news/1?utm_source=google&id=7"),
                article(id = "b", originalUrl = "http://example.com/news/1?id=7#top", publishedAt = 2L)
            )
        )

        assertEquals(listOf("b"), result.map { article -> article.id })
    }

    @Test
    fun keepsSameTitleAndPressWhenOriginalUrlDiffers() {
        val result = NewsDeduplicator.deduplicate(
            listOf(
                article(id = "a", title = "[속보] 경제 뉴스 점검", originalUrl = "https://example.com/a"),
                article(id = "b", title = "경제 뉴스 점검", originalUrl = "https://example.com/b", publishedAt = 2L)
            )
        )

        assertEquals(setOf("a", "b"), result.map { article -> article.id }.toSet())
    }

    @Test
    fun keepsSameTitleFromDifferentPresses() {
        val result = NewsDeduplicator.deduplicate(
            listOf(
                article(id = "mbc", press = NewsPress.MBC, title = "경제 뉴스 점검", originalUrl = "https://example.com/mbc"),
                article(id = "sbs", press = NewsPress.SBS, title = "경제 뉴스 점검", originalUrl = "https://example.com/sbs")
            )
        )

        assertEquals(setOf("mbc", "sbs"), result.map { article -> article.id }.toSet())
    }

    @Test
    fun keepsYtnArticleWhenOnlyTitleMatchesAnotherPress() {
        val result = NewsDeduplicator.deduplicate(
            listOf(
                article(id = "ytn", press = NewsPress.YTN, title = "동일 제목 기사", originalUrl = "https://www.ytn.co.kr/_ln/0101_1"),
                article(id = "kbs", press = NewsPress.KBS, title = "동일 제목 기사", originalUrl = "https://news.kbs.co.kr/news/1")
            )
        )

        assertEquals(setOf("ytn", "kbs"), result.map { article -> article.id }.toSet())
    }

    @Test
    fun removesYtnDuplicateWithSameNormalizedOriginalUrl() {
        val result = NewsDeduplicator.deduplicate(
            listOf(
                article(id = "old", press = NewsPress.YTN, originalUrl = "https://www.ytn.co.kr/_ln/0101_1?utm_source=google"),
                article(id = "new", press = NewsPress.YTN, originalUrl = "https://www.ytn.co.kr/_ln/0101_1", publishedAt = 2L)
            )
        )

        assertEquals(listOf("new"), result.map { article -> article.id })
    }

    @Test
    fun prefersLatestThenRicherArticle() {
        val latest = article(
            id = "latest",
            publishedAt = 2L,
            summary = "짧은 요약",
            content = "짧은 본문",
            originalUrl = "https://example.com/news/1"
        )
        val richButOld = article(
            id = "rich",
            publishedAt = 1L,
            summary = "매우 긴 요약입니다. 더 많은 정보를 담고 있습니다.",
            content = "매우 긴 본문입니다. 더 많은 정보를 담고 있습니다.",
            originalUrl = "https://example.com/news/1"
        )

        val result = NewsDeduplicator.deduplicate(listOf(richButOld, latest))

        assertEquals("latest", result.single().id)
    }

    @Test
    fun prefersRicherArticleWhenPublishedAtIsSame() {
        val plain = article(id = "plain", originalUrl = "https://example.com/news/1", publishedAt = 1L)
        val rich = article(
            id = "rich",
            originalUrl = "https://example.com/news/1",
            publishedAt = 1L,
            summary = "더 긴 요약",
            content = "더 긴 본문 내용을 담고 있습니다."
        )

        val result = NewsDeduplicator.deduplicate(listOf(plain, rich))

        assertEquals("rich", result.single().id)
        assertTrue(result.single().content.length > plain.content.length)
    }

    private fun article(
        id: String,
        press: NewsPress = NewsPress.MBC,
        title: String = "경제 뉴스 점검",
        publishedAt: Long = 1L,
        summary: String = "요약",
        content: String = "본문",
        imageUrl: String? = null,
        originalUrl: String = "https://example.com/$id"
    ): NewsArticle =
        NewsArticle(
            id = id,
            title = title,
            press = press,
            publishedAt = publishedAt,
            summary = summary,
            content = content,
            imageUrl = imageUrl,
            originalUrl = originalUrl,
            keywords = emptyList()
        )
}

package com.example.fakenews.data.remote

import com.example.fakenews.data.model.NewsPress
import com.example.fakenews.data.model.NewsSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlCrawlerParserTest {
    @Test
    fun extractsArticleLinksAndTitlesFromSampleHtml() {
        val articles = HtmlCrawlerParser.parse(
            html = sampleHtml(),
            baseUrl = "https://example.com/news",
            press = NewsPress.MBC,
            nowMillis = 1_000L
        )

        assertTrue(articles.isNotEmpty())
        assertEquals("충분히 긴 테스트 기사 제목 1", articles.first().title)
        assertEquals("https://example.com/news/1", articles.first().originalUrl)
        assertEquals(NewsSourceType.HTML_CRAWLING, articles.first().sourceType)
    }

    @Test
    fun removesDuplicateUrlsAndShortTitles() {
        val articles = HtmlCrawlerParser.parse(
            html = sampleHtml(),
            baseUrl = "https://example.com/news",
            press = NewsPress.SBS,
            nowMillis = 1_000L
        )

        assertEquals(articles.size, articles.distinctBy { article -> article.originalUrl }.size)
        assertTrue(articles.none { article -> article.title == "짧음" })
    }

    @Test
    fun limitsResultsToFiveArticles() {
        val html = """
        <html><body>
          ${(1..8).joinToString("\n") { index -> "<article><a href=\"/news/$index\">충분히 긴 테스트 기사 제목 $index</a></article>" }}
        </body></html>
        """.trimIndent()

        val articles = HtmlCrawlerParser.parse(
            html = html,
            baseUrl = "https://example.com",
            press = NewsPress.KBS,
            nowMillis = 1_000L
        )

        assertEquals(NewsFetchConfig.MAX_ARTICLES_PER_PRESS, articles.size)
    }

    private fun sampleHtml(): String =
        """
        <html>
          <head>
            <meta name="description" content="HTML 테스트 요약">
            <meta property="og:image" content="https://example.com/og.jpg">
          </head>
          <body>
            <article><a href="/news/1">충분히 긴 테스트 기사 제목 1</a></article>
            <article><a href="/news/1">충분히 긴 테스트 기사 제목 1 중복</a></article>
            <article><a href="/news/2">짧음</a></article>
            <article><a href="https://example.com/news/3"><img src="/image.jpg">충분히 긴 테스트 기사 제목 3</a></article>
          </body>
        </html>
        """.trimIndent()
}

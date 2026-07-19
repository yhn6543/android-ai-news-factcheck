package com.example.fakenews.data.remote

import com.example.fakenews.data.model.NewsArticle
import com.example.fakenews.data.model.NewsPress
import com.example.fakenews.util.DateParser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ArticleDetailRemoteDataSourceTest {
    @Test
    fun fetchDetailEnrichesTitleAndBodyWithoutMixingThem() = runTest {
        val articleUrl = "https://news.sbs.co.kr/news/endPage.do?news_id=1"
        val dataSource = ArticleDetailRemoteDataSource(
            fetcher = FakeHtmlPageFetcher(
                mapOf(
                    articleUrl to """
                    <html>
                      <head>
                        <meta property="og:title" content="Clean detail title | SBS 뉴스">
                        <meta name="description" content="Detail summary">
                        <meta property="og:image" content="/image.jpg">
                      </head>
                      <body>
                        <article>
                          <h1>Clean detail title</h1>
                          <p>Clean detail title</p>
                          <p>First full body paragraph.</p>
                          <p>Second full body paragraph.</p>
                        </article>
                      </body>
                    </html>
                    """.trimIndent()
                )
            )
        )

        val result = dataSource.fetchDetail(baseArticle(articleUrl))

        assertEquals("Clean detail title", result.title)
        assertEquals(listOf("First full body paragraph.", "Second full body paragraph."), result.bodyParagraphs)
        assertEquals("Detail summary", result.summary)
        assertEquals("https://news.sbs.co.kr/image.jpg", result.imageUrl)
    }

    @Test
    fun ytnTrustedDetailKeepsOriginalUrlAndRemovesBoilerplate() = runTest {
        val articleUrl = "https://www.ytn.co.kr/_ln/0101_202606110001"
        val dataSource = ArticleDetailRemoteDataSource(
            fetcher = FakeHtmlPageFetcher(
                mapOf(
                    articleUrl to """
                    <html>
                      <head><meta property="og:title" content="YTN detail title | YTN 뉴스"></head>
                      <body>
                        <div id="CmAdContent">
                          <p>YTN detail title</p>
                          <p>YTN body paragraph one.</p>
                          <p>YTN body paragraph two.</p>
                          <p>공유하기</p>
                          <p>추천뉴스</p>
                          <p>Copyright YTN All rights reserved.</p>
                        </div>
                      </body>
                    </html>
                    """.trimIndent()
                )
            )
        )

        val result = dataSource.fetchDetail(baseArticle(articleUrl, NewsPress.YTN))

        assertEquals(articleUrl, result.originalUrl)
        assertEquals("YTN detail title", result.title)
        assertEquals(listOf("YTN body paragraph one.", "YTN body paragraph two."), result.bodyParagraphs)
    }

    @Test
    fun untrustedDetailUrlFallsBackToOriginalArticle() = runTest {
        val article = baseArticle("https://example.com/not-ytn", NewsPress.YTN)
        val dataSource = ArticleDetailRemoteDataSource(
            fetcher = FakeHtmlPageFetcher(emptyMap())
        )

        val result = dataSource.fetchDetail(article)

        assertEquals(article, result)
    }

    @Test
    fun detailPublishedTimeOverridesRssPublishedTimeWhenAvailable() = runTest {
        val articleUrl = "https://imnews.imbc.com/news/2026/society/article/6829534_36918.html"
        val dataSource = ArticleDetailRemoteDataSource(
            fetcher = FakeHtmlPageFetcher(
                mapOf(
                    articleUrl to """
                    <html>
                      <head>
                        <meta property="og:title" content="MBC detail title">
                        <meta property="article:published_time" content="2026-06-11T19:10:55+09:00">
                      </head>
                      <body>
                        <div class="news_txt">
                          <p>MBC detail title</p>
                          <p>MBC original article body paragraph.</p>
                        </div>
                      </body>
                    </html>
                    """.trimIndent()
                )
            )
        )

        val result = dataSource.fetchDetail(
            baseArticle(articleUrl, NewsPress.MBC).copy(
                publishedAt = 1_735_689_600_000L,
                publishedAtSource = "rss:pubDate"
            )
        )

        assertEquals(DateParser.parseEpochMillis("2026-06-11T19:10:55+09:00"), result.publishedAt)
        assertEquals("html:meta[property=article:published_time]", result.publishedAtSource)
    }

    private fun baseArticle(
        originalUrl: String,
        press: NewsPress = NewsPress.SBS
    ): NewsArticle =
        NewsArticle(
            id = "article",
            title = "Old title",
            press = press,
            publishedAt = 1L,
            summary = "Old summary",
            content = "Old content",
            imageUrl = null,
            originalUrl = originalUrl
        )

    private class FakeHtmlPageFetcher(
        private val pages: Map<String, String>
    ) : HtmlPageFetcher {
        override suspend fun fetch(url: String): String =
            pages[url] ?: error("missing fake page: $url")
    }
}

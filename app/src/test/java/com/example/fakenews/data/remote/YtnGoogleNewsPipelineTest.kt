package com.example.fakenews.data.remote

import com.example.fakenews.data.model.NewsPress
import com.example.fakenews.data.repository.MultiSourceNewsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YtnGoogleNewsPipelineTest {
    @Test
    fun ytnPipelineRecordsRawResolvePolicyTitleDetailAndFinalStages() = runTest {
        val finalUrl = "https://www.ytn.co.kr/_ln/0101_202606120001"
        val titleOnlyUrl = "https://www.ytn.co.kr/_ln/0101_202606120002"
        val domainMismatchUrl = "https://imnews.imbc.com/news/2026/society/article.html"
        val listUrl = "https://www.ytn.co.kr/news/list.php"
        val rssUrl = GoogleNewsRssConfig.googleNewsFeed("ytn.co.kr/_ln/")
        val rssDataSource = RssNewsDataSource(
            feedConfigs = listOf(
                RssFeedConfig(
                    press = NewsPress.YTN,
                    url = rssUrl,
                    originalUrlMode = RssOriginalUrlMode.GOOGLE_NEWS,
                    label = GoogleNewsRssConfig.GOOGLE_NEWS_RSS_LABEL,
                    enrichFromOriginalUrl = true
                )
            ),
            fetcher = StaticRssFeedFetcher(
                ytnGoogleRss(
                    listOf(
                        item("YTN field report confirms policy response", "https://news.google.com/rss/articles/final"),
                        item("YTN", "https://news.google.com/rss/articles/title-only"),
                        item("YTN unresolved redirect", "https://news.google.com/rss/articles/unresolved"),
                        item("Other press item", "https://news.google.com/rss/articles/domain-mismatch"),
                        item("YTN list page item", "https://news.google.com/rss/articles/list-page")
                    )
                )
            ),
            originalUrlResolver = OriginalUrlResolver { url ->
                when (url) {
                    "https://news.google.com/rss/articles/final" -> finalUrl
                    "https://news.google.com/rss/articles/title-only" -> titleOnlyUrl
                    "https://news.google.com/rss/articles/domain-mismatch" -> domainMismatchUrl
                    "https://news.google.com/rss/articles/list-page" -> listUrl
                    else -> null
                }
            },
            detailDataSource = ArticleDetailRemoteDataSource(
                fetcher = StaticHtmlPageFetcher(
                    mapOf(
                        finalUrl to """
                        <html>
                          <head>
                            <meta property="og:title" content="YTN field report confirms policy response">
                            <meta name="description" content="YTN detailed summary">
                          </head>
                          <body>
                            <div id="CmAdContent">
                              <p>YTN field report confirms policy response</p>
                              <p>First YTN detail paragraph contains enough reporting context for validation.</p>
                              <p>Second YTN detail paragraph adds confirmed background and response details.</p>
                            </div>
                          </body>
                        </html>
                        """.trimIndent()
                    )
                )
            )
        )
        val repository = MultiSourceNewsRepository(dataSources = listOf(rssDataSource))

        val result = repository.searchNews(setOf(NewsPress.YTN), emptyList())
        val snapshot = YtnPipelineDebugStore.latestSnapshot()
        val excludedUrls = snapshot.items
            .filter { item -> !item.finalDisplay }
            .mapNotNull { item -> item.resolvedUrl }
            .toSet()
        val finalUrls = result.articles.map { article -> article.originalUrl }.toSet()

        assertEquals(1, result.articles.size)
        assertEquals(finalUrl, result.articles.single().originalUrl)
        assertFalse(result.articles.single().originalUrl.contains("news.google.com"))
        assertEquals(5, snapshot.rawItemCount)
        assertEquals(4, snapshot.resolveSuccessCount)
        assertEquals(1, snapshot.resolveFailureCount)
        assertEquals(1, snapshot.domainMismatchCount)
        assertEquals(1, snapshot.articleUrlPolicyMismatchCount)
        assertEquals(1, snapshot.titleNotArticleLikeCount)
        assertEquals(1, snapshot.finalDisplayArticleCount)
        assertTrue(finalUrls.intersect(excludedUrls).isEmpty())
        assertTrue(snapshot.toMarkdownSection().contains("## YTN Debug Section"))
        assertTrue(snapshot.items.first().detailFetchSuccess == true)
        assertTrue(snapshot.items.first().paragraphCount == 2)
    }

    private fun item(
        title: String,
        link: String
    ): String =
        """
        <item>
          <title>$title</title>
          <link>$link</link>
          <pubDate>Fri, 12 Jun 2026 12:00:00 +0900</pubDate>
          <description>${"$title description with enough context for tracing and card preview."}</description>
        </item>
        """.trimIndent()

    private fun ytnGoogleRss(items: List<String>): String =
        """
        <rss version="2.0">
          <channel>
            ${items.joinToString("\n")}
          </channel>
        </rss>
        """.trimIndent()

    private class StaticRssFeedFetcher(
        private val xml: String
    ) : RssFeedFetcher {
        override suspend fun fetch(url: String): String = xml
    }

    private class StaticHtmlPageFetcher(
        private val pages: Map<String, String>
    ) : HtmlPageFetcher {
        override suspend fun fetch(url: String): String =
            pages[url] ?: error("missing page: $url")
    }
}

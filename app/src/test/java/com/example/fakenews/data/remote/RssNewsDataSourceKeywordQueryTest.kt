package com.example.fakenews.data.remote

import com.example.fakenews.data.model.NewsPress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RssNewsDataSourceKeywordQueryTest {
    @Test
    fun googleNewsRssSearchAddsKeywordsToFeedQuery() = runTest {
        val googleUrl = "https://news.google.com/rss/articles/mbc-keyword"
        val originalUrl = "https://imnews.imbc.com/news/2026/society/article.html"
        val feed = RssFeedConfig(
            press = NewsPress.MBC,
            url = "https://news.google.com/rss/search?q=site:imnews.imbc.com%20MBC&hl=ko&gl=KR&ceid=KR:ko",
            originalUrlMode = RssOriginalUrlMode.GOOGLE_NEWS,
            label = GoogleNewsRssConfig.GOOGLE_NEWS_RSS_LABEL
        )
        val fetcher = RecordingRssFeedFetcher(sampleRss(link = googleUrl))
        val dataSource = RssNewsDataSource(
            feedConfigs = listOf(feed),
            fetcher = fetcher,
            originalUrlResolver = OriginalUrlResolver { url -> if (url == googleUrl) originalUrl else null }
        )

        val result = dataSource.fetch(NewsPress.MBC, keywords = listOf("semiconductor", "AI"))

        assertTrue(result.success)
        assertEquals(
            "https://news.google.com/rss/search?q=site:imnews.imbc.com%20MBC%20semiconductor%20AI&hl=ko&gl=KR&ceid=KR:ko",
            fetcher.calls.first()
        )
        assertTrue(fetcher.calls.size > 1)
        assertEquals(originalUrl, result.articles.single().originalUrl)
        assertTrue("semiconductor" in result.articles.single().keywords)
        assertTrue("AI" in result.articles.single().keywords)
    }

    @Test
    fun googleNewsRssSearchTriesPressNameVariantWhenSiteVariantIsEmpty() = runTest {
        val googleUrl = "https://news.google.com/rss/articles/mbc-worldcup"
        val originalUrl = "https://imnews.imbc.com/news/2026/sports/worldcup.html"
        val feed = RssFeedConfig(
            press = NewsPress.MBC,
            url = "https://news.google.com/rss/search?q=site:imnews.imbc.com%20MBC&hl=ko&gl=KR&ceid=KR:ko",
            originalUrlMode = RssOriginalUrlMode.GOOGLE_NEWS,
            label = GoogleNewsRssConfig.GOOGLE_NEWS_RSS_LABEL
        )
        val fetcher = RecordingRssFeedFetcher { url ->
            val decodedUrl = url.decodedUrl()
            if ("site:imnews.imbc.com" in decodedUrl) {
                emptyRss()
            } else if ("worldcup MBC" in decodedUrl) {
                sampleRss(link = googleUrl)
            } else {
                emptyRss()
            }
        }
        val dataSource = RssNewsDataSource(
            feedConfigs = listOf(feed),
            fetcher = fetcher,
            originalUrlResolver = OriginalUrlResolver { url -> if (url == googleUrl) originalUrl else null }
        )

        val result = dataSource.fetch(NewsPress.MBC, keywords = listOf("worldcup"))

        assertTrue(result.success)
        assertEquals(originalUrl, result.articles.single().originalUrl)
        assertTrue(fetcher.calls.first().decodedUrl().contains("site:imnews.imbc.com"))
        assertTrue(fetcher.calls.any { url -> "worldcup MBC" in url.decodedUrl() && "site:" !in url.decodedUrl() })
    }

    @Test
    fun googleNewsRssSearchCanInspectBeyondFirstFiveCandidates() = runTest {
        val feed = RssFeedConfig(
            press = NewsPress.MBC,
            url = "https://news.google.com/rss/search?q=site:imnews.imbc.com%20MBC&hl=ko&gl=KR&ceid=KR:ko",
            originalUrlMode = RssOriginalUrlMode.GOOGLE_NEWS,
            label = GoogleNewsRssConfig.GOOGLE_NEWS_RSS_LABEL
        )
        val fetcher = RecordingRssFeedFetcher(sampleRssItems(count = 20))
        val resolverCallCount = AtomicInteger(0)
        val dataSource = RssNewsDataSource(
            feedConfigs = listOf(feed),
            fetcher = fetcher,
            originalUrlResolver = OriginalUrlResolver { url ->
                resolverCallCount.incrementAndGet()
                val id = url.substringAfterLast("mbc-keyword-")
                if (id.toInt() <= 7) {
                    "https://www.ytn.co.kr/_ln/0101_20260611000$id"
                } else {
                    "https://imnews.imbc.com/news/2026/society/article$id.html"
                }
            },
            maxFeedsPerPress = NewsFetchConfig.MAIN_DEFAULT_GOOGLE_NEWS_MAX_FEEDS_PER_PRESS,
            maxItemsPerFeed = NewsFetchConfig.MAIN_DEFAULT_GOOGLE_NEWS_MAX_ITEMS_PER_FEED
        )

        val result = dataSource.fetch(NewsPress.MBC, keywords = listOf("semiconductor"))

        assertTrue(result.success)
        assertEquals(NewsFetchConfig.MAX_ARTICLES_PER_PRESS, result.articles.size)
        assertTrue(resolverCallCount.get() > NewsFetchConfig.MAX_ARTICLES_PER_PRESS)
        assertEquals(1, fetcher.calls.size)
    }

    @Test
    fun googleNewsRssSearchDoesNotResolveAllOneHundredItemsSequentially() = runTest {
        val feed = RssFeedConfig(
            press = NewsPress.MBC,
            url = "https://news.google.com/rss/search?q=site:imnews.imbc.com%20MBC&hl=ko&gl=KR&ceid=KR:ko",
            originalUrlMode = RssOriginalUrlMode.GOOGLE_NEWS,
            label = GoogleNewsRssConfig.GOOGLE_NEWS_RSS_LABEL
        )
        val resolverCallCount = AtomicInteger(0)
        val dataSource = RssNewsDataSource(
            feedConfigs = listOf(feed),
            fetcher = RecordingRssFeedFetcher(sampleRssItems(count = 100)),
            originalUrlResolver = OriginalUrlResolver { url ->
                resolverCallCount.incrementAndGet()
                val id = url.substringAfterLast("mbc-keyword-")
                "https://imnews.imbc.com/news/2026/society/article$id.html"
            }
        )

        val result = dataSource.fetch(NewsPress.MBC, keywords = listOf("semiconductor"))

        assertTrue(result.success)
        assertEquals(NewsFetchConfig.MAX_ARTICLES_PER_PRESS, result.articles.size)
        assertEquals(NewsFetchConfig.MAX_ARTICLES_PER_PRESS, resolverCallCount.get())
    }

    @Test
    fun googleNewsRssSearchKeepsPartialCandidatesWhenLaterResolvesFail() = runTest {
        val feed = RssFeedConfig(
            press = NewsPress.MBC,
            url = "https://news.google.com/rss/search?q=site:imnews.imbc.com%20MBC&hl=ko&gl=KR&ceid=KR:ko",
            originalUrlMode = RssOriginalUrlMode.GOOGLE_NEWS,
            label = GoogleNewsRssConfig.GOOGLE_NEWS_RSS_LABEL
        )
        val dataSource = RssNewsDataSource(
            feedConfigs = listOf(feed),
            fetcher = RecordingRssFeedFetcher(sampleRssItems(count = 10)),
            originalUrlResolver = OriginalUrlResolver { url ->
                val id = url.substringAfterLast("mbc-keyword-").toInt()
                if (id <= 3) {
                    "https://imnews.imbc.com/news/2026/society/article$id.html"
                } else {
                    null
                }
            }
        )

        val result = dataSource.fetch(NewsPress.MBC, keywords = listOf("semiconductor"))

        assertTrue(result.success)
        assertEquals(3, result.articles.size)
        assertTrue(result.articles.all { article -> "semiconductor" in article.keywords })
    }

    @Test
    fun googleNewsRssSearchDoesNotAddDateRestrictionToQueryVariants() = runTest {
        val feed = RssFeedConfig(
            press = NewsPress.SBS,
            url = "https://news.google.com/rss/search?q=site:news.sbs.co.kr&hl=ko&gl=KR&ceid=KR:ko",
            originalUrlMode = RssOriginalUrlMode.GOOGLE_NEWS,
            label = GoogleNewsRssConfig.GOOGLE_NEWS_RSS_LABEL
        )
        val fetcher = RecordingRssFeedFetcher(emptyRss())
        val dataSource = RssNewsDataSource(
            feedConfigs = listOf(feed),
            fetcher = fetcher,
            originalUrlResolver = OriginalUrlResolver { null },
            maxFeedsPerPress = NewsFetchConfig.MAIN_DEFAULT_GOOGLE_NEWS_MAX_FEEDS_PER_PRESS,
            maxItemsPerFeed = NewsFetchConfig.MAIN_DEFAULT_GOOGLE_NEWS_MAX_ITEMS_PER_FEED
        )

        dataSource.fetch(NewsPress.SBS, keywords = listOf("worldcup"))

        assertTrue(fetcher.calls.size > 1)
        fetcher.calls.map { call -> call.decodedUrl() }.forEach { decodedUrl ->
            assertFalse(decodedUrl.contains("when:"))
            assertFalse(decodedUrl.contains("after:"))
            assertFalse(decodedUrl.contains("before:"))
        }
    }

    @Test
    fun directOfficialRssDoesNotChangeFeedUrlForKeywords() = runTest {
        val feed = RssFeedConfig(
            press = NewsPress.SBS,
            url = "https://news.sbs.co.kr/news/newsflashRssFeed.do?plink=RSSREADER"
        )
        val fetcher = RecordingRssFeedFetcher(
            sampleRss(link = "https://news.sbs.co.kr/news/endPage.do?news_id=N1000000001")
        )
        val dataSource = RssNewsDataSource(
            feedConfigs = listOf(feed),
            fetcher = fetcher
        )

        val result = dataSource.fetch(NewsPress.SBS, keywords = listOf("semiconductor"))

        assertTrue(result.success)
        assertEquals(feed.url, fetcher.calls.single())
    }

    @Test
    fun directOfficialRssSearchFallbackCanReturnMoreThanFiveRawCandidates() = runTest {
        val feed = RssFeedConfig(
            press = NewsPress.SBS,
            url = "https://news.sbs.co.kr/news/newsflashRssFeed.do?plink=RSSREADER"
        )
        val dataSource = RssNewsDataSource(
            feedConfigs = listOf(feed),
            fetcher = RecordingRssFeedFetcher(directRssItems(count = 20))
        )

        val result = dataSource.fetch(NewsPress.SBS, keywords = listOf("rare-keyword"))

        assertTrue(result.success)
        assertEquals(20, result.articles.size)
    }

    private class RecordingRssFeedFetcher(
        private val xmlProvider: (String) -> String
    ) : RssFeedFetcher {
        constructor(xml: String) : this({ xml })

        val calls = mutableListOf<String>()

        override suspend fun fetch(url: String): String {
            calls += url
            return xmlProvider(url)
        }
    }

    private fun sampleRss(link: String): String =
        """
        <rss version="2.0">
          <channel>
            <item>
              <title>Keyword query article title</title>
              <link>$link</link>
              <pubDate>Wed, 01 Jan 2025 00:00:00 GMT</pubDate>
              <description>This is a long enough news summary for a collected RSS article preview.</description>
            </item>
          </channel>
        </rss>
        """.trimIndent()

    private fun sampleRssItems(count: Int): String =
        """
        <rss version="2.0">
          <channel>
            ${(1..count).joinToString("\n") { index ->
            """
            <item>
              <title>Keyword query article title $index</title>
              <link>https://news.google.com/rss/articles/mbc-keyword-$index</link>
              <pubDate>Wed, 01 Jan 2025 00:${index.toString().padStart(2, '0')}:00 GMT</pubDate>
              <description>This is a long enough news summary for collected RSS article preview $index.</description>
            </item>
            """.trimIndent()
        }}
          </channel>
        </rss>
        """.trimIndent()

    private fun directRssItems(count: Int): String =
        """
        <rss version="2.0">
          <channel>
            ${(1..count).joinToString("\n") { index ->
            """
            <item>
              <title>SBS latest article $index</title>
              <link>https://news.sbs.co.kr/news/endPage.do?news_id=N100000${index.toString().padStart(4, '0')}</link>
              <pubDate>Wed, 01 Jan 2025 00:${index.toString().padStart(2, '0')}:00 GMT</pubDate>
              <description>This is a long enough SBS summary for fallback candidate $index.</description>
            </item>
            """.trimIndent()
        }}
          </channel>
        </rss>
        """.trimIndent()

    private fun emptyRss(): String =
        """
        <rss version="2.0">
          <channel></channel>
        </rss>
        """.trimIndent()

    private fun String.decodedUrl(): String =
        URLDecoder.decode(this, StandardCharsets.UTF_8.name())
}

package com.example.fakenews.data.remote

import com.example.fakenews.data.model.NewsPress
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KbsGoogleNewsRssSourceTest {
    @Test
    fun kbsDefaultFeedsTryOfficialRssBeforeGoogleNewsFallback() {
        val kbsFeeds = RssFeedConfig.defaultFeeds.filter { config -> config.press == NewsPress.KBS }

        assertEquals(3, kbsFeeds.size)
        assertEquals(RssFeedConfig.KBS_OFFICIAL_RSS_URL, kbsFeeds[0].url)
        assertEquals(RssOriginalUrlMode.DIRECT, kbsFeeds[0].originalUrlMode)
        assertEquals(RssOriginalUrlMode.GOOGLE_NEWS, kbsFeeds[1].originalUrlMode)
        assertTrue(kbsFeeds[1].url.startsWith("https://news.google.com/rss/search"))
        assertEquals("Google News RSS", kbsFeeds[1].label)
        assertEquals(RssOriginalUrlMode.GOOGLE_NEWS, kbsFeeds[2].originalUrlMode)
        assertTrue(kbsFeeds[2].url.contains("news/mobile/view/view.do"))
    }

    @Test
    fun emptyOfficialRssFallsBackToGoogleNewsRss() = runTest {
        val googleUrl = "https://news.google.com/rss/articles/kbs-fallback"
        val kbsUrl = "https://news.kbs.co.kr/news/pc/view/view.do?ncd=456"
        val officialFeed = RssFeedConfig(NewsPress.KBS, RssFeedConfig.KBS_OFFICIAL_RSS_URL)
        val googleFeed = kbsGoogleFeed()
        val fetcher = RecordingRssFeedFetcher(
            responses = mapOf(
                officialFeed.url to emptyRss(),
                googleFeed.url to googleNewsRss(link = googleUrl)
            )
        )
        val dataSource = RssNewsDataSource(
            feedConfigs = listOf(officialFeed, googleFeed),
            fetcher = fetcher,
            originalUrlResolver = OriginalUrlResolver { url -> if (url == googleUrl) kbsUrl else null }
        )

        val result = dataSource.fetch(NewsPress.KBS, emptyList())

        assertTrue(result.success)
        assertEquals(listOf(officialFeed.url, googleFeed.url), fetcher.calls)
        assertEquals(kbsUrl, result.articles.single().originalUrl)
    }

    @Test
    fun googleNewsRedirectResolvedToKbsArticleIsAccepted() = runTest {
        val googleUrl = "https://news.google.com/rss/articles/kbs1"
        val kbsUrl = "https://news.kbs.co.kr/news/pc/view/view.do?ncd=123"
        val dataSource = RssNewsDataSource(
            feedConfigs = listOf(kbsGoogleFeed()),
            fetcher = StaticRssFeedFetcher(googleNewsRss(link = googleUrl)),
            originalUrlResolver = OriginalUrlResolver { url -> if (url == googleUrl) kbsUrl else null }
        )

        val result = dataSource.fetch(NewsPress.KBS, emptyList())

        assertTrue(result.success)
        assertEquals(kbsUrl, result.articles.single().originalUrl)
        assertEquals(1_735_689_600_000L, result.articles.single().publishedAt)
        assertEquals("rss:pubDate", result.articles.single().publishedAtSource)
        assertEquals("Google News RSS", result.articles.single().sourceLabel)
    }

    @Test
    fun googleNewsRedirectResolvedOutsideKbsDomainIsRejectedForKbs() = runTest {
        val googleUrl = "https://news.google.com/rss/articles/not-kbs"
        val otherPressUrl = "https://www.ytn.co.kr/_ln/0101_202606110001"
        val dataSource = RssNewsDataSource(
            feedConfigs = listOf(kbsGoogleFeed()),
            fetcher = StaticRssFeedFetcher(googleNewsRss(link = googleUrl)),
            originalUrlResolver = OriginalUrlResolver { url -> if (url == googleUrl) otherPressUrl else null }
        )

        val result = dataSource.fetch(NewsPress.KBS, emptyList())

        assertTrue(result.articles.isEmpty())
    }

    @Test
    fun googleNewsRedirectResolvedToKbsLiveUrlIsRejectedForKbs() = runTest {
        val googleUrl = "https://news.google.com/rss/articles/kbs-live"
        val liveUrl = "https://news.kbs.co.kr/live"
        val dataSource = RssNewsDataSource(
            feedConfigs = listOf(kbsGoogleFeed()),
            fetcher = StaticRssFeedFetcher(googleNewsRss(link = googleUrl)),
            originalUrlResolver = OriginalUrlResolver { url -> if (url == googleUrl) liveUrl else null }
        )

        val result = dataSource.fetch(NewsPress.KBS, emptyList())

        assertTrue(result.articles.isEmpty())
    }

    @Test
    fun unresolvedGoogleNewsRedirectIsRejected() = runTest {
        val dataSource = RssNewsDataSource(
            feedConfigs = listOf(kbsGoogleFeed()),
            fetcher = StaticRssFeedFetcher(googleNewsRss(link = "https://news.google.com/rss/articles/unresolved")),
            originalUrlResolver = OriginalUrlResolver { null }
        )

        val result = dataSource.fetch(NewsPress.KBS, emptyList())

        assertTrue(result.articles.isEmpty())
    }

    private fun kbsGoogleFeed(): RssFeedConfig =
        RssFeedConfig(
            press = NewsPress.KBS,
            url = "https://news.google.com/rss/search?q=site:news.kbs.co.kr/news/pc/view/view.do&hl=ko&gl=KR&ceid=KR:ko",
            originalUrlMode = RssOriginalUrlMode.GOOGLE_NEWS,
            label = "Google News RSS"
        )

    private fun emptyRss(): String =
        """
        <rss version="2.0">
          <channel></channel>
        </rss>
        """.trimIndent()

    private fun googleNewsRss(link: String): String =
        """
        <rss version="2.0">
          <channel>
            <item>
              <title>KBS Google News RSS 테스트 기사</title>
              <link>$link</link>
              <pubDate>Wed, 01 Jan 2025 00:00:00 GMT</pubDate>
              <description>KBS 기사 본문 요약입니다. 원본 URL 확인 후에만 표시합니다.</description>
            </item>
          </channel>
        </rss>
        """.trimIndent()

    private class StaticRssFeedFetcher(
        private val xml: String
    ) : RssFeedFetcher {
        override suspend fun fetch(url: String): String = xml
    }

    private class RecordingRssFeedFetcher(
        private val responses: Map<String, String>
    ) : RssFeedFetcher {
        val calls = mutableListOf<String>()

        override suspend fun fetch(url: String): String {
            calls += url
            return responses.getValue(url)
        }
    }
}

package com.example.fakenews.data.remote

import com.example.fakenews.data.model.NewsPress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class GoogleNewsRssConfigTest {
    @Test
    fun defaultGoogleNewsConfigsUseRequestedUrls() {
        val urlsByPress = GoogleNewsRssConfig.defaultConfigs.associate { config ->
            config.press to config.url
        }

        val kbsUrls = GoogleNewsRssConfig.defaultConfigs
            .filter { config -> config.press == NewsPress.KBS }
            .map { config -> config.url }
        assertEquals(
            listOf(
                "https://news.google.com/rss/search?q=site:news.kbs.co.kr%20KBS&hl=ko&gl=KR&ceid=KR:ko",
                "https://news.google.com/rss/search?q=site:news.kbs.co.kr/news/mobile/view/view.do&hl=ko&gl=KR&ceid=KR:ko"
            ),
            kbsUrls
        )
        assertEquals(
            "https://news.google.com/rss/search?q=site:imnews.imbc.com%20MBC&hl=ko&gl=KR&ceid=KR:ko",
            urlsByPress[NewsPress.MBC]
        )
        assertEquals(
            "https://news.google.com/rss/search?q=site:yna.co.kr&hl=ko&gl=KR&ceid=KR:ko",
            urlsByPress[NewsPress.YONHAP]
        )
        assertEquals(
            "https://news.google.com/rss/search?q=site:news.sbs.co.kr&hl=ko&gl=KR&ceid=KR:ko",
            urlsByPress[NewsPress.SBS]
        )
        assertEquals(
            listOf(
                "https://news.google.com/rss/search?q=site:ytn.co.kr/_ln/&hl=ko&gl=KR&ceid=KR:ko",
                "https://news.google.com/rss/search?q=site:www.ytn.co.kr/_ln/&hl=ko&gl=KR&ceid=KR:ko",
                "https://news.google.com/rss/search?q=site:m.ytn.co.kr/_ln/&hl=ko&gl=KR&ceid=KR:ko"
            ),
            GoogleNewsRssConfig.defaultConfigs
                .filter { config -> config.press == NewsPress.YTN }
                .map { config -> config.url }
        )
    }

    @Test
    fun googleNewsFeedBuildsDomainOnlySearchUrl() {
        assertEquals(
            "https://news.google.com/rss/search?q=site:news.kbs.co.kr/news/pc/view/view.do&hl=ko&gl=KR&ceid=KR:ko",
            GoogleNewsRssConfig.googleNewsFeed("news.kbs.co.kr/news/pc/view/view.do")
        )
    }

    @Test
    fun googleNewsConfigsUseGoogleNewsRssModeWhenConverted() {
        GoogleNewsRssConfig.defaultConfigs.forEach { config ->
            val feed = config.toRssFeedConfig()

            assertEquals(RssOriginalUrlMode.GOOGLE_NEWS, feed.originalUrlMode)
            assertEquals(GoogleNewsRssConfig.GOOGLE_NEWS_RSS_LABEL, feed.label)
            assertEquals("news.google.com", PressDomainForTest.hostFrom(feed.url))
            assertEquals(true, feed.enrichFromOriginalUrl)
        }
    }

    @Test
    fun allGoogleNewsConfigsAreSeparatedByPress() {
        assertEquals(
            listOf(
                NewsPress.YONHAP,
                NewsPress.MBC,
                NewsPress.SBS,
                NewsPress.KBS,
                NewsPress.KBS,
                NewsPress.YTN,
                NewsPress.YTN,
                NewsPress.YTN
            ),
            GoogleNewsRssConfig.defaultConfigs.map { config -> config.press }
        )
        assertEquals(
            GoogleNewsRssConfig.defaultConfigs.size,
            GoogleNewsRssConfig.defaultConfigs.map { config -> config.url }.distinct().size
        )
        assertFalse(GoogleNewsRssConfig.defaultConfigs.any { config -> config.press == NewsPress.ALL })
    }
}

private object PressDomainForTest {
    fun hostFrom(url: String): String? =
        java.net.URI(url).host
}

package com.example.fakenews.data.remote

import com.example.fakenews.data.model.NewsPress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RssFeedConfigTest {
    @Test
    fun defaultFeedsUseYtnAndDoNotContainRemovedPresses() {
        assertTrue(RssFeedConfig.defaultFeeds.any { config -> config.press == NewsPress.YTN })
        removedPressNames().forEach { removedName ->
            assertFalse(RssFeedConfig.defaultFeeds.any { config -> config.press.name == removedName })
        }
    }

    @Test
    fun ytnUsesGoogleNewsRssFirst() {
        val ytnFeeds = RssFeedConfig.defaultFeeds.filter { config -> config.press == NewsPress.YTN }

        assertTrue(ytnFeeds.isNotEmpty())
        assertEquals(RssOriginalUrlMode.GOOGLE_NEWS, ytnFeeds.first().originalUrlMode)
        assertEquals(
            "https://news.google.com/rss/search?q=site:ytn.co.kr/_ln/&hl=ko&gl=KR&ceid=KR:ko",
            ytnFeeds.first().url
        )
        assertTrue(ytnFeeds.first().enrichFromOriginalUrl)
    }

    @Test
    fun kbsUsesOfficialRssBeforeGoogleNewsFallback() {
        val kbsFeeds = RssFeedConfig.defaultFeeds.filter { config -> config.press == NewsPress.KBS }

        assertEquals(3, kbsFeeds.size)
        assertEquals(RssFeedConfig.KBS_OFFICIAL_RSS_URL, kbsFeeds[0].url)
        assertEquals(RssOriginalUrlMode.DIRECT, kbsFeeds[0].originalUrlMode)
        assertFalse(kbsFeeds[0].enrichFromOriginalUrl)
        assertEquals(
            "https://news.google.com/rss/search?q=site:news.kbs.co.kr%20KBS&hl=ko&gl=KR&ceid=KR:ko",
            kbsFeeds[1].url
        )
        assertEquals(RssOriginalUrlMode.GOOGLE_NEWS, kbsFeeds[1].originalUrlMode)
        assertTrue(kbsFeeds[1].enrichFromOriginalUrl)
        assertEquals(
            "https://news.google.com/rss/search?q=site:news.kbs.co.kr/news/mobile/view/view.do&hl=ko&gl=KR&ceid=KR:ko",
            kbsFeeds[2].url
        )
        assertEquals(RssOriginalUrlMode.GOOGLE_NEWS, kbsFeeds[2].originalUrlMode)
        assertTrue(kbsFeeds[2].enrichFromOriginalUrl)
    }

    @Test
    fun googleNewsFeedsEnableOriginalDetailEnrichment() {
        listOf(NewsPress.MBC, NewsPress.YTN).forEach { press ->
            val firstFeed = RssFeedConfig.defaultFeeds.first { config -> config.press == press }

            assertEquals(RssOriginalUrlMode.GOOGLE_NEWS, firstFeed.originalUrlMode)
            assertTrue(firstFeed.enrichFromOriginalUrl)
        }

        val kbsGoogleFallback = RssFeedConfig.defaultFeeds
            .filter { config -> config.press == NewsPress.KBS }
            .first { config -> config.originalUrlMode == RssOriginalUrlMode.GOOGLE_NEWS }

        assertTrue(kbsGoogleFallback.enrichFromOriginalUrl)
    }

    @Test
    fun allPressIsNeverFetchTarget() {
        assertFalse(RssFeedConfig.defaultFeeds.any { config -> config.press == NewsPress.ALL })
    }

    @Test
    fun sbsHasHeadlineFallbackAfterNewsflashFeed() {
        val sbsFeeds = RssFeedConfig.defaultFeeds.filter { config -> config.press == NewsPress.SBS }

        assertTrue(sbsFeeds.first().url.contains("newsflashRssFeed"))
        assertTrue(sbsFeeds.any { config -> config.url.contains("headlineRssFeed") })
    }

    @Test
    fun sbsFeedsEnableOriginalDetailEnrichmentForCardPreviewBody() {
        val sbsFeeds = RssFeedConfig.defaultFeeds.filter { config -> config.press == NewsPress.SBS }

        assertTrue(sbsFeeds.isNotEmpty())
        assertTrue(sbsFeeds.all { config -> config.enrichFromOriginalUrl })
    }

    @Test
    fun officialAndGoogleNewsFeedsAreSeparatedForCollectionLogging() {
        assertTrue(RssFeedConfig.officialFeeds.any { config -> config.press == NewsPress.YONHAP })
        assertTrue(RssFeedConfig.officialFeeds.any { config -> config.press == NewsPress.SBS })
        assertTrue(RssFeedConfig.officialFeeds.any { config -> config.url == RssFeedConfig.KBS_OFFICIAL_RSS_URL })
        assertFalse(RssFeedConfig.officialFeeds.any { config -> config.originalUrlMode == RssOriginalUrlMode.GOOGLE_NEWS })

        assertTrue(RssFeedConfig.googleNewsFeeds.any { config -> config.press == NewsPress.MBC })
        assertTrue(RssFeedConfig.googleNewsFeeds.any { config -> config.press == NewsPress.KBS })
        assertTrue(RssFeedConfig.googleNewsFeeds.any { config -> config.press == NewsPress.YTN })
        assertTrue(RssFeedConfig.googleNewsFeeds.all { config -> config.originalUrlMode == RssOriginalUrlMode.GOOGLE_NEWS })
    }

    @Test
    fun keywordSearchGoogleNewsFeedsCoverAllArticlePresses() {
        val searchPresses = RssFeedConfig.keywordSearchGoogleNewsFeeds
            .map { config -> config.press }
            .toSet()

        assertEquals(NewsPress.articlePresses().toSet(), searchPresses)
        assertTrue(RssFeedConfig.keywordSearchGoogleNewsFeeds.all { config ->
            config.originalUrlMode == RssOriginalUrlMode.GOOGLE_NEWS
        })
    }

    private fun removedPressNames(): List<String> =
        listOf("KBS" + "_WORLD", "J" + "TBC")
}

package com.example.fakenews.data.remote

import com.example.fakenews.data.model.NewsPress
import com.example.fakenews.data.model.NewsSourceType
import com.example.fakenews.data.remote.GoogleNewsRssConfig.Companion.rssConfigFor
import com.example.fakenews.data.remote.GoogleNewsRssConfig.Companion.rssConfigsFor

data class RssFeedConfig(
    val press: NewsPress,
    val url: String,
    val originalUrlMode: RssOriginalUrlMode = RssOriginalUrlMode.DIRECT,
    val label: String = NewsSourceType.RSS.displayName,
    val enrichFromOriginalUrl: Boolean = false
) {
    companion object {
        const val KBS_OFFICIAL_RSS_URL = "http://news.kbs.co.kr/rss/kbsrss_news.php?site=news"

        val officialFeeds: List<RssFeedConfig> = buildList {
            add(RssFeedConfig(NewsPress.YONHAP, "https://www.yna.co.kr/rss/news.xml"))
            add(
                RssFeedConfig(
                    NewsPress.SBS,
                    "https://news.sbs.co.kr/news/newsflashRssFeed.do?plink=RSSREADER",
                    enrichFromOriginalUrl = true
                )
            )
            add(
                RssFeedConfig(
                    NewsPress.SBS,
                    "https://news.sbs.co.kr/news/headlineRssFeed.do?plink=RSSREADER",
                    enrichFromOriginalUrl = true
                )
            )
            add(RssFeedConfig(NewsPress.KBS, KBS_OFFICIAL_RSS_URL))
        }

        val googleNewsFeeds: List<RssFeedConfig> = buildList {
            add(requireNotNull(rssConfigFor(NewsPress.MBC)))
            addAll(rssConfigsFor(NewsPress.KBS))
            addAll(rssConfigsFor(NewsPress.YTN))
        }

        val keywordSearchGoogleNewsFeeds: List<RssFeedConfig> =
            NewsPress.articlePresses()
                .flatMap { press -> rssConfigsFor(press) }

        val defaultFeeds: List<RssFeedConfig> = officialFeeds + googleNewsFeeds
    }
}

enum class RssOriginalUrlMode {
    DIRECT,
    GOOGLE_NEWS
}

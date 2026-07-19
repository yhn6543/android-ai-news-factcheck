package com.example.fakenews.data.remote

import com.example.fakenews.data.model.NewsPress

data class GoogleNewsRssConfig(
    val press: NewsPress,
    val url: String
) {
    fun toRssFeedConfig(): RssFeedConfig =
        RssFeedConfig(
            press = press,
            url = url,
            originalUrlMode = RssOriginalUrlMode.GOOGLE_NEWS,
            label = GOOGLE_NEWS_RSS_LABEL,
            enrichFromOriginalUrl = true
        )

    companion object {
        const val GOOGLE_NEWS_RSS_LABEL = "Google News RSS"

        val defaultConfigs: List<GoogleNewsRssConfig> = listOf(
            GoogleNewsRssConfig(
                press = NewsPress.YONHAP,
                url = googleNewsFeed("yna.co.kr")
            ),
            GoogleNewsRssConfig(
                press = NewsPress.MBC,
                url = "https://news.google.com/rss/search?q=site:imnews.imbc.com%20MBC&hl=ko&gl=KR&ceid=KR:ko"
            ),
            GoogleNewsRssConfig(
                press = NewsPress.SBS,
                url = googleNewsFeed("news.sbs.co.kr")
            ),
            GoogleNewsRssConfig(
                press = NewsPress.KBS,
                url = "https://news.google.com/rss/search?q=site:news.kbs.co.kr%20KBS&hl=ko&gl=KR&ceid=KR:ko"
            ),
            GoogleNewsRssConfig(
                press = NewsPress.KBS,
                url = googleNewsFeed("news.kbs.co.kr/news/mobile/view/view.do")
            ),
            GoogleNewsRssConfig(
                press = NewsPress.YTN,
                url = googleNewsFeed("ytn.co.kr/_ln/")
            ),
            GoogleNewsRssConfig(
                press = NewsPress.YTN,
                url = googleNewsFeed("www.ytn.co.kr/_ln/")
            ),
            GoogleNewsRssConfig(
                press = NewsPress.YTN,
                url = googleNewsFeed("m.ytn.co.kr/_ln/")
            )
        )

        fun googleNewsFeed(sitePattern: String): String =
            "https://news.google.com/rss/search?q=site:$sitePattern&hl=ko&gl=KR&ceid=KR:ko"

        fun rssConfigsFor(press: NewsPress): List<RssFeedConfig> =
            defaultConfigs
                .filter { config -> config.press == press }
                .map { config -> config.toRssFeedConfig() }

        fun rssConfigFor(press: NewsPress): RssFeedConfig? =
            rssConfigsFor(press).firstOrNull()
    }
}

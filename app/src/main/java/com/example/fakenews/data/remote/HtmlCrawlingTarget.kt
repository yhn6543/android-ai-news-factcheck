package com.example.fakenews.data.remote

import com.example.fakenews.data.model.NewsPress

data class HtmlCrawlingTarget(
    val press: NewsPress,
    val url: String
) {
    companion object {
        val defaultTargets: List<HtmlCrawlingTarget> = listOf(
            HtmlCrawlingTarget(NewsPress.YONHAP, "https://www.yna.co.kr/news"),
            HtmlCrawlingTarget(NewsPress.MBC, "https://imnews.imbc.com/news/2026/society/"),
            HtmlCrawlingTarget(NewsPress.SBS, "https://news.sbs.co.kr/news/newsflash.do"),
            HtmlCrawlingTarget(NewsPress.KBS, "https://news.kbs.co.kr/news/pc/main/main.html"),
            HtmlCrawlingTarget(NewsPress.YTN, "https://m.ytn.co.kr/newslist/news_list.php?s_mcd=0101")
        )
    }
}

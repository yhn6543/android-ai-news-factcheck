package com.example.fakenews.data.remote

import com.example.fakenews.data.model.NewsPress
import com.example.fakenews.util.DateParser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlCrawlingNewsDataSourceTest {
    @Test
    fun relativeLinkIsConvertedAndOutsideDomainLinkIsExcluded() = runTest {
        val listUrl = "https://news.sbs.co.kr/news/newsflash.do"
        val articleUrl = "https://news.sbs.co.kr/news/endPage.do?news_id=1"
        val dataSource = HtmlCrawlingNewsDataSource(
            targets = listOf(HtmlCrawlingTarget(NewsPress.SBS, listUrl)),
            enabled = true,
            fetcher = FakeHtmlPageFetcher(
                mapOf(
                    listUrl to """
                    <html><body>
                      <article><a href="/news/endPage.do?news_id=1">Allowed SBS article title</a></article>
                      <article><a href="https://imnews.imbc.com/news/1">MBC text but wrong domain</a></article>
                    </body></html>
                    """.trimIndent()
                )
            )
        )

        val result = dataSource.fetch(NewsPress.SBS, emptyList())

        assertEquals(1, result.articles.size)
        val article = result.articles.single()
        assertEquals(articleUrl, article.originalUrl)
        assertEquals(NewsPress.SBS, article.press)
    }

    @Test
    fun titleOrBodyPressNameDoesNotOverrideConfiguredPress() = runTest {
        val listUrl = "https://www.ytn.co.kr/news/list.php"
        val articleUrl = "https://www.ytn.co.kr/_ln/0101_202606110001"
        val dataSource = HtmlCrawlingNewsDataSource(
            targets = listOf(HtmlCrawlingTarget(NewsPress.YTN, listUrl)),
            enabled = true,
            fetcher = FakeHtmlPageFetcher(
                mapOf(
                    listUrl to """
                    <html><body>
                      <article><a href="$articleUrl">KBS mentioned title on YTN domain</a></article>
                    </body></html>
                    """.trimIndent()
                )
            )
        )

        val result = dataSource.fetch(NewsPress.YTN, emptyList())

        assertTrue(result.articles.isNotEmpty())
        assertTrue(result.articles.all { article -> article.press == NewsPress.YTN })
    }

    @Test
    fun ytnTargetIncludesOnlyYtnDomain() = runTest {
        val listUrl = "https://www.ytn.co.kr/news/list.php"
        val ytnArticleUrl = "https://www.ytn.co.kr/_ln/0101_202606110001"
        val dataSource = HtmlCrawlingNewsDataSource(
            targets = listOf(HtmlCrawlingTarget(NewsPress.YTN, listUrl)),
            enabled = true,
            fetcher = FakeHtmlPageFetcher(
                mapOf(
                    listUrl to """
                    <html><body>
                      <article><a href="/_ln/0101_202606110001">YTN allowed article</a></article>
                      <article><a href="https://news.kbs.co.kr/news/pc/view/view.do?ncd=1">Regular KBS should be excluded</a></article>
                    </body></html>
                    """.trimIndent()
                )
            )
        )

        val result = dataSource.fetch(NewsPress.YTN, emptyList())

        assertEquals(1, result.articles.size)
        assertEquals(ytnArticleUrl, result.articles.single().originalUrl)
        assertEquals(NewsPress.YTN, result.articles.single().press)
    }

    @Test
    fun ytnMobileListArticleIsCanonicalizedAndEnriched() = runTest {
        val listUrl = "https://m.ytn.co.kr/newslist/news_list.php?s_mcd=0101"
        val mobileUrl = "https://m.ytn.co.kr/news_view.php?key=202606121945145572&s_mcd=0101"
        val canonicalUrl = "https://www.ytn.co.kr/_ln/0101_202606121945145572"
        val dataSource = HtmlCrawlingNewsDataSource(
            targets = listOf(HtmlCrawlingTarget(NewsPress.YTN, listUrl)),
            enabled = true,
            fetcher = FakeHtmlPageFetcher(
                mapOf(
                    listUrl to """
                    <html><body>
                      <a href="${mobileUrl.replace("&", "&amp;")}">
                        <div class="title">YTN mobile canonical article title</div>
                      </a>
                    </body></html>
                    """.trimIndent(),
                    canonicalUrl to """
                    <html>
                      <head>
                        <meta property="og:title" content="YTN mobile canonical article title">
                        <meta property="article:published_time" content="2026-06-12T19:43:00+09:00">
                      </head>
                      <body>
                        <div class="paragraph flexible_font">
                          First YTN mobile article paragraph with enough context for validation.<br><br>
                          Second YTN mobile article paragraph keeps the full detail body available.
                        </div>
                      </body>
                    </html>
                    """.trimIndent()
                )
            )
        )

        val result = dataSource.fetch(NewsPress.YTN, emptyList())

        assertTrue(result.success)
        val article = result.articles.single()
        assertEquals(canonicalUrl, article.originalUrl)
        assertEquals(DateParser.parseEpochMillis("2026-06-12T19:43:00+09:00"), article.publishedAt)
        assertEquals("html:meta[property=article:published_time]", article.publishedAtSource)
        assertEquals(
            listOf(
                "First YTN mobile article paragraph with enough context for validation.",
                "Second YTN mobile article paragraph keeps the full detail body available."
            ),
            article.bodyParagraphs
        )
    }

    @Test
    fun mbcHtmlFallbackCandidateIsEnrichedFromOriginalArticle() = runTest {
        val listUrl = "https://imnews.imbc.com/news/2026/society/"
        val articleUrl = "https://imnews.imbc.com/news/2026/society/article/6829534_36918.html"
        val dataSource = HtmlCrawlingNewsDataSource(
            targets = listOf(HtmlCrawlingTarget(NewsPress.MBC, listUrl)),
            enabled = true,
            fetcher = FakeHtmlPageFetcher(
                mapOf(
                    listUrl to """
                    <html><body>
                      <article><a href="$articleUrl">중국 알리페이에 고객정보 넘긴 카카오페이</a></article>
                    </body></html>
                    """.trimIndent(),
                    articleUrl to """
                    <html>
                      <head>
                        <meta property="og:title" content="중국 알리페이에 고객정보 넘긴 카카오페이">
                        <meta property="og:image" content="https://image.imnews.imbc.com/news/2026/society/article/photo.jpg">
                        <meta name="nextweb:createDate" id="createDate" content="2026-06-11 19:10">
                      </head>
                      <body>
                        <div class="news_txt">
                          <div class="news_img"><p class="caption">카카오페이 [자료사진]</p></div>
                          고객 동의 없이 개인정보를 넘긴 카카오페이에 대한 법원 판단입니다.<br><br>
                          서울행정법원은 과징금 처분이 적법하다고 판단했습니다.
                        </div>
                      </body>
                    </html>
                    """.trimIndent()
                )
            )
        )

        val result = dataSource.fetch(NewsPress.MBC, emptyList())

        assertTrue(result.success)
        val article = result.articles.single()
        assertEquals(DateParser.parseEpochMillis("2026-06-11 19:10"), article.publishedAt)
        assertEquals("html:meta[name=nextweb:createDate]", article.publishedAtSource)
        assertEquals(
            listOf(
                "고객 동의 없이 개인정보를 넘긴 카카오페이에 대한 법원 판단입니다.",
                "서울행정법원은 과징금 처분이 적법하다고 판단했습니다."
            ),
            article.bodyParagraphs
        )
        assertEquals("https://image.imnews.imbc.com/news/2026/society/article/photo.jpg", article.imageUrl)
    }

    @Test
    fun mainDefaultHtmlFallbackKeepsFiveCandidateLimit() = runTest {
        val listUrl = "https://news.sbs.co.kr/news/newsflash.do"
        val dataSource = HtmlCrawlingNewsDataSource(
            targets = listOf(HtmlCrawlingTarget(NewsPress.SBS, listUrl)),
            enabled = true,
            fetcher = FakeHtmlPageFetcher(mapOf(listUrl to sbsListHtml(count = 8)))
        )

        val result = dataSource.fetch(NewsPress.SBS, emptyList())

        assertEquals(NewsFetchConfig.MAX_ARTICLES_PER_PRESS, result.articles.size)
    }

    @Test
    fun searchHtmlFallbackCanInspectMoreThanFiveCandidates() = runTest {
        val listUrl = "https://news.sbs.co.kr/news/newsflash.do"
        val dataSource = HtmlCrawlingNewsDataSource(
            targets = listOf(HtmlCrawlingTarget(NewsPress.SBS, listUrl)),
            enabled = true,
            fetcher = FakeHtmlPageFetcher(mapOf(listUrl to sbsListHtml(count = 8)))
        )

        val result = dataSource.fetch(NewsPress.SBS, listOf("rare-keyword"))

        assertEquals(8, result.articles.size)
    }

    private fun sbsListHtml(count: Int): String =
        """
        <html><body>
          ${(1..count).joinToString("\n") { index ->
            """<article><a href="https://news.sbs.co.kr/news/endPage.do?news_id=N100000$index">SBS fallback article $index</a></article>"""
        }}
        </body></html>
        """.trimIndent()

    private class FakeHtmlPageFetcher(
        private val pages: Map<String, String>
    ) : HtmlPageFetcher {
        override suspend fun fetch(url: String): String =
            pages[url] ?: error("missing fake page: $url")
    }
}

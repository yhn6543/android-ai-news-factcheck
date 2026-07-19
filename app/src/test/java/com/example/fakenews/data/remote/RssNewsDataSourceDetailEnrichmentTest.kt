package com.example.fakenews.data.remote

import com.example.fakenews.data.model.NewsPress
import com.example.fakenews.util.DateParser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RssNewsDataSourceDetailEnrichmentTest {
    @Test
    fun mbcGoogleNewsRssArticleIsEnrichedWithOriginalTimeBodyAndImage() = runTest {
        val originalUrl = "https://imnews.imbc.com/news/2026/society/article/6829534_36918.html"
        val title = "중국 알리페이에 고객정보 넘긴 카카오페이"
        val dataSource = RssNewsDataSource(
            feedConfigs = listOf(googleNewsFeed(NewsPress.MBC, enrich = true)),
            fetcher = StaticRssFeedFetcher(
                rss(
                    title = "$title - MBC 뉴스",
                    originalUrl = originalUrl,
                    pubDate = "not a date",
                    description = "<![CDATA[<a href=\"$originalUrl\">$title</a>&nbsp;&nbsp;<font>MBC 뉴스</font>]]>"
                )
            ),
            detailDataSource = ArticleDetailRemoteDataSource(
                fetcher = FakeHtmlPageFetcher(
                    mapOf(
                        originalUrl to """
                        <html>
                          <head>
                            <meta property="og:title" content="$title">
                            <meta name="description" content="MBC 원본 상세 요약입니다.">
                            <meta property="og:image" content="https://image.imnews.imbc.com/news/2026/society/article/photo.jpg">
                            <meta property="article:published_time" content="2026-06-11T19:10:55+09:00">
                          </head>
                          <body>
                            <div class="news_txt" itemprop="articleBody">
                              <div class="news_img">
                                <img src="//image.imnews.imbc.com/news/2026/society/article/photo.jpg">
                                <p class="caption">카카오페이 [자료사진]</p>
                              </div>
                              고객 동의 없이 개인정보를 넘긴 카카오페이에 대한 법원 판단이 나왔습니다.<br><br>
                              서울행정법원은 개인정보보호위원회의 과징금 처분이 적법하다고 판단했습니다.
                            </div>
                          </body>
                        </html>
                        """.trimIndent()
                    )
                )
            )
        )

        val result = dataSource.fetch(NewsPress.MBC, emptyList())

        assertTrue(result.success)
        val article = result.articles.single()
        assertEquals(title, article.title)
        assertEquals(DateParser.parseEpochMillis("2026-06-11T19:10:55+09:00"), article.publishedAt)
        assertEquals("html:meta[property=article:published_time]", article.publishedAtSource)
        assertEquals(
            listOf(
                "고객 동의 없이 개인정보를 넘긴 카카오페이에 대한 법원 판단이 나왔습니다.",
                "서울행정법원은 개인정보보호위원회의 과징금 처분이 적법하다고 판단했습니다."
            ),
            article.bodyParagraphs
        )
        assertFalse(article.bodyParagraphs.joinToString(" ").contains("자료사진"))
        assertEquals("https://image.imnews.imbc.com/news/2026/society/article/photo.jpg", article.imageUrl)
    }

    @Test
    fun kbsGoogleNewsViewUrlIsCollectedAsRealArticleAfterDetailEnrichment() = runTest {
        val originalUrl = "https://news.kbs.co.kr/news/pc/view/view.do?ncd=1234567"
        val dataSource = RssNewsDataSource(
            feedConfigs = listOf(googleNewsFeed(NewsPress.KBS, enrich = true)),
            fetcher = StaticRssFeedFetcher(
                rss(
                    title = "KBS view 기사 테스트 - KBS 뉴스",
                    originalUrl = originalUrl,
                    description = "<![CDATA[<a href=\"$originalUrl\">KBS view 기사 테스트</a>]]>"
                )
            ),
            detailDataSource = ArticleDetailRemoteDataSource(
                fetcher = FakeHtmlPageFetcher(
                    mapOf(
                        originalUrl to """
                        <html>
                          <head>
                            <meta property="og:title" content="KBS view 기사 테스트">
                            <meta property="article:published_time" content="2026-06-11T18:30:00+09:00">
                          </head>
                          <body>
                            <div id="cont_newstext">
                              <p>KBS view URL에 포함된 실제 기사 본문 첫 문단입니다.</p>
                              <p>수집 단계에서 상세 본문을 보강해 카드 미리보기에 사용할 수 있습니다.</p>
                            </div>
                          </body>
                        </html>
                        """.trimIndent()
                    )
                )
            )
        )

        val result = dataSource.fetch(NewsPress.KBS, emptyList())

        assertTrue(result.success)
        val article = result.articles.single()
        assertEquals(originalUrl, article.originalUrl)
        assertEquals("KBS view 기사 테스트", article.title)
        assertTrue(article.bodyParagraphs.first().contains("실제 기사 본문"))
        assertEquals(DateParser.parseEpochMillis("2026-06-11T18:30:00+09:00"), article.publishedAt)
    }

    @Test
    fun sbsRssArticleIsEnrichedSoCardPreviewUsesOriginalBodyInsteadOfAppCta() = runTest {
        val originalUrl = "https://news.sbs.co.kr/news/endPage.do?news_id=N1008605287"
        val dataSource = RssNewsDataSource(
            feedConfigs = listOf(
                RssFeedConfig(
                    press = NewsPress.SBS,
                    url = "https://example.com/sbs-rss",
                    enrichFromOriginalUrl = true
                )
            ),
            fetcher = StaticRssFeedFetcher(
                rss(
                    title = "SBS 카드 본문 테스트",
                    originalUrl = originalUrl,
                    description = """
                        <![CDATA[
                        <p>SBS뉴스 앱 다운로드</p>
                        <p>뉴스에 지식을 담다 - 스브스프리미엄 앱 다운로드</p>
                        <p>영상 시청</p>
                        ]]>
                    """.trimIndent()
                )
            ),
            detailDataSource = ArticleDetailRemoteDataSource(
                fetcher = FakeHtmlPageFetcher(
                    mapOf(
                        originalUrl to """
                        <html>
                          <head>
                            <meta property="og:title" content="SBS 카드 본문 테스트">
                            <meta property="article:published_time" content="2026-06-11T18:30:00+09:00">
                          </head>
                          <body>
                            <div class="article_cont_area">
                              <p>첫 번째 SBS 실제 기사 본문입니다. 카드 미리보기에 표시되어야 합니다.</p>
                              <p>두 번째 SBS 실제 기사 본문입니다. 앱 다운로드 문구가 아닙니다.</p>
                            </div>
                          </body>
                        </html>
                        """.trimIndent()
                    )
                )
            )
        )

        val result = dataSource.fetch(NewsPress.SBS, emptyList())

        assertTrue(result.success)
        val article = result.articles.single()
        val previewText = article.bodyParagraphs.joinToString(" ")
        assertTrue(previewText.contains("첫 번째 SBS 실제 기사 본문"))
        assertFalse(previewText.contains("SBS뉴스 앱 다운로드"))
        assertFalse(previewText.contains("스브스프리미엄 앱 다운로드"))
        assertFalse(previewText.contains("영상 시청"))
    }

    @Test
    fun detailEnrichmentFailureKeepsRssArticle() = runTest {
        val originalUrl = "https://imnews.imbc.com/news/2026/society/article/6829534_36918.html"
        val dataSource = RssNewsDataSource(
            feedConfigs = listOf(googleNewsFeed(NewsPress.MBC, enrich = true)),
            fetcher = StaticRssFeedFetcher(
                rss(
                    title = "MBC RSS 제목",
                    originalUrl = originalUrl,
                    description = "<![CDATA[MBC RSS 본문 요약입니다. 상세 보강 실패 시에도 유지됩니다.]]>"
                )
            ),
            detailDataSource = ArticleDetailRemoteDataSource(
                fetcher = FakeHtmlPageFetcher(emptyMap())
            )
        )

        val result = dataSource.fetch(NewsPress.MBC, emptyList())

        assertTrue(result.success)
        assertEquals("MBC RSS 제목", result.articles.single().title)
        assertEquals(originalUrl, result.articles.single().originalUrl)
    }

    @Test
    fun keywordGoogleNewsRssIsEnrichedEvenWhenDefaultDetailEnrichmentDisabled() = runTest {
        val googleUrl = "https://news.google.com/rss/articles/kbs-keyword"
        val originalUrl = "https://news.kbs.co.kr/news/view.do?ncd=777"
        val dataSource = RssNewsDataSource(
            feedConfigs = listOf(
                RssFeedConfig(
                    press = NewsPress.KBS,
                    url = "https://example.com/google-news-rss",
                    originalUrlMode = RssOriginalUrlMode.GOOGLE_NEWS,
                    label = "Google News RSS",
                    enrichFromOriginalUrl = true
                )
            ),
            fetcher = StaticRssFeedFetcher(
                googleNewsRss(
                    googleUrl = googleUrl,
                    sourceUrl = "https://news.kbs.co.kr",
                    title = "KBS keyword article"
                )
            ),
            originalUrlResolver = OriginalUrlResolver { url ->
                if (url == googleUrl) originalUrl else null
            },
            detailDataSource = ArticleDetailRemoteDataSource(
                fetcher = FakeHtmlPageFetcher(
                    mapOf(
                        originalUrl to """
                        <html>
                          <head>
                            <meta property="og:title" content="KBS keyword article">
                            <meta property="og:image" content="https://news.kbs.co.kr/images/kbs-keyword.jpg">
                          </head>
                          <body>
                            <div id="cont_newstext">
                              <p>KBS keyword detail body first paragraph for the search card preview.</p>
                              <p>KBS keyword detail body second paragraph with enough text.</p>
                            </div>
                          </body>
                        </html>
                        """.trimIndent()
                    )
                )
            ),
            enableDetailEnrichment = false
        )

        val result = dataSource.fetch(NewsPress.KBS, listOf("keyword"))

        assertTrue(result.success)
        val article = result.articles.single()
        assertEquals(originalUrl, article.originalUrl)
        assertEquals("https://news.kbs.co.kr/images/kbs-keyword.jpg", article.imageUrl)
        assertTrue(article.bodyParagraphs.first().contains("search card preview"))
    }

    private fun googleNewsFeed(
        press: NewsPress,
        enrich: Boolean
    ): RssFeedConfig =
        RssFeedConfig(
            press = press,
            url = "https://example.com/rss",
            label = "Google News RSS",
            enrichFromOriginalUrl = enrich
        )

    private fun rss(
        title: String,
        originalUrl: String,
        pubDate: String = "Thu, 11 Jun 2026 09:00:00 GMT",
        description: String = "<![CDATA[RSS 요약입니다.]]>"
    ): String =
        """
        <rss version="2.0">
          <channel>
            <item>
              <title><![CDATA[$title]]></title>
              <link>$originalUrl</link>
              <pubDate>$pubDate</pubDate>
              <description>$description</description>
            </item>
          </channel>
        </rss>
        """.trimIndent()

    private fun googleNewsRss(
        googleUrl: String,
        sourceUrl: String,
        title: String
    ): String =
        """
        <rss version="2.0">
          <channel>
            <item>
              <title><![CDATA[$title]]></title>
              <link>$googleUrl</link>
              <source url="$sourceUrl">KBS</source>
              <pubDate>Thu, 11 Jun 2026 09:00:00 GMT</pubDate>
              <description><![CDATA[RSS summary for $title]]></description>
            </item>
          </channel>
        </rss>
        """.trimIndent()

    private class StaticRssFeedFetcher(
        private val xml: String
    ) : RssFeedFetcher {
        override suspend fun fetch(url: String): String = xml
    }

    private class FakeHtmlPageFetcher(
        private val pages: Map<String, String>
    ) : HtmlPageFetcher {
        override suspend fun fetch(url: String): String =
            pages[url] ?: error("missing fake page: $url")
    }
}

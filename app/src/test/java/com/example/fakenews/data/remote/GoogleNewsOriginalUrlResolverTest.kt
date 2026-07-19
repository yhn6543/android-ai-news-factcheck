package com.example.fakenews.data.remote

import com.example.fakenews.data.model.NewsPress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleNewsOriginalUrlResolverTest {
    @Test
    fun extractsOriginalUrlFromGoogleNewsQueryParameter() {
        val originalUrl = "https://imnews.imbc.com/news/2026/society/article.html"
        val googleUrl = "https://news.google.com/rss/articles/sample?url=${originalUrl.urlEncoded()}&hl=ko"

        assertEquals(originalUrl, GoogleNewsOriginalUrlExtractor.extractedUrlFromQuery(googleUrl))
    }

    @Test
    fun parserUsesExtractedOriginalUrlAndRejectsGoogleRedirectUrl() {
        val originalUrl = "https://www.ytn.co.kr/_ln/0101_202606110001"
        val googleUrl = "https://news.google.com/rss/articles/sample?url=${originalUrl.urlEncoded()}&hl=ko"

        val articles = RssParser.parse(
            xml = googleRss(link = googleUrl),
            press = NewsPress.YTN,
            originalUrlMode = RssOriginalUrlMode.GOOGLE_NEWS
        )

        assertEquals(1, articles.size)
        assertEquals(originalUrl, articles.single().originalUrl)
        assertEquals(1_735_689_600_000L, articles.single().publishedAt)
        assertEquals("rss:pubDate", articles.single().publishedAtSource)
    }

    @Test
    fun parserResolvesGoogleRedirectWhenQueryDoesNotContainOriginalUrl() {
        val googleUrl = "https://news.google.com/rss/articles/redirect-only"
        val originalUrl = "https://m.ytn.co.kr/_ln/0101_202606110001"

        val articles = RssParser.parse(
            xml = googleRss(link = googleUrl),
            press = NewsPress.YTN,
            originalUrlMode = RssOriginalUrlMode.GOOGLE_NEWS,
            originalUrlResolver = OriginalUrlResolver { url ->
                if (url == googleUrl) originalUrl else null
            }
        )

        assertEquals(1, articles.size)
        assertEquals(originalUrl, articles.single().originalUrl)
    }

    @Test
    fun resolvedUrlOutsideArticleUrlPolicyIsExcluded() {
        val googleUrl = "https://news.google.com/rss/articles/ytn-live"
        val articles = RssParser.parse(
            xml = googleRss(link = googleUrl),
            press = NewsPress.YTN,
            originalUrlMode = RssOriginalUrlMode.GOOGLE_NEWS,
            originalUrlResolver = OriginalUrlResolver {
                "https://www.ytn.co.kr/live.php"
            }
        )

        assertTrue(articles.isEmpty())
    }

    @Test
    fun unresolvedGoogleRedirectIsExcluded() {
        val articles = RssParser.parse(
            xml = googleRss(link = "https://news.google.com/rss/articles/unresolved"),
            press = NewsPress.MBC,
            originalUrlMode = RssOriginalUrlMode.GOOGLE_NEWS,
            originalUrlResolver = OriginalUrlResolver { null }
        )

        assertTrue(articles.isEmpty())
    }

    @Test
    fun resolvedUrlOutsideAllowedDomainIsExcluded() {
        val googleUrl = "https://news.google.com/rss/articles/wrong-domain"
        val articles = RssParser.parse(
            xml = googleRss(link = googleUrl),
            press = NewsPress.MBC,
            originalUrlMode = RssOriginalUrlMode.GOOGLE_NEWS,
            originalUrlResolver = OriginalUrlResolver {
                "https://www.ytn.co.kr/_ln/0101_202606110001"
            }
        )

        assertTrue(articles.isEmpty())
    }

    @Test
    fun googleNewsSourceUrlForDifferentPressIsSkippedBeforeResolving() {
        var resolverCallCount = 0
        val articles = RssParser.parse(
            xml = googleRss(
                link = "https://news.google.com/rss/articles/source-mismatch",
                sourceUrl = "https://www.ytn.co.kr"
            ),
            press = NewsPress.MBC,
            originalUrlMode = RssOriginalUrlMode.GOOGLE_NEWS,
            originalUrlResolver = OriginalUrlResolver {
                resolverCallCount += 1
                "https://imnews.imbc.com/news/2026/society/article.html"
            }
        )

        assertTrue(articles.isEmpty())
        assertEquals(0, resolverCallCount)
    }

    private fun googleRss(
        link: String,
        sourceUrl: String? = null
    ): String =
        """
        <rss version="2.0">
          <channel>
            <item>
              <title>Google News 원본 URL 테스트 기사</title>
              <link>$link</link>
              <pubDate>Wed, 01 Jan 2025 00:00:00 GMT</pubDate>
              <description><![CDATA[짧은 요약입니다.]]></description>
              ${sourceUrl?.let { "<source url=\"$it\">Source</source>" }.orEmpty()}
            </item>
          </channel>
        </rss>
        """.trimIndent()

    private fun String.urlEncoded(): String =
        java.net.URLEncoder.encode(this, Charsets.UTF_8.name())
}

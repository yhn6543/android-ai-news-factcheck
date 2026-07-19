package com.example.fakenews.util

import com.example.fakenews.data.model.NewsPress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class UrlNormalizerTest {
    @Test
    fun removesTrackingParameters() {
        val first = UrlNormalizer.normalize("https://example.com/news/1?utm_source=a&utm_medium=b&id=42")
        val second = UrlNormalizer.normalize("https://example.com/news/1?id=42&utm_campaign=c")

        assertEquals(second, first)
    }

    @Test
    fun treatsHttpAndHttpsAsSameUrl() {
        val http = UrlNormalizer.normalize("http://example.com/news/1")
        val https = UrlNormalizer.normalize("https://example.com/news/1")

        assertEquals(https, http)
    }

    @Test
    fun removesTrailingSlashAndFragment() {
        val first = UrlNormalizer.normalize("https://example.com/news/1/#section")
        val second = UrlNormalizer.normalize("https://example.com/news/1")

        assertEquals(second, first)
    }

    @Test
    fun lowercasesHost() {
        val result = UrlNormalizer.normalize("https://EXAMPLE.com/News/1")

        assertEquals("example.com/News/1", result)
    }

    @Test
    fun decodesHtmlEntityQuerySeparatorsBeforeNormalizing() {
        val raw = "https://news.sbs.co.kr/news/endPage.do?news_id=N1008615495&amp;plink=RSSLINK&amp;cooper=RSSREADER"

        val decoded = UrlNormalizer.decodeHtmlEntities(raw)
        val normalized = UrlNormalizer.normalize(raw)

        assertEquals(
            "https://news.sbs.co.kr/news/endPage.do?news_id=N1008615495&plink=RSSLINK&cooper=RSSREADER",
            decoded
        )
        assertFalse(normalized.contains("amp;plink"))
        assertFalse(normalized.contains("amp;cooper"))
        assertEquals(
            UrlNormalizer.normalize(decoded),
            normalized
        )
    }

    @Test
    fun articleIdentityUsesDecodedUrlForStableHash() {
        val raw = "https://news.sbs.co.kr/news/endPage.do?news_id=N1008615495&amp;plink=RSSLINK"
        val decoded = "https://news.sbs.co.kr/news/endPage.do?news_id=N1008615495&plink=RSSLINK"

        assertEquals(
            ArticleIdentity.idFor(NewsPress.SBS, decoded, "SBS title"),
            ArticleIdentity.idFor(NewsPress.SBS, raw, "SBS title")
        )
    }

    @Test
    fun kbsPcMobileAndCommonViewUrlsShareNcdIdentity() {
        val pc = UrlNormalizer.normalize("https://news.kbs.co.kr/news/pc/view/view.do?ncd=1234567&utm_source=x")
        val mobile = UrlNormalizer.normalize("https://news.kbs.co.kr/news/mobile/view/view.do?ncd=1234567")
        val common = UrlNormalizer.normalize("https://www.news.kbs.co.kr/common/news_view.html?foo=bar&ncd=1234567")
        val short = UrlNormalizer.normalize("https://kbs.co.kr/news/view.do?ncd=1234567")

        assertEquals("news.kbs.co.kr/news/view.do?ncd=1234567", pc)
        assertEquals(pc, mobile)
        assertEquals(pc, common)
        assertEquals(pc, short)
    }
}

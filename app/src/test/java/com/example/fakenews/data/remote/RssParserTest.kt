package com.example.fakenews.data.remote

import com.example.fakenews.data.model.NewsPress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RssParserTest {
    @Test
    fun parsesTitleLinkPubDateAndDescriptionFromSampleRss() {
        val articles = RssParser.parse(sampleRss(), NewsPress.YONHAP)

        val article = articles.first()
        assertEquals("테스트 뉴스 제목", article.title)
        assertEquals("https://www.yna.co.kr/view/AKR20250101000100001", article.originalUrl)
        assertEquals("테스트 뉴스 요약입니다.", article.summary)
        assertEquals(1_735_689_600_000L, article.publishedAt)
        assertEquals("rss:pubDate", article.publishedAtSource)
    }

    @Test
    fun parsesEnclosureImageUrl() {
        val article = RssParser.parse(
            sampleRss(
                originalUrl = "https://imnews.imbc.com/news/2025/society/article.html",
                enclosureUrl = "https://example.com/image.jpg"
            ),
            NewsPress.MBC
        ).first()

        assertEquals("https://example.com/image.jpg", article.imageUrl)
    }

    @Test
    fun extractsImageUrlFromDescriptionImgSrc() {
        val article = RssParser.parse(
            sampleRss(
                originalUrl = "https://news.sbs.co.kr/news/endPage.do?news_id=N1000000001",
                description = "<![CDATA[<img src=\"https://example.com/desc.png\" /> 설명 본문]]>"
            ),
            NewsPress.SBS
        ).first()

        assertEquals("https://example.com/desc.png", article.imageUrl)
    }

    @Test
    fun invalidPubDateReturnsNull() {
        val article = RssParser.parse(
            sampleRss(
                originalUrl = "https://news.kbs.co.kr/news/pc/view/view.do?ncd=1",
                pubDate = "not a date"
            ),
            NewsPress.KBS
        ).first()

        assertNull(article.publishedAt)
    }

    @Test
    fun parsesMediaContentImageUrl() {
        val article = RssParser.parse(
            sampleRss(
                originalUrl = "https://www.ytn.co.kr/_ln/0101_202606110001",
                mediaContentUrl = "https://example.com/media.webp"
            ),
            NewsPress.YTN
        ).first()

        assertEquals("https://example.com/media.webp", article.imageUrl)
    }

    @Test
    fun parsesMediaThumbnailImageUrl() {
        val article = RssParser.parse(
            sampleRss(
                originalUrl = "https://imnews.imbc.com/news/2025/society/article.html",
                mediaThumbnailUrl = "https://example.com/thumb.jpg"
            ),
            NewsPress.MBC
        ).first()

        assertEquals("https://example.com/thumb.jpg", article.imageUrl)
    }

    @Test
    fun parsesYonhapImageFromLaterMediaContentWithQueryString() {
        val article = RssParser.parse(
            """
            <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/">
              <channel>
                <item>
                  <title><![CDATA[연합뉴스 이미지 테스트 기사]]></title>
                  <link>https://www.yna.co.kr/view/AKR20260611094500052</link>
                  <pubDate>Thu, 11 Jun 2026 13:02:17 +0900</pubDate>
                  <description><![CDATA[(서울=연합뉴스) 본문 요약입니다.]]></description>
                  <media:content url="https://video.example.com/news.mp4" type="video/mp4" />
                  <media:content url="https://img.yna.co.kr/photo/yna/YH/2026/06/11/PYH2026061103790001300_P2.jpg?type=w647" />
                </item>
              </channel>
            </rss>
            """.trimIndent(),
            NewsPress.YONHAP
        ).first()

        assertEquals(
            "https://img.yna.co.kr/photo/yna/YH/2026/06/11/PYH2026061103790001300_P2.jpg?type=w647",
            article.imageUrl
        )
    }

    @Test
    fun normalizesProtocolRelativeDescriptionImageUrl() {
        val article = RssParser.parse(
            sampleRss(
                description = "<![CDATA[<img data-src=\"//img.yna.co.kr/photo/sample.jpg\" /> 설명 본문]]>"
            ),
            NewsPress.YONHAP
        ).first()

        assertEquals("https://img.yna.co.kr/photo/sample.jpg", article.imageUrl)
    }

    @Test
    fun parsesAtomEntryWithHrefLinkAndUpdatedDate() {
        val articles = RssParser.parse(sampleAtom(), NewsPress.YTN)

        val article = articles.first()
        assertEquals("Atom 테스트 제목", article.title)
        assertEquals("https://www.ytn.co.kr/_ln/0101_202606110001", article.originalUrl)
        assertEquals("Atom 요약입니다.", article.summary)
        assertEquals(1_735_689_600_000L, article.publishedAt)
        assertEquals("atom:updated", article.publishedAtSource)
    }

    @Test
    fun createsKeywordsFromTitleAndSummary() {
        val article = RssParser.parse(
            sampleRss(originalUrl = "https://www.ytn.co.kr/_ln/0101_202606110001"),
            NewsPress.YTN
        ).first()

        assertNotNull(article.keywords)
    }

    private fun sampleRss(
        pubDate: String = "Wed, 01 Jan 2025 00:00:00 GMT",
        originalUrl: String = "https://www.yna.co.kr/view/AKR20250101000100001",
        description: String = "<![CDATA[테스트 뉴스 요약입니다.]]>",
        enclosureUrl: String? = null,
        mediaContentUrl: String? = null,
        mediaThumbnailUrl: String? = null
    ): String =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/">
          <channel>
            <item>
              <title><![CDATA[테스트 뉴스 제목]]></title>
              <link>$originalUrl</link>
              <pubDate>$pubDate</pubDate>
              <description>$description</description>
              ${enclosureUrl?.let { "<enclosure url=\"$it\" type=\"image/jpeg\" />" }.orEmpty()}
              ${mediaContentUrl?.let { "<media:content url=\"$it\" medium=\"image\" />" }.orEmpty()}
              ${mediaThumbnailUrl?.let { "<media:thumbnail url=\"$it\" />" }.orEmpty()}
            </item>
          </channel>
        </rss>
        """.trimIndent()

    private fun sampleAtom(): String =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom">
          <entry>
            <title>Atom 테스트 제목</title>
            <link href="https://www.ytn.co.kr/_ln/0101_202606110001" />
            <updated>2025-01-01T00:00:00Z</updated>
            <summary>Atom 요약입니다.</summary>
            <content>Atom 본문입니다.</content>
          </entry>
        </feed>
        """.trimIndent()
}

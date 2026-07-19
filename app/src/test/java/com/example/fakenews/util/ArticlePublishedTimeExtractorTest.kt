package com.example.fakenews.util

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArticlePublishedTimeExtractorTest {
    @Test
    fun extractsArticlePublishedTimeMetaFirst() {
        val document = Jsoup.parse(
            """
            <html>
              <head>
                <meta property="article:published_time" content="2025-06-10T12:34:56+09:00">
              </head>
              <body><time datetime="2025-06-11T12:34:56+09:00"></time></body>
            </html>
            """.trimIndent()
        )

        val result = ArticlePublishedTimeExtractor.extract(document)

        assertEquals(DateParser.parseEpochMillis("2025-06-10T12:34:56+09:00"), result?.epochMillis)
        assertEquals("html:meta[property=article:published_time]", result?.source)
    }

    @Test
    fun extractsTimeDatetime() {
        val document = Jsoup.parse("""<article><time datetime="2025.06.10 12:34">등록</time></article>""")

        val result = ArticlePublishedTimeExtractor.extract(document)

        assertEquals(DateParser.parseEpochMillis("2025.06.10 12:34"), result?.epochMillis)
        assertEquals("html:time[datetime]", result?.source)
    }

    @Test
    fun extractsKoreanInputTimeFromText() {
        val document = Jsoup.parse("""<article>기사입력 2025-06-10 오후 3:04 본문입니다.</article>""")

        val result = ArticlePublishedTimeExtractor.extract(document)

        assertEquals(DateParser.parseEpochMillis("2025-06-10 오후 3:04"), result?.epochMillis)
        assertEquals("html:text", result?.source)
    }

    @Test
    fun extractsMbcNextwebCreateDateMetaWhenPublishedTimeMetaIsMissing() {
        val document = Jsoup.parse(
            """
            <html>
              <head>
                <meta name="nextweb:createDate" id="createDate" content="2026-06-11 19:10">
              </head>
              <body><article>MBC 기사 본문입니다.</article></body>
            </html>
            """.trimIndent()
        )

        val result = ArticlePublishedTimeExtractor.extract(document)

        assertEquals(DateParser.parseEpochMillis("2026-06-11 19:10"), result?.epochMillis)
        assertEquals("html:meta[name=nextweb:createDate]", result?.source)
    }

    @Test
    fun returnsNullWhenNoPublishedTimeExists() {
        val document = Jsoup.parse("""<article>본문만 있습니다.</article>""")

        assertNull(ArticlePublishedTimeExtractor.extract(document))
    }
}

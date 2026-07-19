package com.example.fakenews.data.remote

import com.example.fakenews.data.model.NewsPress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RssParserBodyTest {
    @Test
    fun contentEncodedIsPreferredAndParagraphsArePreserved() {
        val article = RssParser.parse(
            xml = sampleRss(
                link = "https://www.yna.co.kr/view/AKR202601010001",
                description = "<![CDATA[Short summary only]]>",
                contentEncoded = "<![CDATA[<p>First body paragraph.</p><p>Second body paragraph.</p>]]>"
            ),
            press = NewsPress.YONHAP
        ).first()

        assertEquals("Short summary only", article.summary)
        assertEquals(listOf("First body paragraph.", "Second body paragraph."), article.bodyParagraphs)
        assertTrue(article.content.contains("First body paragraph."))
    }

    @Test
    fun descriptionHtmlIsCleanedToTextParagraphs() {
        val article = RssParser.parse(
            xml = sampleRss(
                link = "https://imnews.imbc.com/news/2025/society/article.html",
                description = "<![CDATA[<div>First line<br/>Second line</div>]]>",
                contentEncoded = ""
            ),
            press = NewsPress.MBC
        ).first()

        assertEquals(listOf("First line", "Second line"), article.bodyParagraphs)
    }

    private fun sampleRss(
        link: String,
        description: String,
        contentEncoded: String
    ): String =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/">
          <channel>
            <item>
              <title>Sample article title</title>
              <link>$link</link>
              <pubDate>Wed, 01 Jan 2025 00:00:00 GMT</pubDate>
              <description>$description</description>
              <content:encoded>$contentEncoded</content:encoded>
            </item>
          </channel>
        </rss>
        """.trimIndent()
}

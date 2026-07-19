package com.example.fakenews.data.remote

import com.example.fakenews.data.model.NewsPress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RssParserVideoTest {
    @Test
    fun extractsVideoUrlFromVideoEnclosure() {
        val article = RssParser.parse(
            xml = sampleRss(
                link = "https://www.yna.co.kr/view/AKR20250101000100001",
                extraItemTag = """<enclosure url="https://example.com/video.mp4" type="video/mp4" />"""
            ),
            press = NewsPress.YONHAP
        ).first()

        assertEquals("https://example.com/video.mp4", article.videoUrl)
    }

    @Test
    fun extractsVideoUrlFromMediaContentMp4() {
        val article = RssParser.parse(
            xml = sampleRss(
                link = "https://imnews.imbc.com/news/2025/society/article.html",
                extraItemTag = """<media:content url="https://example.com/video.mp4" medium="video" />"""
            ),
            press = NewsPress.MBC
        ).first()

        assertEquals("https://example.com/video.mp4", article.videoUrl)
    }

    @Test
    fun mediaContentJpgIsImageAndNotVideo() {
        val article = RssParser.parse(
            xml = sampleRss(
                link = "https://news.sbs.co.kr/news/endPage.do?news_id=N1000000001",
                extraItemTag = """<media:content url="https://example.com/image.jpg" medium="image" />"""
            ),
            press = NewsPress.SBS
        ).first()

        assertEquals("https://example.com/image.jpg", article.imageUrl)
        assertNull(article.videoUrl)
    }

    private fun sampleRss(
        link: String,
        extraItemTag: String
    ): String =
        """
        <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/">
          <channel>
            <item>
              <title>영상 테스트 기사</title>
              <link>$link</link>
              <pubDate>Wed, 01 Jan 2025 00:00:00 GMT</pubDate>
              <description>영상 테스트 요약</description>
              $extraItemTag
            </item>
          </channel>
        </rss>
        """.trimIndent()
}

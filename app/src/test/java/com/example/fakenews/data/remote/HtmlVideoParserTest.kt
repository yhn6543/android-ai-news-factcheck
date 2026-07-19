package com.example.fakenews.data.remote

import com.example.fakenews.data.model.NewsPress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HtmlVideoParserTest {
    @Test
    fun extractsVideoUrlFromVideoSrc() {
        val article = HtmlCrawlerParser.parse(
            html = sampleHtml("""<video src="/movie.mp4"></video>"""),
            baseUrl = "https://example.com",
            press = NewsPress.MBC
        ).first()

        assertEquals("https://example.com/movie.mp4", article.videoUrl)
    }

    @Test
    fun extractsVideoUrlFromSourceSrc() {
        val article = HtmlCrawlerParser.parse(
            html = sampleHtml("""<video><source src="/movie.webm"></video>"""),
            baseUrl = "https://example.com",
            press = NewsPress.SBS
        ).first()

        assertEquals("https://example.com/movie.webm", article.videoUrl)
    }

    @Test
    fun extractsVideoUrlFromOgVideo() {
        val article = HtmlCrawlerParser.parse(
            html = """
            <html>
              <head><meta property="og:video" content="https://example.com/og-video.mp4"></head>
              <body><article><a href="/news/1">충분히 긴 영상 테스트 기사</a></article></body>
            </html>
            """.trimIndent(),
            baseUrl = "https://example.com",
            press = NewsPress.KBS
        ).first()

        assertEquals("https://example.com/og-video.mp4", article.videoUrl)
    }

    @Test
    fun youtubeIframeIsNotUsedAsVideoUrl() {
        val article = HtmlCrawlerParser.parse(
            html = sampleHtml("""<iframe src="https://www.youtube.com/watch?v=abc"></iframe>"""),
            baseUrl = "https://example.com",
            press = NewsPress.YTN
        ).first()

        assertNull(article.videoUrl)
    }

    private fun sampleHtml(extraMedia: String): String =
        """
        <html>
          <body>
            <article>
              <a href="/news/1">충분히 긴 영상 테스트 기사</a>
              $extraMedia
            </article>
          </body>
        </html>
        """.trimIndent()
}

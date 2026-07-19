package com.example.fakenews.util

import com.example.fakenews.data.model.NewsPress
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleContentExtractorTest {
    @Test
    fun extractsH1AsTitleAndArticleParagraphsInOrder() {
        val extracted = ArticleContentExtractor.extract(
            document = Jsoup.parse(
                """
                <html><body>
                  <article>
                    <h1>Article headline</h1>
                    <p>Article headline</p>
                    <p>First paragraph.</p>
                    <p>Second paragraph.</p>
                  </article>
                </body></html>
                """.trimIndent(),
                "https://news.sbs.co.kr/news/1"
            ),
            press = NewsPress.SBS
        )

        assertEquals("Article headline", extracted.title)
        assertEquals(listOf("First paragraph.", "Second paragraph."), extracted.paragraphs)
    }

    @Test
    fun brTagIsKeptAsLineBreakInsideParagraph() {
        val extracted = ArticleContentExtractor.extract(
            document = Jsoup.parse(
                """
                <html><body><article><p>First line<br>Second line</p></article></body></html>
                """.trimIndent(),
                "https://news.sbs.co.kr/news/1"
            ),
            press = NewsPress.SBS
        )

        assertTrue(extracted.paragraphs.single().contains("First line"))
        assertTrue(extracted.paragraphs.single().contains("Second line"))
    }

    @Test
    fun boilerplateAreasAreExcludedFromParagraphs() {
        val extracted = ArticleContentExtractor.extract(
            document = Jsoup.parse(
                """
                <html><body>
                  <article>
                    <p>Real article paragraph with enough meaningful text.</p>
                    <div class="share">공유하기</div>
                    <div class="recommend">추천뉴스</div>
                    <div class="notice">공지사항</div>
                  </article>
                </body></html>
                """.trimIndent(),
                "https://news.sbs.co.kr/news/1"
            ),
            press = NewsPress.SBS
        )

        val joined = extracted.paragraphs.joinToString(" ")
        assertFalse(joined.contains("공유하기"))
        assertFalse(joined.contains("추천뉴스"))
        assertFalse(joined.contains("공지사항"))
    }

    @Test
    fun ytnSelectorsExtractBodyAndRemoveBoilerplate() {
        val extracted = ArticleContentExtractor.extract(
            document = Jsoup.parse(
                """
                <html><body>
                  <div id="CmAdContent">
                    <h1>YTN 테스트 제목</h1>
                    <p>YTN 테스트 제목</p>
                    <p>YTN 본문 첫 문단입니다.</p>
                    <p>YTN 본문 두 번째 문단입니다.</p>
                    <p>공유하기</p>
                    <p>추천뉴스</p>
                    <p>Copyright YTN All rights reserved.</p>
                  </div>
                </body></html>
                """.trimIndent(),
                "https://www.ytn.co.kr/_ln/0101_202606110001"
            ),
            press = NewsPress.YTN
        )

        assertEquals(listOf("YTN 본문 첫 문단입니다.", "YTN 본문 두 번째 문단입니다."), extracted.paragraphs)
    }

    @Test
    fun longBodyIsNotTruncated() {
        val body = (1..20).joinToString("\n") { index ->
            "<p>Long article paragraph number $index with meaningful text.</p>"
        }
        val extracted = ArticleContentExtractor.extract(
            document = Jsoup.parse("<html><body><article>$body</article></body></html>"),
            press = NewsPress.SBS
        )

        assertEquals(20, extracted.paragraphs.size)
        assertTrue(extracted.paragraphs.last().contains("20"))
    }

    @Test
    fun ytnArticleSelectorExtractsBody() {
        val extracted = ArticleContentExtractor.extract(
            document = Jsoup.parse(
                """
                <html><body>
                  <article>
                    <h1>YTN article title</h1>
                    <p>YTN article title</p>
                    <p>YTN first paragraph.</p>
                    <p>YTN second paragraph.</p>
                  </article>
                </body></html>
                """.trimIndent(),
                "https://www.ytn.co.kr/_ln/0101_202606110001"
            ),
            press = NewsPress.YTN
        )

        assertEquals(listOf("YTN first paragraph.", "YTN second paragraph."), extracted.paragraphs)
    }

    @Test
    fun extractsYonhapBodyImageWhenMetaImageIsMissing() {
        val extracted = ArticleContentExtractor.extract(
            document = Jsoup.parse(
                """
                <html><body>
                  <div class="story-news">
                    <p>Yonhap first paragraph.</p>
                    <img data-src="/photos/2026/06/17/sample.jpg" alt="article image">
                  </div>
                </body></html>
                """.trimIndent(),
                "https://www.yna.co.kr/view/AKR202606170001"
            ),
            press = NewsPress.YONHAP
        )

        assertEquals("https://www.yna.co.kr/photos/2026/06/17/sample.jpg", extracted.imageUrl)
    }

    @Test
    fun extractsProtocolRelativeAndSrcsetImages() {
        val protocolRelative = ArticleContentExtractor.extract(
            document = Jsoup.parse(
                """
                <html><body><article><img src="//img.yna.co.kr/photo/sample.jpg"></article></body></html>
                """.trimIndent(),
                "https://www.yna.co.kr/view/AKR202606170001"
            ),
            press = NewsPress.YONHAP
        )
        val srcset = ArticleContentExtractor.extract(
            document = Jsoup.parse(
                """
                <html><body><article><source srcset="/photo/small.jpg 1x, /photo/large.jpg 2x"></article></body></html>
                """.trimIndent(),
                "https://www.yna.co.kr/view/AKR202606170001"
            ),
            press = NewsPress.YONHAP
        )

        assertEquals("https://img.yna.co.kr/photo/sample.jpg", protocolRelative.imageUrl)
        assertEquals("https://www.yna.co.kr/photo/small.jpg", srcset.imageUrl)
    }
}

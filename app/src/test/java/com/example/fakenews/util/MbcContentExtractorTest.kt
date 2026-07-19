package com.example.fakenews.util

import com.example.fakenews.data.model.NewsPress
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MbcContentExtractorTest {
    @Test
    fun newsTxtIsPreferredAndBodyFallbackIsNotMixed() {
        val extracted = ArticleContentExtractor.extract(
            document = Jsoup.parse(
                """
                <html><body>
                  <meta property="og:title" content="MBC 테스트 기사" />
                  <div class="news_txt">
                    <p>MBC 실제 본문 첫 문단입니다.</p>
                    <div>MBC 실제 본문 두 번째 문단입니다.</div>
                    <p>자료사진</p>
                    <p>좋아요</p>
                    <p>훈훈해요</p>
                    <p>슬퍼요</p>
                    <p>화나요</p>
                    <p>후속요청</p>
                  </div>
                  <article>
                    <p>이 문장은 fallback 영역이라 섞이면 안 됩니다.</p>
                  </article>
                </body></html>
                """.trimIndent(),
                "https://imnews.imbc.com/news/2026/society/article.html"
            ),
            press = NewsPress.MBC
        )

        assertEquals(
            listOf(
                "MBC 실제 본문 첫 문단입니다.",
                "MBC 실제 본문 두 번째 문단입니다."
            ),
            extracted.paragraphs
        )
        assertFalse(extracted.paragraphs.joinToString(" ").contains("fallback 영역"))
    }

    @Test
    fun newsTxtUiWordsAreRemovedAndParagraphBreaksArePreserved() {
        val extracted = ArticleContentExtractor.extract(
            document = Jsoup.parse(
                """
                <html><body>
                  <div class="news_txt">
                    첫 번째 줄 본문입니다.<br>
                    두 번째 줄 본문입니다.
                    <div>세 번째 문단 본문입니다.</div>
                    <div>공유하기</div>
                    <div>관련기사</div>
                    <div>댓글</div>
                    <div>공감</div>
                    <div>광고</div>
                  </div>
                </body></html>
                """.trimIndent(),
                "https://imnews.imbc.com/news/2026/society/article.html"
            ),
            press = NewsPress.MBC
        )

        val joined = extracted.paragraphs.joinToString(" ")
        assertTrue(joined.contains("첫 번째 줄 본문입니다."))
        assertTrue(joined.contains("두 번째 줄 본문입니다."))
        assertTrue(joined.contains("세 번째 문단 본문입니다."))
        assertFalse(joined.contains("공유하기"))
        assertFalse(joined.contains("관련기사"))
        assertFalse(joined.contains("댓글"))
        assertFalse(joined.contains("공감"))
        assertFalse(joined.contains("광고"))
    }

    @Test
    fun nonEmptyNewsTxtWithOnlyReactionUiDoesNotFallbackToPageBody() {
        val extracted = ArticleContentExtractor.extract(
            document = Jsoup.parse(
                """
                <html><body>
                  <div class="news_txt">
                    <p>좋아요</p>
                    <p>훈훈해요</p>
                    <p>슬퍼요</p>
                    <p>화나요</p>
                    <p>후속요청</p>
                  </div>
                  <article>
                    <p>fallback 본문이지만 news_txt가 비어 있지 않으므로 사용하면 안 됩니다.</p>
                  </article>
                </body></html>
                """.trimIndent(),
                "https://imnews.imbc.com/news/2026/society/article.html"
            ),
            press = NewsPress.MBC
        )

        assertEquals(emptyList<String>(), extracted.paragraphs)
    }

    @Test
    fun emptyNewsTxtCanFallbackToArticleSelector() {
        val extracted = ArticleContentExtractor.extract(
            document = Jsoup.parse(
                """
                <html><body>
                  <div class="news_txt"></div>
                  <article>
                    <p>비어 있는 news_txt 대신 사용할 수 있는 실제 기사 본문입니다.</p>
                  </article>
                </body></html>
                """.trimIndent(),
                "https://imnews.imbc.com/news/2026/society/article.html"
            ),
            press = NewsPress.MBC
        )

        assertEquals(
            listOf("비어 있는 news_txt 대신 사용할 수 있는 실제 기사 본문입니다."),
            extracted.paragraphs
        )
    }
}

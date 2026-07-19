package com.example.fakenews.ui.components

import com.example.fakenews.data.model.NewsArticle
import com.example.fakenews.data.model.NewsPress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleTextPreviewTest {
    @Test
    fun bodyParagraphsKeepParagraphSeparation() {
        val article = article(
            bodyParagraphs = listOf("first paragraph", "second paragraph")
        )

        assertEquals(
            listOf("first paragraph", "second paragraph"),
            ArticleTextFormatter.paragraphs(article)
        )
    }

    @Test
    fun contentNewLinesAreUsedWhenBodyParagraphsAreEmpty() {
        val article = article(
            content = "first content paragraph\nsecond content paragraph",
            summary = "summary"
        )

        assertEquals(
            listOf("first content paragraph", "second content paragraph"),
            ArticleTextFormatter.paragraphs(article)
        )
    }

    @Test
    fun summaryIsUsedWhenContentIsEmpty() {
        val article = article(
            content = "",
            summary = "summary fallback"
        )

        assertEquals(listOf("summary fallback"), ArticleTextFormatter.paragraphs(article))
    }

    @Test
    fun videoArticleStillCreatesBodyPreview() {
        val article = article(
            content = "video article body",
            videoUrl = "https://example.com/video.mp4"
        )

        assertTrue(ArticleTextFormatter.previewParagraphs(article, maxParagraphs = 2).isNotEmpty())
    }

    private fun article(
        content: String = "",
        summary: String = "",
        bodyParagraphs: List<String> = emptyList(),
        videoUrl: String? = null
    ): NewsArticle =
        NewsArticle(
            id = "article",
            title = "Article title",
            press = NewsPress.SBS,
            publishedAt = 1L,
            summary = summary,
            content = content,
            bodyParagraphs = bodyParagraphs,
            imageUrl = null,
            videoUrl = videoUrl,
            originalUrl = "https://news.sbs.co.kr/news/1",
            keywords = emptyList()
        )
}

package com.example.fakenews.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ArticleTitleCleanerTest {
    @Test
    fun keepsOnlyFirstMeaningfulTitleLineWhenBodyIsAttached() {
        val title = ArticleTitleCleaner.cleanTitle(
            """
            Clean article title
            This is the first body paragraph that should not be part of title.
            """.trimIndent()
        )

        assertEquals("Clean article title", title)
    }

    @Test
    fun removesSiteNameSuffix() {
        assertEquals(
            "기사 제목",
            ArticleTitleCleaner.cleanTitle("기사 제목 | MBC 뉴스")
        )
    }

    @Test
    fun removesFirstParagraphWhenItDuplicatesTitle() {
        val result = ArticleTitleCleaner.removeDuplicatedTitleFromParagraphs(
            title = "기사 제목",
            paragraphs = listOf("기사 제목", "첫 번째 본문", "두 번째 본문")
        )

        assertEquals(listOf("첫 번째 본문", "두 번째 본문"), result)
    }
}

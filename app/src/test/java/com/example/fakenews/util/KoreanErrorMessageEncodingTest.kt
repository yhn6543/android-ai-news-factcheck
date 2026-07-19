package com.example.fakenews.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class KoreanErrorMessageEncodingTest {
    @Test
    fun userFacingErrorMessagesAreReadableKorean() {
        val messages = listOf(
            UiText.ARTICLE_NOT_FOUND,
            UiText.ARTICLE_LOAD_FAILED,
            UiText.ARTICLE_BODY_LOAD_FAILED,
            UiText.NO_SELECTED_PRESS,
            UiText.NO_SEARCH_RESULTS,
            UiText.SOME_PRESS_ARTICLES_NOT_FOUND
        )

        assertEquals("기사를 찾을 수 없습니다.", UiText.ARTICLE_NOT_FOUND)
        messages.forEach { message ->
            assertFalse(message, message.contains("??"))
            assertFalse(message, message.contains("湲"))
            assertFalse(message, message.contains("疫"))
        }
    }
}

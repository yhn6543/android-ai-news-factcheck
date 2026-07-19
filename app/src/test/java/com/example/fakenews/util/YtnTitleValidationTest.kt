package com.example.fakenews.util

import com.example.fakenews.data.remote.YtnTitlePolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YtnTitleValidationTest {
    @Test
    fun normalYtnArticleTitleIsValid() {
        val result = YtnTitlePolicy.validateTitle("YTN field report confirms policy response")

        assertTrue(result.first)
        assertNull(result.second)
    }

    @Test
    fun blankTitleIsInvalid() {
        val result = YtnTitlePolicy.validateTitle("   ")

        assertFalse(result.first)
        assertTrue(result.second == "title_empty")
    }

    @Test
    fun pressNameOnlyTitleIsInvalid() {
        val result = YtnTitlePolicy.validateTitle("YTN")

        assertFalse(result.first)
        assertTrue(result.second == "title_not_article_like")
    }
}

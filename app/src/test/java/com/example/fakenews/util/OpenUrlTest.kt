package com.example.fakenews.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenUrlTest {
    @Test
    fun emptyOriginalUrlIsInvalidWithoutThrowing() {
        assertFalse(OpenUrl.isValidHttpUrl(""))
    }

    @Test
    fun malformedOriginalUrlIsInvalidWithoutThrowing() {
        assertFalse(OpenUrl.isValidHttpUrl("not a url"))
    }

    @Test
    fun httpAndHttpsUrlsAreValid() {
        assertTrue(OpenUrl.isValidHttpUrl("https://example.com/mock-news"))
        assertTrue(OpenUrl.isValidHttpUrl("http://example.com/mock-news"))
    }
}

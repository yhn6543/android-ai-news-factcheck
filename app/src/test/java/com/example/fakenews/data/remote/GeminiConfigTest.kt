package com.example.fakenews.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class GeminiConfigTest {
    @Test
    fun defaultModelUsesVerifiedStableGeminiFlashModel() {
        assertEquals("gemini-2.5-flash", GeminiConfig.DEFAULT_MODEL)
        assertEquals("gemini-2.5-flash-lite", GeminiConfig.FALLBACK_MODEL)
    }
}

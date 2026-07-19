package com.example.fakenews

import com.example.fakenews.util.ApiKeyProvider
import org.junit.Assert.assertNotNull
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun geminiApiKeyBuildConfigValueIsAccessible() {
        assertNotNull(BuildConfig.GEMINI_API_KEY)
        assertNotNull(ApiKeyProvider.geminiApiKey)
    }
}

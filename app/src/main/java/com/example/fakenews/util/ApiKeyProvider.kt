package com.example.fakenews.util

import com.example.fakenews.BuildConfig

object ApiKeyProvider {
    val geminiApiKey: String
        get() = BuildConfig.GEMINI_API_KEY

    fun hasGeminiApiKey(): Boolean = geminiApiKey.isNotBlank()
}

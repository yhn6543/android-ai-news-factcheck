package com.example.fakenews.data.remote

object GeminiConfig {
    const val BASE_URL = "https://generativelanguage.googleapis.com/"
    const val DEFAULT_MODEL = "gemini-2.5-flash"
    const val FALLBACK_MODEL = "gemini-2.5-flash-lite"
    val MODEL_FALLBACK_CHAIN: List<String> = listOf(DEFAULT_MODEL, FALLBACK_MODEL)
}

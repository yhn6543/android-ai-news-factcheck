package com.example.fakenews.util

import java.util.Locale

object KeywordExtractor {
    private val tokenRegex = Regex("[^\\p{L}\\p{N}]+")

    fun extract(
        text: String,
        limit: Int = 6
    ): List<String> =
        text
            .split(tokenRegex)
            .map { token -> token.trim() }
            .filter { token -> token.length >= 2 }
            .distinctBy { token -> token.lowercase(Locale.ROOT) }
            .take(limit)
}

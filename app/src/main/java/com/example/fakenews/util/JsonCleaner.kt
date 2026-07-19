package com.example.fakenews.util

object JsonCleaner {
    fun clean(rawText: String): String {
        val text = rawText.trim()
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val startIndex = text.indexOf('{')
        val endIndex = text.lastIndexOf('}')

        return if (startIndex >= 0 && endIndex >= startIndex) {
            text.substring(startIndex, endIndex + 1)
        } else {
            text
        }
    }
}

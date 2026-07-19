package com.example.fakenews.util

import java.util.Locale

object MediaUrlDetector {
    fun isPlayableVideoUrl(url: String?): Boolean {
        val normalizedUrl = url?.trim().orEmpty()
        if (normalizedUrl.isBlank()) return false

        val lower = normalizedUrl.lowercase(Locale.ROOT)
        if ("youtube.com" in lower || "youtu.be" in lower || "iframe" in lower) {
            return false
        }

        val path = lower.substringBefore("?").substringBefore("#")
        return path.endsWith(".mp4") ||
            path.endsWith(".m3u8") ||
            path.endsWith(".webm")
    }
}

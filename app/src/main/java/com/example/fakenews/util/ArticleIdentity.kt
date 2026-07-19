package com.example.fakenews.util

import com.example.fakenews.data.model.NewsPress
import java.util.Locale

object ArticleIdentity {
    fun idFor(
        press: NewsPress,
        originalUrl: String,
        fallbackTitle: String = ""
    ): String {
        val normalizedUrl = UrlNormalizer.normalize(originalUrl)
        val identitySource = normalizedUrl.ifBlank { TitleNormalizer.normalize(fallbackTitle) }
        val hash = identitySource.hashCode().toUInt().toString(16)
        return "article-${press.name.lowercase(Locale.ROOT)}-$hash"
    }
}

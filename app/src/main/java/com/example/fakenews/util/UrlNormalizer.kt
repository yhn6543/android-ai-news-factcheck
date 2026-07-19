package com.example.fakenews.util

import java.net.URI
import java.util.Locale
import org.jsoup.parser.Parser

object UrlNormalizer {
    private val trackingParameters = setOf(
        "utm_source",
        "utm_medium",
        "utm_campaign",
        "utm_term",
        "utm_content",
        "fbclid",
        "gclid"
    )

    fun normalize(rawUrl: String?): String {
        val trimmed = decodeHtmlEntities(rawUrl).trim()
        if (trimmed.isBlank()) return ""

        return runCatching {
            val uri = URI(trimmed)
            val host = uri.host?.lowercase(Locale.ROOT).orEmpty()
            if (host.isBlank()) return@runCatching fallbackNormalize(trimmed)

            normalizeKbsArticleUrl(host = host, rawQuery = uri.rawQuery.orEmpty())?.let { normalized ->
                return@runCatching normalized
            }

            val path = uri.rawPath
                ?.takeIf { path -> path != "/" }
                ?.trimEnd('/')
                .orEmpty()
            val query = uri.rawQuery
                ?.split("&")
                ?.mapNotNull { parameter -> parameter.takeIf { it.isNotBlank() } }
                ?.filterNot { parameter ->
                    parameter.substringBefore("=")
                        .lowercase(Locale.ROOT) in trackingParameters
                }
                ?.sorted()
                ?.joinToString("&")
                .orEmpty()

            buildString {
                append(host)
                if (uri.port != -1) {
                    append(":")
                    append(uri.port)
                }
                append(path)
                if (query.isNotBlank()) {
                    append("?")
                    append(query)
                }
            }
        }.getOrElse {
            fallbackNormalize(trimmed)
        }
    }

    fun decodeHtmlEntities(rawUrl: String?): String =
        Parser.unescapeEntities(rawUrl.orEmpty(), false)

    private fun fallbackNormalize(url: String): String =
        decodeHtmlEntities(url).trim()
            .substringBefore("#")
            .replace(Regex("^https?://", RegexOption.IGNORE_CASE), "")
            .trimEnd('/')
            .lowercase(Locale.ROOT)

    private fun normalizeKbsArticleUrl(
        host: String,
        rawQuery: String
    ): String? {
        if (host !in kbsHosts) return null
        val ncd = rawQuery
            .split("&")
            .firstNotNullOfOrNull { parameter ->
                val name = parameter.substringBefore("=").lowercase(Locale.ROOT)
                val value = parameter.substringAfter("=", missingDelimiterValue = "")
                value.takeIf { name == "ncd" && it.isNotBlank() }
            } ?: return null

        return "news.kbs.co.kr/news/view.do?ncd=$ncd"
    }

    private val kbsHosts = setOf(
        "news.kbs.co.kr",
        "www.news.kbs.co.kr",
        "kbs.co.kr",
        "www.kbs.co.kr"
    )
}

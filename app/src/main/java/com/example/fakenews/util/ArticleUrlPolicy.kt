package com.example.fakenews.util

import com.example.fakenews.data.model.NewsPress
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

object ArticleUrlPolicy {
    data class Inspection(
        val host: String,
        val path: String,
        val allowedDomainPass: Boolean,
        val articleUrlPolicyPass: Boolean,
        val excludeReason: String?
    )

    fun isValidArticleUrl(
        press: NewsPress,
        url: String
    ): Boolean =
        inspect(press, url).articleUrlPolicyPass

    fun inspect(
        press: NewsPress,
        url: String
    ): Inspection {
        val parts = UrlParts.from(url) ?: return false
            .toInspection(host = "", path = "", allowedDomainPass = false, reason = "invalid_url")
        val allowedDomainPass = PressDomainMatcher.isUrlAllowedForPress(url, press)
        if (!allowedDomainPass) {
            return false.toInspection(
                host = parts.host,
                path = parts.path,
                allowedDomainPass = false,
                reason = "domain_mismatch"
            )
        }

        return when (press) {
            NewsPress.KBS -> inspectKbsArticleUrl(parts)
            NewsPress.YTN -> inspectYtnArticleUrl(parts)
            else -> true.toInspection(host = parts.host, path = parts.path)
        }
    }

    fun isExcludedNonArticleUrl(
        press: NewsPress,
        url: String
    ): Boolean {
        val parts = UrlParts.from(url) ?: return false
        return when (press) {
            NewsPress.KBS -> parts.containsAny(kbsExcludedTokens)
            NewsPress.YTN -> parts.containsAny(ytnExcludedTokens)
            else -> false
        }
    }

    private fun inspectKbsArticleUrl(parts: UrlParts): Inspection {
        if (parts.host !in kbsArticleHosts) {
            return false.toInspection(parts.host, parts.path, reason = "kbs_host_not_news_kbs")
        }
        if (parts.containsAny(kbsExcludedTokens)) {
            return false.toInspection(parts.host, parts.path, reason = "excluded_non_article_url")
        }

        val hasViewPath = kbsViewPaths.any { viewPath -> parts.path.contains(viewPath) } ||
            (parts.path.contains("/m/news/") && parts.path.contains("view"))
        val hasArticleId = parts.hasQueryParameter("ncd")
        val isValid = hasViewPath && hasArticleId
        return isValid.toInspection(
            host = parts.host,
            path = parts.path,
            reason = if (isValid) null else "kbs_missing_view_article_pattern"
        )
    }

    private fun inspectYtnArticleUrl(parts: UrlParts): Inspection {
        if (parts.host !in ytnArticleHosts) {
            return false.toInspection(parts.host, parts.path, reason = "ytn_host_not_allowed")
        }
        if (parts.containsAny(ytnExcludedTokens)) {
            return false.toInspection(parts.host, parts.path, reason = "excluded_non_article_url")
        }
        return parts.path.contains("/_ln/").toInspection(
            host = parts.host,
            path = parts.path,
            reason = if (parts.path.contains("/_ln/")) null else "ytn_missing_ln_path"
        )
    }

    private fun Boolean.toInspection(
        host: String,
        path: String,
        allowedDomainPass: Boolean = true,
        reason: String? = null
    ): Inspection =
        Inspection(
            host = host,
            path = path,
            allowedDomainPass = allowedDomainPass,
            articleUrlPolicyPass = this,
            excludeReason = reason.takeUnless { this }
        )

    private data class UrlParts(
        val host: String,
        val path: String,
        val query: String,
        val full: String
    ) {
        fun containsAny(tokens: List<String>): Boolean =
            tokens.any { token -> full.contains(token) }

        fun hasQueryParameter(name: String): Boolean {
            val normalizedName = name.lowercase(Locale.ROOT)
            return query
                .split('&')
                .any { parameter ->
                    parameter.substringBefore("=")
                        .trim()
                        .lowercase(Locale.ROOT) == normalizedName &&
                        parameter.substringAfter("=", missingDelimiterValue = "").isNotBlank()
                }
        }

        companion object {
            fun from(rawUrl: String): UrlParts? {
                val candidate = rawUrl.trim()
                if (candidate.isBlank()) return null
                val withScheme = if (candidate.startsWith("http://") || candidate.startsWith("https://")) {
                    candidate
                } else {
                    "https://$candidate"
                }
                return runCatching {
                    val uri = URI(withScheme)
                    val host = uri.host.orEmpty().lowercase(Locale.ROOT)
                    val path = uri.rawPath.orEmpty().decodeUrlComponent().lowercase(Locale.ROOT)
                    val query = uri.rawQuery.orEmpty().decodeUrlComponent().lowercase(Locale.ROOT)
                    val full = "$host $path $query".lowercase(Locale.ROOT)
                    UrlParts(
                        host = host,
                        path = path,
                        query = query,
                        full = full
                    )
                }.getOrNull()
            }
        }
    }

    private fun String.decodeUrlComponent(): String =
        runCatching {
            URLDecoder.decode(this, StandardCharsets.UTF_8.name())
        }.getOrDefault(this)

    private val kbsArticleHosts = setOf(
        "news.kbs.co.kr",
        "www.news.kbs.co.kr",
        "kbs.co.kr",
        "www.kbs.co.kr"
    )

    private val kbsViewPaths = listOf(
        "/news/pc/view/view.do",
        "/news/mobile/view/view.do",
        "/news/view.do",
        "/common/news_view.html"
    )

    private val ytnArticleHosts = setOf(
        "ytn.co.kr",
        "www.ytn.co.kr",
        "m.ytn.co.kr"
    )

    private val kbsExcludedTokens = listOf(
        "live",
        "onair",
        "on-air",
        "schedule",
        "program",
        "replay",
        "vod",
        "shorts",
        "youtube",
        "\uD3B8\uC131\uD45C",
        "\uB2E4\uC2DC\uBCF4\uAE30",
        "\uC628\uC5D0\uC5B4",
        "\uC2E4\uC2DC\uAC04 \uBC29\uC1A1",
        "\uB77C\uC774\uBE0C \uBC29\uC1A1"
    )

    private val ytnExcludedTokens = listOf(
        "/live.php",
        "live",
        "program",
        "replay",
        "vod",
        "schedule",
        "search",
        "ranking",
        "popular"
    )
}

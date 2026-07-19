package com.example.fakenews.util

import com.example.fakenews.data.model.NewsPress
import java.net.URI
import java.util.Locale

object PressDomainMatcher {
    private val allowedDomainsByPress: Map<NewsPress, Set<String>> = mapOf(
        NewsPress.YONHAP to setOf(
            "yna.co.kr",
            "www.yna.co.kr",
            "yonhapnewstv.co.kr",
            "www.yonhapnewstv.co.kr"
        ),
        NewsPress.MBC to setOf(
            "imnews.imbc.com",
            "www.imbc.com",
            "mbc.co.kr",
            "www.mbc.co.kr"
        ),
        NewsPress.SBS to setOf(
            "news.sbs.co.kr",
            "www.sbs.co.kr",
            "sbs.co.kr"
        ),
        NewsPress.KBS to setOf(
            "news.kbs.co.kr",
            "www.news.kbs.co.kr",
            "www.kbs.co.kr",
            "kbs.co.kr"
        ),
        NewsPress.YTN to setOf(
            "ytn.co.kr",
            "www.ytn.co.kr",
            "m.ytn.co.kr"
        )
    )

    fun allowedDomains(press: NewsPress): Set<String> =
        allowedDomainsByPress[press].orEmpty()

    fun isUrlAllowedForPress(
        url: String,
        press: NewsPress
    ): Boolean {
        if (press == NewsPress.ALL) return false
        val host = normalizeHost(url) ?: return false
        if (press == NewsPress.KBS || press == NewsPress.YTN) {
            return host in allowedDomains(press)
        }
        return allowedDomains(press).any { allowedDomain ->
            host == allowedDomain || host.endsWith(".$allowedDomain")
        }
    }

    fun detectPressFromUrl(url: String): NewsPress? =
        detectionOrder.firstOrNull { press ->
            isUrlAllowedForPress(url, press)
        }

    fun normalizeHost(url: String): String? {
        val candidate = url.trim()
        if (candidate.isBlank()) return null

        val withScheme = if (candidate.startsWith("http://") || candidate.startsWith("https://")) {
            candidate
        } else {
            "https://$candidate"
        }

        return runCatching {
            URI(withScheme)
                .host
                ?.lowercase(Locale.ROOT)
                ?.takeIf { host -> host.isNotBlank() }
        }.getOrNull()
    }

    private val detectionOrder = listOf(
        NewsPress.YONHAP,
        NewsPress.MBC,
        NewsPress.SBS,
        NewsPress.KBS,
        NewsPress.YTN
    )
}

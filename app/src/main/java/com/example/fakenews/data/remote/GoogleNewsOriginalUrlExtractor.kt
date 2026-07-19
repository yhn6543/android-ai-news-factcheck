package com.example.fakenews.data.remote

import com.example.fakenews.util.UrlNormalizer
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import org.jsoup.Jsoup
import org.w3c.dom.Element

data class GoogleNewsOriginalUrlResolution(
    val resolvedUrl: String?,
    val resolveSuccess: Boolean,
    val resolveFailureReason: String?
)

object GoogleNewsOriginalUrlExtractor {
    fun resolveOriginalUrl(
        item: Element,
        rawUrl: String,
        resolver: OriginalUrlResolver?
    ): String? =
        resolveOriginalUrlWithReason(
            item = item,
            rawUrl = rawUrl,
            resolver = resolver
        ).resolvedUrl

    fun resolveOriginalUrlWithReason(
        item: Element,
        rawUrl: String,
        resolver: OriginalUrlResolver?
    ): GoogleNewsOriginalUrlResolution {
        directCandidates(item, rawUrl).firstOrNull { candidate ->
            !candidate.isGoogleNewsUrl()
        }?.let { resolvedUrl ->
            return GoogleNewsOriginalUrlResolution(
                resolvedUrl = resolvedUrl,
                resolveSuccess = true,
                resolveFailureReason = null
            )
        }

        val googleUrl = directCandidates(item, rawUrl)
            .firstOrNull { candidate -> candidate.isGoogleNewsUrl() }
            ?: return GoogleNewsOriginalUrlResolution(
            resolvedUrl = null,
            resolveSuccess = false,
            resolveFailureReason = ORIGINAL_URL_PARAM_NOT_FOUND
        )

        if (resolver == null) {
            return GoogleNewsOriginalUrlResolution(
                resolvedUrl = null,
                resolveSuccess = false,
                resolveFailureReason = REDIRECT_RESOLVER_MISSING
            )
        }

        val resolvedCandidate = runCatching { resolver.resolve(googleUrl) }
            .getOrElse {
                return GoogleNewsOriginalUrlResolution(
                    resolvedUrl = null,
                    resolveSuccess = false,
                    resolveFailureReason = REDIRECT_TIMEOUT
                )
            }
        val resolvedUrl = resolvedCandidate
            ?.takeIf { candidate -> !candidate.isGoogleNewsUrl() }

        return GoogleNewsOriginalUrlResolution(
            resolvedUrl = resolvedUrl,
            resolveSuccess = !resolvedUrl.isNullOrBlank(),
            resolveFailureReason = when {
                !resolvedUrl.isNullOrBlank() -> null
                resolvedCandidate.isNullOrBlank() -> FINAL_URL_EMPTY
                resolvedCandidate?.isGoogleNewsUrl() == true -> FINAL_URL_STILL_GOOGLE
                else -> URL_PARSE_FAILED
            }
        )
    }

    fun extractedUrlFromQuery(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val query = uri.rawQuery.orEmpty()
        return query.split('&')
            .asSequence()
            .mapNotNull { part ->
                val key = part.substringBefore('=')
                val value = part.substringAfter('=', missingDelimiterValue = "")
                if (key in queryUrlKeys && value.isNotBlank()) value.decodeUrl() else null
            }
            .firstOrNull { candidate -> candidate.startsWith("http://") || candidate.startsWith("https://") }
    }

    private fun directCandidates(
        item: Element,
        rawUrl: String
    ): List<String> =
        buildList {
            add(rawUrl)
            extractedUrlFromQuery(rawUrl)?.let(::add)
            item.textForChild("guid", "id")?.let(::add)
            item.textForChild("description", "summary")?.let { description ->
                addAll(hrefsFromHtml(description))
            }
            item.textForChild("content:encoded", "encoded", "content")?.let { content ->
                addAll(hrefsFromHtml(content))
            }
        }
            .map { candidate -> UrlNormalizer.decodeHtmlEntities(candidate).trim() }
            .filter { candidate -> candidate.startsWith("http://") || candidate.startsWith("https://") }
            .flatMap { candidate -> listOfNotNull(extractedUrlFromQuery(candidate), candidate) }
            .distinct()

    private fun hrefsFromHtml(html: String): List<String> =
        runCatching {
            Jsoup.parse(html).select("a[href]")
                .map { element -> UrlNormalizer.decodeHtmlEntities(element.attr("href")).trim() }
                .filter { href -> href.startsWith("http://") || href.startsWith("https://") }
        }.getOrDefault(emptyList())

    private fun Element.textForChild(vararg names: String): String? {
        val wantedNames = names.toSet()
        val children = childNodes
        for (index in 0 until children.length) {
            val child = children.item(index)
            val nodeName = child.nodeName.orEmpty()
            val localName = child.localName.orEmpty()
            val matches = nodeName in wantedNames ||
                localName in wantedNames ||
                nodeName.substringAfter(':') in wantedNames
            if (matches) return child.textContent.orEmpty().takeIf { it.isNotBlank() }
        }
        return null
    }

    private fun String.isGoogleNewsUrl(): Boolean =
        runCatching { URI(this).host.orEmpty().lowercase() }
            .getOrDefault("")
            .let { host -> host == "news.google.com" || host.endsWith(".news.google.com") }

    private fun String.decodeUrl(): String =
        runCatching {
            URLDecoder.decode(this, StandardCharsets.UTF_8.name())
        }.getOrDefault(this).let(UrlNormalizer::decodeHtmlEntities)

    private val queryUrlKeys = setOf("url", "u", "q")

    const val RSS_FETCH_TIMEOUT = "RSS_FETCH_TIMEOUT"
    const val RSS_PARSE_EMPTY = "RSS_PARSE_EMPTY"
    const val ORIGINAL_URL_PARAM_NOT_FOUND = "ORIGINAL_URL_PARAM_NOT_FOUND"
    const val REDIRECT_RESOLVER_MISSING = "REDIRECT_RESOLVER_MISSING"
    const val REDIRECT_TIMEOUT = "REDIRECT_TIMEOUT"
    const val FINAL_URL_STILL_GOOGLE = "FINAL_URL_STILL_GOOGLE"
    const val FINAL_URL_EMPTY = "FINAL_URL_EMPTY"
    const val DOMAIN_MISMATCH = "DOMAIN_MISMATCH"
    const val URL_PARSE_FAILED = "URL_PARSE_FAILED"
}

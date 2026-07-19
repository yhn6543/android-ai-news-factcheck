package com.example.fakenews.data.remote

import com.example.fakenews.util.UrlNormalizer
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup

fun interface OriginalUrlResolver {
    fun resolve(url: String): String?
}

class HttpRedirectOriginalUrlResolver(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(NewsFetchConfig.REDIRECT_RESOLVE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(NewsFetchConfig.REDIRECT_RESOLVE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(NewsFetchConfig.REDIRECT_RESOLVE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) : OriginalUrlResolver {
    override fun resolve(url: String): String? {
        if (url.isGoogleNewsArticleUrl()) {
            return runCatching { resolveGoogleNewsArticleUrl(url) }.getOrNull()
        }
        return resolveByRedirect(url)
    }

    private fun resolveByRedirect(url: String): String? =
        runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", NewsFetchConfig.USER_AGENT)
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                val finalUrl = response.request.url.toString()
                if (finalUrl != url) {
                    finalUrl
                } else {
                    response.body?.string()
                        ?.let { html -> html.extractHtmlUrlCandidate(baseUrl = finalUrl) }
                }
            }
        }.getOrNull()

    private fun resolveGoogleNewsArticleUrl(url: String): String? {
        val articleId = url.googleNewsArticleId() ?: return null
        val articlePageHtml = client.newCall(
            Request.Builder()
                .url(url)
                .header("User-Agent", NewsFetchConfig.USER_AGENT)
                .header("Accept", HTML_ACCEPT)
                .header("Accept-Language", ACCEPT_LANGUAGE)
                .get()
                .build()
        ).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body?.string().orEmpty()
        }

        articlePageHtml.extractHtmlUrlCandidate(baseUrl = url)
            ?.takeIf { candidate -> !candidate.isGoogleNewsUrl() }
            ?.let { candidate -> return candidate }

        val id = articlePageHtml.htmlAttribute("data-n-a-id") ?: articleId
        val signature = articlePageHtml.htmlAttribute("data-n-a-sg") ?: return null
        val timestamp = articlePageHtml.htmlAttribute("data-n-a-ts")
            ?.takeIf { value -> value.all(Char::isDigit) }
            ?: return null
        val body = googleNewsDecodeBody(
            articleId = id,
            timestamp = timestamp,
            signature = signature
        )

        val decodeResponse = client.newCall(
            Request.Builder()
                .url(GOOGLE_NEWS_BATCH_EXECUTE_URL)
                .header("User-Agent", NewsFetchConfig.USER_AGENT)
                .header("Accept", "*/*")
                .header("Accept-Language", ACCEPT_LANGUAGE)
                .header("Origin", GOOGLE_NEWS_ORIGIN)
                .header("Referer", url)
                .header("X-Same-Domain", "1")
                .post(body.toRequestBody(FORM_URLENCODED))
                .build()
        ).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body?.string().orEmpty()
        }

        return decodeResponse.extractGoogleNewsDecodedUrl()
    }

    private fun String.extractHtmlUrlCandidate(baseUrl: String): String? =
        runCatching {
            val document = Jsoup.parse(this, baseUrl)
            val refreshUrl = document.selectFirst("meta[http-equiv=refresh], meta[http-equiv=Refresh]")
                ?.attr("content")
                ?.substringAfter("url=", missingDelimiterValue = "")
                ?.trim()
                ?.takeIf { candidate -> candidate.isNotBlank() }
            listOfNotNull(
                refreshUrl,
                document.selectFirst("link[rel=canonical]")?.absUrl("href"),
                document.selectFirst("meta[property=og:url]")?.absUrl("content")
            )
                .asSequence()
                .plus(
                    document.select("a[href]").asSequence()
                        .map { element -> element.absUrl("href") }
                )
                .map { candidate -> UrlNormalizer.decodeHtmlEntities(candidate).trim() }
                .firstOrNull { candidate ->
                    (candidate.startsWith("http://") || candidate.startsWith("https://")) &&
                        !candidate.isGoogleNewsUrl()
                }
        }.getOrNull()

    private fun googleNewsDecodeBody(
        articleId: String,
        timestamp: String,
        signature: String
    ): String {
        val innerRequest =
            "[\"garturlreq\",[[\"ko\",\"KR\",[\"FINANCE_TOP_INDICES\",\"WEB_TEST_1_0_0\"]," +
                "null,null,1,1,\"KR:ko\",null,180,null,null,null,null,null,0,null,null,[$timestamp,0]]," +
                "\"ko\",\"KR\",1,[2,3,4,8],1,0,\"655000234\",0,0,null,0]," +
                "${articleId.toJsonString()},$timestamp,${signature.toJsonString()}]"
        val outerRequest = "[[[\"Fbv4je\",${innerRequest.toJsonString()},null,\"generic\"]]]"
        return "f.req=${outerRequest.encodeFormValue()}"
    }

    private fun String.extractGoogleNewsDecodedUrl(): String? =
        GOOGLE_NEWS_DECODED_URL_REGEXES
            .asSequence()
            .mapNotNull { regex -> regex.find(this) }
            .mapNotNull { matchResult -> matchResult.groupValues.getOrNull(1) }
            .map { value -> value.decodeJsonStringFragment() }
            .map(UrlNormalizer::decodeHtmlEntities)
            .firstOrNull { decodedUrl -> decodedUrl.startsWith("http://") || decodedUrl.startsWith("https://") }

    private fun String.htmlAttribute(name: String): String? =
        Regex("""\b${Regex.escape(name)}=(["'])(.*?)\1""")
            .find(this)
            ?.groupValues
            ?.getOrNull(2)
            ?.let(UrlNormalizer::decodeHtmlEntities)
            ?.takeIf { value -> value.isNotBlank() }
            ?: runCatching {
                Jsoup.parse(this).selectFirst("[$name]")
                    ?.attr(name)
                    ?.let(UrlNormalizer::decodeHtmlEntities)
                    ?.takeIf { value -> value.isNotBlank() }
            }.getOrNull()

    private fun String.googleNewsArticleId(): String? =
        runCatching {
            URI(this).rawPath
                .orEmpty()
                .split('/')
                .filter { segment -> segment.isNotBlank() }
                .let { segments ->
                    val articlesIndex = segments.indexOf("articles")
                    segments.getOrNull(articlesIndex + 1)
                }
        }.getOrNull()

    private fun String.isGoogleNewsArticleUrl(): Boolean =
        runCatching {
            val uri = URI(this)
            val host = uri.host.orEmpty().lowercase()
            (host == "news.google.com" || host.endsWith(".news.google.com")) &&
                uri.rawPath.orEmpty().split('/').contains("articles")
        }.getOrDefault(false)

    private fun String.isGoogleNewsUrl(): Boolean =
        runCatching { URI(this).host.orEmpty().lowercase() }
            .getOrDefault("")
            .let { host -> host == "news.google.com" || host.endsWith(".news.google.com") }

    private fun String.encodeFormValue(): String =
        URLEncoder.encode(this, StandardCharsets.UTF_8.name())

    private fun String.toJsonString(): String =
        buildString {
            append('"')
            this@toJsonString.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
            append('"')
        }

    private fun String.decodeJsonStringFragment(): String {
        val decoded = StringBuilder(length)
        var index = 0
        while (index < length) {
            val char = this[index]
            if (char != '\\' || index + 1 >= length) {
                decoded.append(char)
                index += 1
                continue
            }

            when (val escaped = this[index + 1]) {
                '"', '\\', '/' -> {
                    decoded.append(escaped)
                    index += 2
                }
                'b' -> {
                    decoded.append('\b')
                    index += 2
                }
                'f' -> {
                    decoded.append('\u000C')
                    index += 2
                }
                'n' -> {
                    decoded.append('\n')
                    index += 2
                }
                'r' -> {
                    decoded.append('\r')
                    index += 2
                }
                't' -> {
                    decoded.append('\t')
                    index += 2
                }
                'u' -> {
                    val hex = substring(index + 2, (index + 6).coerceAtMost(length))
                    val codePoint = hex.takeIf { it.length == 4 }?.toIntOrNull(radix = 16)
                    if (codePoint == null) {
                        decoded.append(escaped)
                        index += 2
                    } else {
                        decoded.append(codePoint.toChar())
                        index += 6
                    }
                }
                else -> {
                    decoded.append(escaped)
                    index += 2
                }
            }
        }
        return decoded.toString()
    }

    private companion object {
        val FORM_URLENCODED = "application/x-www-form-urlencoded;charset=UTF-8".toMediaType()
        const val ACCEPT_LANGUAGE = "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7"
        const val HTML_ACCEPT =
            "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        const val GOOGLE_NEWS_ORIGIN = "https://news.google.com"
        const val GOOGLE_NEWS_BATCH_EXECUTE_URL =
            "https://news.google.com/_/DotsSplashUi/data/batchexecute?rpcids=Fbv4je"
        val GOOGLE_NEWS_DECODED_URL_REGEXES = listOf(
            Regex("""\\\"garturlres\\\",\\\"(.+?)\\\",(?:\d+|null)"""),
            Regex(""""garturlres","(.+?)",(?:\d+|null)""")
        )
    }
}

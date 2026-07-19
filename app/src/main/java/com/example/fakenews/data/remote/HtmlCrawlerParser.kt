package com.example.fakenews.data.remote

import com.example.fakenews.data.model.NewsArticle
import com.example.fakenews.data.model.NewsPress
import com.example.fakenews.data.model.NewsSourceType
import com.example.fakenews.data.model.ArticleType
import com.example.fakenews.util.ArticleContentExtractor
import com.example.fakenews.util.ArticleIdentity
import com.example.fakenews.util.ArticlePublishedTimeExtractor
import com.example.fakenews.util.ArticleTitleCleaner
import com.example.fakenews.util.ArticleTypeClassifier
import com.example.fakenews.util.ArticleUrlPolicy
import com.example.fakenews.util.KeywordExtractor
import com.example.fakenews.util.MediaUrlDetector
import com.example.fakenews.util.UrlNormalizer
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

object HtmlCrawlerParser {
    data class ArticleBody(
        val content: String,
        val bodyParagraphs: List<String>,
        val imageUrl: String?,
        val videoUrl: String?
    )

    fun parse(
        html: String,
        baseUrl: String,
        press: NewsPress,
        nowMillis: Long = 0L,
        enforceAllowedDomain: Boolean = false,
        maxArticles: Int = NewsFetchConfig.MAX_ARTICLES_PER_PRESS
    ): List<NewsArticle> =
        parse(
            document = Jsoup.parse(html, baseUrl),
            baseUrl = baseUrl,
            press = press,
            nowMillis = nowMillis,
            enforceAllowedDomain = enforceAllowedDomain,
            maxArticles = maxArticles
        )

    fun parse(
        document: Document,
        baseUrl: String,
        press: NewsPress,
        nowMillis: Long = 0L,
        enforceAllowedDomain: Boolean = false,
        maxArticles: Int = NewsFetchConfig.MAX_ARTICLES_PER_PRESS
    ): List<NewsArticle> {
        val linkCandidates = document.select("article a[href], li a[href], div a[href], a[href]")
        val metaDescription = document.selectFirst("meta[name=description]")?.attr("content")
            ?.cleanText()
            .orEmpty()
        val defaultImageUrl = document.selectFirst("meta[property=og:image]")?.absUrl("content")
            ?.takeIf { url -> url.isNotBlank() }
        val defaultVideoUrl = document.videoUrlFromMeta()
        val articles = linkCandidates
            .asSequence()
            .mapNotNull { element ->
                element.toNewsArticle(
                    baseUrl = baseUrl,
                    press = press,
                    metaDescription = metaDescription,
                    defaultImageUrl = defaultImageUrl,
                    defaultVideoUrl = defaultVideoUrl,
                    nowMillis = nowMillis,
                    enforceAllowedDomain = enforceAllowedDomain
                )
            }
            .distinctBy { article -> article.originalUrl }
            .take(maxArticles)
            .toList()

        return articles
    }

    fun parseDetailBody(
        html: String,
        baseUrl: String,
        press: NewsPress
    ): ArticleBody {
        val document = Jsoup.parse(html, baseUrl)
        val extracted = ArticleContentExtractor.extract(document, press)
        val bodyParagraphs = extracted.paragraphs
        val content = bodyParagraphs.joinToString("\n\n")

        return ArticleBody(
            content = content,
            bodyParagraphs = bodyParagraphs,
            imageUrl = extracted.imageUrl,
            videoUrl = extracted.videoUrl
        )
    }

    private fun Element.toNewsArticle(
        baseUrl: String,
        press: NewsPress,
        metaDescription: String,
        defaultImageUrl: String?,
        defaultVideoUrl: String?,
        nowMillis: Long,
        enforceAllowedDomain: Boolean
    ): NewsArticle? {
        val title = ArticleTitleCleaner.cleanTitle(ownText().ifBlank { text() }.cleanText())
        val rawOriginalUrl = UrlNormalizer.decodeHtmlEntities(
            absUrl("href").ifBlank { attr("href") }
        ).takeIf { url ->
            url.startsWith("http://") || url.startsWith("https://")
        } ?: return null
        val originalUrl = canonicalArticleUrl(
            press = press,
            rawUrl = rawOriginalUrl
        )

        if (title.length < MIN_TITLE_LENGTH) return null
        if (enforceAllowedDomain && !ArticleUrlPolicy.isValidArticleUrl(press, originalUrl)) return null

        val imageUrl = selectFirst("img[src]")?.absUrl("src")
            ?.takeIf { url -> url.isNotBlank() }
            ?: parent()?.selectFirst("img[src]")?.absUrl("src")?.takeIf { url -> url.isNotBlank() }
            ?: defaultImageUrl
        val videoUrl = videoUrlFromElement() ?: parent()?.videoUrlFromElement() ?: defaultVideoUrl
        val summary = candidateSummary(
            press = press,
            metaDescription = metaDescription,
            title = title
        )
        val bodyParagraphs = ArticleContentExtractor.extractParagraphsFromElement(parent() ?: this, title)
        val content = bodyParagraphs.joinToString("\n\n").ifBlank { summary }
        val publishedTime = ArticlePublishedTimeExtractor.extractFromElement(parent() ?: this)
        val articleType = ArticleTypeClassifier.classify(
            title = title,
            originalUrl = originalUrl,
            bodyText = content,
            metaText = summary,
            videoUrl = videoUrl
        )
        if (!ArticleTypeClassifier.isNewsArticle(articleType)) return null

        return NewsArticle(
            id = ArticleIdentity.idFor(press, originalUrl, title),
            title = title,
            press = press,
            publishedAt = publishedTime?.epochMillis,
            publishedAtSource = publishedTime?.source,
            summary = summary,
            content = content,
            bodyParagraphs = bodyParagraphs,
            imageUrl = imageUrl,
            videoUrl = videoUrl,
            originalUrl = originalUrl,
            keywords = KeywordExtractor.extract("$title $summary $content"),
            sourceType = NewsSourceType.HTML_CRAWLING,
            sourceLabel = NewsSourceType.HTML_CRAWLING.displayName,
            articleType = articleType,
            isVideoNews = videoUrl != null || articleType == ArticleType.VIDEO_NEWS_ARTICLE
        )
    }

    private fun String.cleanText(): String =
        replace(Regex("\\s+"), " ").trim()

    private fun Document.videoUrlFromMeta(): String? =
        listOf(
            "meta[property=og:video]",
            "meta[property=og:video:url]",
            "meta[property=og:video:secure_url]"
        ).asSequence()
            .mapNotNull { selector ->
                selectFirst(selector)?.absUrl("content")
                    ?.ifBlank { selectFirst(selector)?.attr("content") }
            }
            .firstOrNull { url -> MediaUrlDetector.isPlayableVideoUrl(url) }

    private fun Element.videoUrlFromElement(): String? =
        listOfNotNull(
            selectFirst("video[src]")?.absUrl("src"),
            selectFirst("video source[src]")?.absUrl("src"),
            selectFirst("source[src]")?.absUrl("src")
        ).firstOrNull { url -> MediaUrlDetector.isPlayableVideoUrl(url) }

    private fun canonicalArticleUrl(
        press: NewsPress,
        rawUrl: String
    ): String {
        val decodedRawUrl = UrlNormalizer.decodeHtmlEntities(rawUrl)
        if (press != NewsPress.YTN) return decodedRawUrl

        canonicalYtnMobileViewUrl(decodedRawUrl)?.let { canonicalUrl ->
            return canonicalUrl
        }
        val uri = runCatching { URI(decodedRawUrl) }.getOrNull() ?: return decodedRawUrl
        val host = uri.host.orEmpty().lowercase(Locale.ROOT)
        val path = uri.rawPath.orEmpty().lowercase(Locale.ROOT)
        if (host !in ytnHosts || path != "/news_view.php") return decodedRawUrl

        val queryParams = uri.rawQuery.orEmpty().queryParams()
        val key = queryParams["key"]?.takeIf { value -> value.isNotBlank() } ?: return decodedRawUrl
        val section = (queryParams["s_mcd"] ?: queryParams["amp;s_mcd"])
            ?.takeIf { value -> value.isNotBlank() }
            ?: return decodedRawUrl
        return "https://www.ytn.co.kr/_ln/${section}_$key"
    }

    private fun candidateSummary(
        press: NewsPress,
        metaDescription: String,
        title: String
    ): String =
        if (press == NewsPress.YTN) {
            title
        } else {
            metaDescription.ifBlank { title }
        }

    private fun canonicalYtnMobileViewUrl(url: String): String? {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null
        if (!url.contains("ytn.co.kr") || !url.contains("news_view.php")) return null

        val key = Regex("[?&]key=([^&]+)")
            .find(url)
            ?.groupValues
            ?.getOrNull(1)
            ?.takeIf { value -> value.isNotBlank() }
            ?: return null
        val section = Regex("[?&](?:amp;)?s_mcd=([^&]+)")
            .find(url)
            ?.groupValues
            ?.getOrNull(1)
            ?.takeIf { value -> value.isNotBlank() }
            ?: return null
        return "https://www.ytn.co.kr/_ln/${section}_$key"
    }

    private fun String.queryParams(): Map<String, String> =
        split('&')
            .mapNotNull { part ->
                val key = part.substringBefore('=', missingDelimiterValue = "").decodeUrlComponent()
                val value = part.substringAfter('=', missingDelimiterValue = "").decodeUrlComponent()
                if (key.isBlank()) null else key to value
            }
            .toMap()

    private fun String.decodeUrlComponent(): String =
        runCatching {
            URLDecoder.decode(this, StandardCharsets.UTF_8.name())
        }.getOrDefault(this)

    private const val MIN_TITLE_LENGTH = 8

    private val ytnHosts = setOf(
        "m.ytn.co.kr",
        "www.ytn.co.kr",
        "ytn.co.kr"
    )
}

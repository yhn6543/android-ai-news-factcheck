package com.example.fakenews.data.remote

import com.example.fakenews.data.model.NewsArticle
import com.example.fakenews.data.model.NewsPress
import com.example.fakenews.data.model.NewsSourceType
import com.example.fakenews.data.model.ArticleType
import com.example.fakenews.util.ArticleIdentity
import com.example.fakenews.util.ArticleTextExtractor
import com.example.fakenews.util.ArticleTypeClassifier
import com.example.fakenews.util.ArticleTitleCleaner
import com.example.fakenews.util.ArticleUrlPolicy
import com.example.fakenews.util.DateParser
import com.example.fakenews.util.KeywordExtractor
import com.example.fakenews.util.MediaUrlDetector
import com.example.fakenews.util.PressDomainMatcher
import com.example.fakenews.util.UrlNormalizer
import java.io.StringReader
import java.net.URI
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource

object RssParser {
    private val imageRegex = Regex(
        pattern = "<img[^>]+(?:src|data-src|data-original)=[\"']([^\"']+)[\"']",
        options = setOf(RegexOption.IGNORE_CASE)
    )

    fun parse(
        xml: String,
        press: NewsPress,
        sourceType: NewsSourceType = NewsSourceType.RSS,
        sourceLabel: String = NewsSourceType.RSS.displayName,
        originalUrlMode: RssOriginalUrlMode = RssOriginalUrlMode.DIRECT,
        originalUrlResolver: OriginalUrlResolver? = null,
        feedUrl: String = "",
        ytnTraceCollector: YtnPipelineTraceCollector? = null,
        maxItems: Int = Int.MAX_VALUE,
        maxArticles: Int = Int.MAX_VALUE
    ): List<NewsArticle> {
        val document = DocumentBuilderFactory.newInstance()
            .apply {
                isNamespaceAware = false
                setFeatureSafely("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeatureSafely("http://xml.org/sax/features/external-general-entities", false)
                setFeatureSafely("http://xml.org/sax/features/external-parameter-entities", false)
            }
            .newDocumentBuilder()
            .parse(InputSource(StringReader(xml.escapeBareAmpersands())))

        val items = document.getElementsByTagName("item").toElements() +
            document.getElementsByTagName("entry").toElements()
        val articles = mutableListOf<NewsArticle>()
        for (item in items.asSequence().take(maxItems)) {
            val article = item.toNewsArticle(
                press = press,
                sourceType = sourceType,
                sourceLabel = sourceLabel,
                originalUrlMode = originalUrlMode,
                originalUrlResolver = originalUrlResolver,
                feedUrl = feedUrl,
                ytnTraceCollector = ytnTraceCollector.takeIf { press == NewsPress.YTN }
            ) ?: continue
            articles += article
            if (articles.size >= maxArticles) break
        }
        return articles
    }

    fun countItems(xml: String): Int =
        runCatching {
            val document = DocumentBuilderFactory.newInstance()
                .apply {
                    isNamespaceAware = false
                    setFeatureSafely("http://apache.org/xml/features/disallow-doctype-decl", true)
                    setFeatureSafely("http://xml.org/sax/features/external-general-entities", false)
                    setFeatureSafely("http://xml.org/sax/features/external-parameter-entities", false)
                }
                .newDocumentBuilder()
                .parse(InputSource(StringReader(xml.escapeBareAmpersands())))

            document.getElementsByTagName("item").length +
                document.getElementsByTagName("entry").length
        }.getOrDefault(0)

    fun googleNewsCandidateUrls(
        xml: String,
        press: NewsPress,
        maxItems: Int
    ): List<String> =
        runCatching {
            val document = DocumentBuilderFactory.newInstance()
                .apply {
                    isNamespaceAware = false
                    setFeatureSafely("http://apache.org/xml/features/disallow-doctype-decl", true)
                    setFeatureSafely("http://xml.org/sax/features/external-general-entities", false)
                    setFeatureSafely("http://xml.org/sax/features/external-parameter-entities", false)
                }
                .newDocumentBuilder()
                .parse(InputSource(StringReader(xml.escapeBareAmpersands())))

            val items = document.getElementsByTagName("item").toElements() +
                document.getElementsByTagName("entry").toElements()
            items.asSequence()
                .take(maxItems)
                .filter { item -> item.googleNewsSourceMatchesPress(press) }
                .map { item ->
                    UrlNormalizer.decodeHtmlEntities(
                        item.linkUrl().ifBlank { item.childText("guid", "id") }
                    ).trim()
                }
                .filter { url -> url.startsWith("http://") || url.startsWith("https://") }
                .distinct()
                .toList()
        }.getOrDefault(emptyList())

    private fun Element.toNewsArticle(
        press: NewsPress,
        sourceType: NewsSourceType,
        sourceLabel: String,
        originalUrlMode: RssOriginalUrlMode,
        originalUrlResolver: OriginalUrlResolver?,
        feedUrl: String,
        ytnTraceCollector: YtnPipelineTraceCollector?
    ): NewsArticle? {
        val rawTitle = childText("title")
        val rawDescription = childText("description", "summary")
        val traceItem = ytnTraceCollector?.beginItem(
            feedUrl = feedUrl,
            rawTitle = rawTitle.cleanText(),
            rawLink = linkUrl().ifBlank { childText("guid", "id") }.trim(),
            rawPubDate = childText("pubDate", "published", "updated"),
            rawDescriptionLength = rawDescription.length
        )
        val title = ArticleTitleCleaner.cleanTitle(rawTitle.cleanText())
        traceItem?.let { item ->
            val titleCheck = YtnTitlePolicy.validateTitle(rawTitle.cleanText())
            ytnTraceCollector?.recordTitle(
                item = item,
                cleanedTitle = title,
                titleValid = titleCheck.first,
                titleInvalidReason = titleCheck.second
            )
        }
        val rawOriginalUrl = UrlNormalizer.decodeHtmlEntities(
            linkUrl().ifBlank { childText("guid", "id") }
        ).trim()
        if (originalUrlMode == RssOriginalUrlMode.GOOGLE_NEWS && !googleNewsSourceMatchesPress(press)) {
            return null
        }
        val originalUrl = resolveOriginalUrl(
            rawOriginalUrl = rawOriginalUrl,
            press = press,
            originalUrlMode = originalUrlMode,
            originalUrlResolver = originalUrlResolver,
            traceItem = traceItem,
            ytnTraceCollector = ytnTraceCollector
        ) ?: return null
        if (traceItem != null && traceItem.titleValid.not()) return null
        if (title.isBlank() || originalUrl.isBlank()) return null

        val rawContent = childText("content:encoded", "encoded", "content")
        val bodySource = rawContent
            .ifBlank { rawDescription }
            .ifBlank { title }
        val bodyParagraphs = ArticleTitleCleaner.removeDuplicatedTitleFromParagraphs(
            title = title,
            paragraphs = ArticleTextExtractor.paragraphsFromHtml(bodySource)
        )
        val content = bodyParagraphs.joinToString("\n\n").ifBlank { title }
        val summary = ArticleTextExtractor.plainText(rawDescription)
            .ifBlank { bodyParagraphs.firstOrNull().orEmpty() }
            .ifBlank { title }
        val publishedTime = publishedTime()
        val imageUrl = enclosureImageUrl()
            ?: mediaContentImageUrl()
            ?: mediaThumbnailImageUrl()
            ?: rawDescription.extractImageUrl()
        val videoUrl = enclosureVideoUrl()
            ?: mediaContentVideoUrl()
        val articleType = ArticleTypeClassifier.classify(
            title = title,
            originalUrl = originalUrl,
            bodyText = content,
            metaText = summary,
            videoUrl = videoUrl
        )

        return NewsArticle(
            id = ArticleIdentity.idFor(press, originalUrl, title),
            title = title,
            press = press,
            publishedAt = publishedTime?.epochMillis,
            publishedAtSource = publishedTime?.source,
            summary = summary.ifBlank { title },
            content = content.ifBlank { summary.ifBlank { title } },
            bodyParagraphs = bodyParagraphs,
            imageUrl = imageUrl,
            videoUrl = videoUrl,
            originalUrl = originalUrl,
            keywords = KeywordExtractor.extract("$title $summary $content"),
            sourceType = sourceType,
            sourceLabel = sourceLabel,
            articleType = articleType,
            isVideoNews = videoUrl != null || articleType == ArticleType.VIDEO_NEWS_ARTICLE
        )
    }

    private fun Element.resolveOriginalUrl(
        rawOriginalUrl: String,
        press: NewsPress,
        originalUrlMode: RssOriginalUrlMode,
        originalUrlResolver: OriginalUrlResolver?,
        traceItem: YtnPipelineDebugItem?,
        ytnTraceCollector: YtnPipelineTraceCollector?
    ): String? {
        val resolution = when (originalUrlMode) {
            RssOriginalUrlMode.DIRECT -> GoogleNewsOriginalUrlResolution(
                resolvedUrl = rawOriginalUrl,
                resolveSuccess = rawOriginalUrl.isNotBlank(),
                resolveFailureReason = if (rawOriginalUrl.isBlank()) "original_url_empty" else null
            )
            RssOriginalUrlMode.GOOGLE_NEWS -> GoogleNewsOriginalUrlExtractor.resolveOriginalUrlWithReason(
                item = this,
                rawUrl = rawOriginalUrl,
                resolver = originalUrlResolver
            )
        }
        val resolvedUrl = UrlNormalizer.decodeHtmlEntities(resolution.resolvedUrl).trim()
        if (traceItem != null && ytnTraceCollector != null) {
            ytnTraceCollector.recordResolve(traceItem, resolution)
            val inspection = ArticleUrlPolicy.inspect(press, resolvedUrl)
            ytnTraceCollector.recordUrlPolicy(traceItem, inspection)
        }

        return resolvedUrl.takeIf { url ->
            url.isNotBlank() && ArticleUrlPolicy.isValidArticleUrl(press, url)
        }
    }

    private fun Element.googleNewsSourceMatchesPress(press: NewsPress): Boolean {
        val sourceUrl = firstDirectChild("source")
            ?.getAttribute("url")
            ?.let(UrlNormalizer::decodeHtmlEntities)
            ?.trim()
            .orEmpty()
        if (sourceUrl.isBlank()) return true
        return PressDomainMatcher.isUrlAllowedForPress(sourceUrl, press)
    }

    private fun Element.publishedTime(): RssPublishedTime? {
        val candidates = listOf(
            "pubDate" to "rss:pubDate",
            "published" to "atom:published",
            "updated" to "atom:updated"
        )
        return candidates.firstNotNullOfOrNull { (tagName, source) ->
            val rawValue = childText(tagName)
            DateParser.parseEpochMillis(rawValue)?.let { epochMillis ->
                RssPublishedTime(
                    epochMillis = epochMillis,
                    source = source
                )
            }
        }
    }

    private fun Element.linkUrl(): String {
        val directLink = childText("link").trim()
        if (directLink.isNotBlank()) return directLink

        val linkElement = firstDirectChild("link") ?: return ""
        return linkElement.getAttribute("href").takeIf { href -> href.isNotBlank() }
            ?: linkElement.textContent.orEmpty()
    }

    private fun Element.childText(vararg names: String): String {
        val wantedNames = names.toSet()
        val children = childNodes
        for (index in 0 until children.length) {
            val child = children.item(index)
            if (child.nodeType == Node.ELEMENT_NODE && child.matchesAnyName(wantedNames)) {
                return child.textContent.orEmpty()
            }
        }
        return ""
    }

    private fun Element.enclosureImageUrl(): String? =
        directChildren("enclosure")
            .firstNotNullOfOrNull { element ->
                val url = element.getAttribute("url")
                val isImage =
                    element.getAttribute("type").startsWith("image/", ignoreCase = true) ||
                    element.getAttribute("url").looksLikeImageUrl()
                if (isImage) url.normalizedRemoteUrl() else null
            }

    private fun Element.enclosureVideoUrl(): String? =
        directChildren("enclosure")
            .firstNotNullOfOrNull { element ->
                val url = element.getAttribute("url")
                val isVideo =
                    element.getAttribute("type").startsWith("video/", ignoreCase = true) ||
                    MediaUrlDetector.isPlayableVideoUrl(element.getAttribute("url"))
                if (isVideo) url.normalizedRemoteUrl()?.takeIf(MediaUrlDetector::isPlayableVideoUrl) else null
            }

    private fun Element.mediaContentImageUrl(): String? =
        directChildren("media:content", "content")
            .firstNotNullOfOrNull { element ->
                val url = element.getAttribute("url")
                val isImage =
                    element.getAttribute("medium").equals("image", ignoreCase = true) ||
                    element.getAttribute("type").startsWith("image/", ignoreCase = true) ||
                    element.getAttribute("url").looksLikeImageUrl()
                if (isImage) url.normalizedRemoteUrl() else null
            }

    private fun Element.mediaContentVideoUrl(): String? =
        directChildren("media:content", "content")
            .firstNotNullOfOrNull { element ->
                val url = element.getAttribute("url")
                val isVideo =
                    element.getAttribute("medium").equals("video", ignoreCase = true) ||
                    element.getAttribute("type").startsWith("video/", ignoreCase = true) ||
                    MediaUrlDetector.isPlayableVideoUrl(element.getAttribute("url"))
                if (isVideo) url.normalizedRemoteUrl()?.takeIf(MediaUrlDetector::isPlayableVideoUrl) else null
            }

    private fun Element.mediaThumbnailImageUrl(): String? =
        directChildren("media:thumbnail", "thumbnail")
            .firstNotNullOfOrNull { element ->
                element.getAttribute("url").normalizedRemoteUrl()
            }

    private fun Element.directChildren(vararg names: String): List<Element> {
        val wantedNames = names.toSet()
        val children = childNodes
        return (0 until children.length).mapNotNull { index ->
            val child = children.item(index)
            (child as? Element)?.takeIf { element -> element.matchesAnyName(wantedNames) }
        }
    }

    private fun Element.firstDirectChild(vararg names: String): Element? {
        val wantedNames = names.toSet()
        val children = childNodes
        for (index in 0 until children.length) {
            val child = children.item(index)
            if (child.nodeType == Node.ELEMENT_NODE && child.matchesAnyName(wantedNames)) {
                return child as? Element
            }
        }
        return null
    }

    private fun Node.matchesAnyName(names: Set<String>): Boolean {
        val nodeNameValue = nodeName.orEmpty()
        val localNameValue = localName.orEmpty()
        return nodeNameValue in names ||
            localNameValue in names ||
            nodeNameValue.substringAfter(':') in names
    }

    private data class RssPublishedTime(
        val epochMillis: Long,
        val source: String
    )

    private fun String.extractImageUrl(): String? =
        imageRegex.find(this)
            ?.groupValues
            ?.getOrNull(1)
            ?.normalizedRemoteUrl()

    private fun String.cleanText(): String =
        ArticleTextExtractor.plainText(this)

    private fun String.escapeBareAmpersands(): String =
        replace(Regex("&(?!#\\d+;|#x[0-9a-fA-F]+;|amp;|lt;|gt;|quot;|apos;)"), "&amp;")

    private fun String.looksLikeImageUrl(): Boolean {
        val lower = runCatching { URI(this).path.orEmpty().lowercase() }
            .getOrDefault(substringBefore('?').lowercase())
        return lower.endsWith(".jpg") ||
            lower.endsWith(".jpeg") ||
            lower.endsWith(".png") ||
            lower.endsWith(".webp") ||
            lower.endsWith(".gif")
    }

    private fun String.normalizedRemoteUrl(): String? {
        val value = trim()
            .replace("&amp;", "&")
        return when {
            value.startsWith("https://") || value.startsWith("http://") -> value
            value.startsWith("//") -> "https:$value"
            else -> null
        }
    }

    private fun DocumentBuilderFactory.setFeatureSafely(
        feature: String,
        value: Boolean
    ) {
        runCatching { setFeature(feature, value) }
    }

    private fun org.w3c.dom.NodeList.toElements(): List<Element> =
        (0 until length).mapNotNull { index -> item(index) as? Element }
}

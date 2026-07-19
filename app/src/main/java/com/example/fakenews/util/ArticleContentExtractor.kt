package com.example.fakenews.util

import com.example.fakenews.data.model.NewsPress
import com.example.fakenews.data.remote.NewsFetchLogger
import java.net.URI
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

data class ExtractedArticleContent(
    val title: String?,
    val paragraphs: List<String>,
    val summary: String?,
    val imageUrl: String?,
    val videoUrl: String?,
    val publishedTime: PublishedTimeExtraction?,
    val boilerplateRemovedCount: Int = 0
)

object ArticleContentExtractor {
    fun extract(
        document: Document,
        press: NewsPress
    ): ExtractedArticleContent {
        val boilerplateKeywordCountBefore = ArticleBoilerplateCleaner.boilerplateKeywordCount(document.text())
        val cleanedDocument = document.clone()
        ArticleBoilerplateCleaner.removeBoilerplateElements(cleanedDocument)

        val title = extractTitle(cleanedDocument)
        val summary = cleanedDocument.selectFirst("meta[name=description]")?.attr("content")
            ?.let(ArticleBoilerplateCleaner::cleanParagraph)
            ?.takeIf { it.isNotBlank() }
        val imageUrl = extractImageUrl(cleanedDocument)
        val videoUrl = extractVideoUrl(cleanedDocument)
        val publishedTime = ArticlePublishedTimeExtractor.extract(cleanedDocument)

        val mbcParagraphs = if (press == NewsPress.MBC) {
            mbcNewsTxtParagraphs(cleanedDocument, title)
        } else {
            null
        }
        val bestParagraphs = mbcParagraphs
            ?: run {
            candidateSelectors(press)
                .flatMap { selector -> cleanedDocument.select(selector) }
                .distinct()
                .map { element -> scoredCandidate(element, title) }
                .filter { candidate -> candidate.paragraphs.isNotEmpty() }
                .maxByOrNull { candidate -> candidate.score }
                ?.paragraphs
                ?: fallbackParagraphs(cleanedDocument, title)
            }

        val remainingText = bestParagraphs.joinToString(" ")
        val boilerplateKeywordCountAfter = ArticleBoilerplateCleaner.boilerplateKeywordCount(remainingText)
        val removedCount = (boilerplateKeywordCountBefore - boilerplateKeywordCountAfter).coerceAtLeast(0)
        NewsFetchLogger.logArticleExtract(
            "press=${press.displayName} paragraphCount=${bestParagraphs.size} boilerplateRemoved count=$removedCount"
        )

        return ExtractedArticleContent(
            title = title,
            paragraphs = bestParagraphs,
            summary = summary,
            imageUrl = imageUrl,
            videoUrl = videoUrl,
            publishedTime = publishedTime,
            boilerplateRemovedCount = removedCount
        )
    }

    fun extractParagraphsFromElement(
        element: Element,
        title: String? = null
    ): List<String> {
        val cloned = element.clone()
        ArticleBoilerplateCleaner.removeBoilerplateElements(cloned)

        val paragraphs = cloned.select("p, li")
            .takeIf { elements -> elements.isNotEmpty() }
            ?.mapNotNull { block -> paragraphText(block).takeIf { it.isNotBlank() } }
            ?: paragraphText(cloned)
                .split('\n')
                .mapNotNull { text -> text.takeIf { it.isNotBlank() } }

        return ArticleTitleCleaner.removeDuplicatedTitleFromParagraphs(
            title = title.orEmpty(),
            paragraphs = deduplicateParagraphs(
                paragraphs
                    .map(ArticleBoilerplateCleaner::cleanParagraph)
                    .filterNot(ArticleBoilerplateCleaner::isBoilerplateParagraph)
            )
        )
    }

    private fun extractTitle(document: Document): String? =
        listOfNotNull(
            document.selectFirst("meta[property=og:title]")?.attr("content"),
            document.selectFirst("h1")?.text(),
            document.title()
        )
            .map(ArticleTitleCleaner::cleanTitle)
            .firstOrNull { it.isNotBlank() }

    private fun scoredCandidate(
        element: Element,
        title: String?
    ): Candidate {
        val rawText = element.text()
        val paragraphs = extractParagraphsFromElement(element, title)
        val totalLength = paragraphs.sumOf { it.length }
        val boilerplatePenalty = ArticleBoilerplateCleaner.boilerplateKeywordCount(rawText) * 20
        val duplicatePenalty = duplicateCount(paragraphs) * 10
        val score = (paragraphs.size * 10) + (totalLength / 100) - boilerplatePenalty - duplicatePenalty

        return Candidate(
            paragraphs = paragraphs,
            score = score
        )
    }

    private fun fallbackParagraphs(
        document: Document,
        title: String?
    ): List<String> =
        document.select("p")
            .mapNotNull { paragraph -> paragraphText(paragraph).takeIf { it.isNotBlank() } }
            .let { paragraphs ->
                ArticleTitleCleaner.removeDuplicatedTitleFromParagraphs(
                    title = title.orEmpty(),
                    paragraphs = deduplicateParagraphs(
                        paragraphs
                            .map(ArticleBoilerplateCleaner::cleanParagraph)
                            .filterNot(ArticleBoilerplateCleaner::isBoilerplateParagraph)
                    )
                )
            }

    private fun mbcNewsTxtParagraphs(
        document: Document,
        title: String?
    ): List<String>? {
        val newsTextElements = document.select(".news_txt, div.news_txt")
        if (newsTextElements.isEmpty()) return null

        val hasRawNewsText = newsTextElements.any { element ->
            ArticleBoilerplateCleaner.cleanParagraph(element.text()).isNotBlank()
        }
        if (!hasRawNewsText) return null

        return ArticleTitleCleaner.removeDuplicatedTitleFromParagraphs(
            title = title.orEmpty(),
            paragraphs = deduplicateParagraphs(
                newsTextElements
                    .flatMap { element -> ArticleTextExtractor.paragraphsFromHtml(element.html()) }
                    .map(ArticleBoilerplateCleaner::cleanParagraph)
                    .filter { paragraph -> paragraph.isNotBlank() }
                    .filterNot(ArticleBoilerplateCleaner::isBoilerplateParagraph)
            )
        )
    }

    private fun paragraphText(element: Element): String {
        val cloned = element.clone()
        cloned.select("br").append("\n")
        val prefix = if (cloned.tagName().equals("li", ignoreCase = true)) "• " else ""
        return prefix + cloned.wholeText()
            .split('\n')
            .joinToString("\n") { line -> ArticleBoilerplateCleaner.cleanParagraph(line) }
            .trim()
    }

    private fun deduplicateParagraphs(paragraphs: List<String>): List<String> {
        val seen = mutableSetOf<String>()
        return paragraphs.filter { paragraph ->
            val normalized = paragraph.lowercase()
            if (normalized in seen) {
                false
            } else {
                seen += normalized
                true
            }
        }
    }

    private fun duplicateCount(paragraphs: List<String>): Int =
        paragraphs.size - paragraphs.map { it.lowercase() }.toSet().size

    private fun candidateSelectors(press: NewsPress): List<String> =
        pressSpecificSelectors[press].orEmpty() + commonSelectors

    private fun extractVideoUrl(document: Document): String? =
        listOf(
            "meta[property=og:video]",
            "meta[property=og:video:url]",
            "meta[property=og:video:secure_url]"
        )
            .asSequence()
            .mapNotNull { selector ->
                document.selectFirst(selector)?.absUrl("content")
                    ?.ifBlank { document.selectFirst(selector)?.attr("content") }
            }
            .firstOrNull(MediaUrlDetector::isPlayableVideoUrl)
            ?: listOfNotNull(
                document.selectFirst("video[src]")?.absUrl("src"),
                document.selectFirst("video source[src]")?.absUrl("src"),
                document.selectFirst("source[src]")?.absUrl("src")
            ).firstOrNull(MediaUrlDetector::isPlayableVideoUrl)

    private fun extractImageUrl(document: Document): String? {
        val metaImage = listOf(
            "meta[property=og:image]",
            "meta[name=twitter:image]",
            "meta[name=image]"
        )
            .asSequence()
            .mapNotNull { selector -> document.selectFirst(selector)?.normalizedUrl("content") }
            .firstOrNull()
        if (metaImage != null) return metaImage

        return document.select(articleImageSelectors)
            .asSequence()
            .flatMap { element ->
                sequenceOf(
                    element.normalizedUrl("src"),
                    element.normalizedUrl("data-src"),
                    element.normalizedUrl("data-original"),
                    element.normalizedSrcsetUrl("srcset")
                )
                    .filterNotNull()
                    .map { imageUrl -> element to imageUrl }
            }
            .filterNot { (element, imageUrl) -> isLikelyDecorativeImage(element, imageUrl) }
            .map { (_, imageUrl) -> imageUrl }
            .firstOrNull()
    }

    private fun Element.normalizedUrl(attribute: String): String? =
        absUrl(attribute)
            .ifBlank { attr(attribute) }
            .trim()
            .takeIf { value -> value.isNotBlank() && !value.startsWith("data:", ignoreCase = true) }

    private fun Element.normalizedSrcsetUrl(attribute: String): String? {
        val candidate = attr(attribute)
            .split(',')
            .asSequence()
            .map { entry -> entry.trim().substringBefore(' ').trim() }
            .firstOrNull { entry -> entry.isNotBlank() }
            ?: return null

        return normalizePossibleRelativeUrl(candidate, baseUri())
            ?.takeIf { value -> !value.startsWith("data:", ignoreCase = true) }
    }

    private fun normalizePossibleRelativeUrl(
        candidate: String,
        baseUri: String
    ): String? {
        val trimmed = candidate.trim()
        if (trimmed.isBlank()) return null

        return when {
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            else -> runCatching { URI(baseUri).resolve(trimmed).toString() }
                .getOrElse { trimmed }
        }
    }

    private fun isLikelyDecorativeImage(
        element: Element,
        imageUrl: String
    ): Boolean {
        val width = element.attr("width").toIntOrNull()
        val height = element.attr("height").toIntOrNull()
        if (width != null && height != null && width <= 80 && height <= 80) return true

        val marker = listOf(
            imageUrl,
            element.attr("alt"),
            element.className(),
            element.id()
        ).joinToString(" ").lowercase()

        return decorativeImageMarkers.any { marker.contains(it) }
    }

    private data class Candidate(
        val paragraphs: List<String>,
        val score: Int
    )

    private val pressSpecificSelectors = mapOf(
        NewsPress.MBC to listOf("div.news_txt", "div.article_txt", "div#news_content", "article"),
        NewsPress.SBS to listOf("div.article_cont_area", "div.text_area", "div.news_text", "article"),
        NewsPress.KBS to listOf("div.detail-body", "div#cont_newstext", "div.news_content", "article"),
        NewsPress.YTN to listOf(
            "div#CmAdContent",
            "div.paragraph",
            "div.paragraph.flexible_font",
            "div#newsContent",
            "div.article_content",
            "div.article_body",
            "div.news_view",
            "div.news-contents",
            "article"
        ),
        NewsPress.YONHAP to listOf("div.article", "div.story-news", "div.news-con", "article")
    )

    private val commonSelectors = listOf(
        "article",
        ".article_view",
        ".news_view",
        ".view_text",
        ".article-body",
        ".articleBody",
        ".news-contents",
        ".content"
    )

    private const val articleImageSelectors =
        "article img, article source, .article img, .story-news img, .news-con img, .content img, img"
    private val decorativeImageMarkers = listOf(
        "/ad/",
        "_ad",
        "ad_",
        "advert",
        "banner",
        "btn",
        "button",
        "icon",
        "logo",
        "share",
        "sns"
    )
}

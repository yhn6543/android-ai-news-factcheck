package com.example.fakenews.util

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

data class PublishedTimeExtraction(
    val epochMillis: Long,
    val source: String,
    val rawValue: String
)

object ArticlePublishedTimeExtractor {
    fun extract(document: Document): PublishedTimeExtraction? =
        extractFromMeta(document)
            ?: extractFromTimeElement(document)
            ?: extractFromText(document.text())

    fun extractFromElement(element: Element): PublishedTimeExtraction? =
        extractFromTimeElement(element)
            ?: extractFromText(element.text())

    private fun extractFromMeta(document: Document): PublishedTimeExtraction? {
        val selectors = listOf(
            "meta[property=article:published_time]",
            "meta[name=article:published_time]",
            "meta[property=og:article:published_time]",
            "meta[itemprop=datePublished]",
            "meta[name=date]",
            "meta[name=pubdate]",
            "meta[name=publishdate]",
            "meta[name=nextweb:createDate]",
            "meta[id=createDate]",
            "meta[name=nextweb:modDate]",
            "meta[id=modDate]"
        )

        return selectors.firstNotNullOfOrNull { selector ->
            val rawValue = document.selectFirst(selector)
                ?.attr("content")
                ?.takeIf { value -> value.isNotBlank() }
            rawValue?.toExtraction("html:$selector")
        }
    }

    private fun extractFromTimeElement(element: Element): PublishedTimeExtraction? =
        element.select("time[datetime], [itemprop=datePublished][datetime]")
            .firstNotNullOfOrNull { timeElement ->
                val rawValue = timeElement.attr("datetime")
                    .takeIf { value -> value.isNotBlank() }
                    ?: timeElement.text().takeIf { value -> value.isNotBlank() }
                rawValue?.toExtraction("html:time[datetime]")
            }

    private fun extractFromText(text: String): PublishedTimeExtraction? {
        val normalizedText = text.replace(Regex("\\s+"), " ").take(TEXT_SCAN_LIMIT)
        return textPatterns.firstNotNullOfOrNull { pattern ->
            pattern.find(normalizedText)
                ?.groupValues
                ?.getOrNull(1)
                ?.toExtraction("html:text")
        }
    }

    private fun String.toExtraction(source: String): PublishedTimeExtraction? =
        DateParser.parseEpochMillis(this)?.let { epochMillis ->
            PublishedTimeExtraction(
                epochMillis = epochMillis,
                source = source,
                rawValue = this.trim()
            )
        }

    private val textPatterns = listOf(
        Regex("(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2})"),
        Regex("(\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2})"),
        Regex("(\\d{4}\\.\\d{2}\\.\\d{2}\\.?\\s+\\d{2}:\\d{2}(?::\\d{2})?)"),
        Regex("(\\d{4}-\\d{2}-\\d{2}\\s+(?:오전|오후)\\s+\\d{1,2}:\\d{2})"),
        Regex("(\\d{4}\\.\\d{2}\\.\\d{2}\\.?\\s+(?:오전|오후)\\s+\\d{1,2}:\\d{2})"),
        Regex("(\\d{4}년\\s*\\d{1,2}월\\s*\\d{1,2}일\\s+(?:오전|오후)\\s+\\d{1,2}:\\d{2})"),
        Regex("(\\d{4}년\\s*\\d{1,2}월\\s*\\d{1,2}일\\s+\\d{1,2}:\\d{2})")
    )

    private const val TEXT_SCAN_LIMIT = 4_000
}

package com.example.fakenews.util

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

object ArticleTextExtractor {
    const val EMPTY_BODY_MESSAGE = "본문 요약을 불러오지 못했습니다. 원본 기사에서 확인해 주세요."

    fun paragraphsFromHtml(rawHtml: String): List<String> {
        val html = rawHtml.stripCdata()
        if (html.isBlank()) return emptyList()

        val document = Jsoup.parseBodyFragment(html)
        document.select("script, style, noscript").remove()

        val explicitParagraphs = document
            .select("p, li")
            .mapNotNull { element -> element.text().cleanParagraph().takeIf { it.isNotBlank() } }
            .filterNot(ArticleBoilerplateCleaner::isBoilerplateParagraph)

        if (explicitParagraphs.size >= 2) {
            return explicitParagraphs.distinctConsecutive()
        }

        document.select("br").append("\n")
        document.select("p, div, li").append("\n\n")

        return splitParagraphs(document.body().wholeText())
            .ifEmpty {
                listOfNotNull(
                    document.body().text().cleanParagraph().takeIf { it.isNotBlank() }
                )
            }
            .distinctConsecutive()
    }

    fun paragraphsFromElement(element: Element): List<String> {
        val paragraphElements = element
            .select("p, li")
            .mapNotNull { paragraph -> paragraph.text().cleanParagraph().takeIf { it.isNotBlank() } }

        return paragraphElements
            .ifEmpty { paragraphsFromHtml(element.html()) }
            .distinctConsecutive()
    }

    fun splitParagraphs(rawText: String): List<String> =
        rawText.stripCdata()
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .split(Regex("\\n+"))
            .flatMap { block ->
                block
                    .split(Regex("(?<=\\.)\\s+(?=[가-힣A-Z0-9])"))
                    .takeIf { parts -> parts.size > 1 && block.length > LONG_SINGLE_PARAGRAPH_LENGTH }
                    ?: listOf(block)
            }
            .mapNotNull { paragraph -> paragraph.cleanParagraph().takeIf { it.isNotBlank() } }
            .filterNot(ArticleBoilerplateCleaner::isBoilerplateParagraph)
            .distinctConsecutive()

    fun plainText(rawHtmlOrText: String): String =
        paragraphsFromHtml(rawHtmlOrText).joinToString("\n\n")

    fun displayParagraphs(
        bodyParagraphs: List<String>,
        content: String,
        summary: String,
        emptyMessage: String = UiText.ARTICLE_BODY_LOAD_FAILED
    ): List<String> =
        bodyParagraphs
            .mapNotNull { paragraph -> paragraph.cleanParagraph().takeIf { it.isNotBlank() } }
            .filterNot(ArticleBoilerplateCleaner::isBoilerplateParagraph)
            .ifEmpty { splitParagraphs(content) }
            .ifEmpty { splitParagraphs(summary) }
            .ifEmpty { listOf(emptyMessage) }

    private fun String.stripCdata(): String =
        replace("<![CDATA[", "")
            .replace("]]>", "")

    private fun String.cleanParagraph(): String =
        replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun List<String>.distinctConsecutive(): List<String> =
        fold(mutableListOf()) { acc, paragraph ->
            if (acc.lastOrNull() != paragraph) acc += paragraph
            acc
        }

    private const val LONG_SINGLE_PARAGRAPH_LENGTH = 240
}

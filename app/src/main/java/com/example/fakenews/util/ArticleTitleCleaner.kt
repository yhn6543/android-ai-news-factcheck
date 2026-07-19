package com.example.fakenews.util

object ArticleTitleCleaner {
    fun cleanTitle(rawTitle: String): String {
        val titleLine = rawTitle
            .replace("<![CDATA[", "")
            .replace("]]>", "")
            .replace('\r', '\n')
            .split('\n')
            .map { line -> normalizeTitle(line) }
            .firstOrNull { line -> line.length >= MIN_TITLE_LENGTH }
            ?: normalizeTitle(rawTitle)

        return removeSiteSuffix(titleLine)
            .take(MAX_TITLE_LENGTH)
            .trim()
    }

    fun removeDuplicatedTitleFromParagraphs(
        title: String,
        paragraphs: List<String>
    ): List<String> {
        val normalizedTitle = TitleNormalizer.normalize(cleanTitle(title))
        if (normalizedTitle.isBlank()) return paragraphs

        return paragraphs.filterIndexed { index, paragraph ->
            val normalizedParagraph = TitleNormalizer.normalize(cleanTitle(paragraph))
            !(index == 0 && normalizedParagraph == normalizedTitle)
        }
    }

    private fun normalizeTitle(value: String): String =
        value
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun removeSiteSuffix(value: String): String =
        siteSuffixPatterns.fold(value) { title, pattern ->
            title.replace(pattern, "").trim()
        }

    private val siteSuffixPatterns = listOf(
        Regex("\\s*[|｜-]\\s*MBC\\s*뉴스\\s*$", RegexOption.IGNORE_CASE),
        Regex("\\s*[|｜-]\\s*SBS\\s*뉴스\\s*$", RegexOption.IGNORE_CASE),
        Regex("\\s*[|｜-]\\s*KBS\\s*뉴스\\s*$", RegexOption.IGNORE_CASE),
        Regex("\\s*[|｜-]\\s*YTN\\s*뉴스\\s*$", RegexOption.IGNORE_CASE),
        Regex("\\s*[|｜-]\\s*연합뉴스\\s*$"),
        Regex("\\s*[|｜-]\\s*네이버\\s*뉴스\\s*$")
    )

    private const val MIN_TITLE_LENGTH = 4
    private const val MAX_TITLE_LENGTH = 140
}

package com.example.fakenews.data.validation

import com.example.fakenews.data.model.NewsPress
import com.example.fakenews.util.ArticleValidationSeverity

data class NewsIntegrityPressSummary(
    val press: NewsPress,
    val articleCount: Int,
    val issueCount: Int,
    val warningCount: Int,
    val failCount: Int,
    val boilerplateParagraphCount: Int,
    val status: String
)

data class NewsIntegritySourceSummary(
    val press: NewsPress,
    val rssStatus: String,
    val crawlingStatus: String,
    val unavailable: Boolean,
    val rssArticleCount: Int,
    val crawlingArticleCount: Int
)

data class NewsIntegrityReport(
    val pressSummaries: List<NewsIntegrityPressSummary>,
    val sourceSummaries: List<NewsIntegritySourceSummary> = emptyList(),
    val issues: List<NewsIntegrityIssue>,
    val fallbackPresses: List<NewsPress> = emptyList(),
    val unavailablePresses: List<NewsPress> = emptyList(),
    val excludedNonArticleCount: Int = 0,
    val domainMismatchCount: Int = 0,
    val boilerplateParagraphCount: Int = 0,
    val duplicateNormalizedOriginalUrlCount: Int = 0,
    val cardDetailOriginalUrlMismatchCount: Int = 0,
    val missingPublishedAtCount: Int = 0,
    val publishedAtWithoutSourceCount: Int = 0
) {
    val failCount: Int
        get() = issues.count { issue -> issue.severity == ArticleValidationSeverity.ERROR }

    val warningCount: Int
        get() = issues.count { issue -> issue.severity == ArticleValidationSeverity.WARNING }

    fun toMarkdown(): String = buildString {
        appendLine("# News Integrity Report")
        appendLine()
        appendLine("- Status: ${if (failCount == 0) "PASS" else "FAIL"}")
        appendLine("- Failures: $failCount")
        appendLine("- Warnings: $warningCount")
        appendLine("- Mock fallback presses: ${fallbackPresses.joinToString { it.displayName }.ifBlank { "none" }}")
        appendLine("- Unavailable presses: ${unavailablePresses.joinToString { it.displayName }.ifBlank { "none" }}")
        appendLine("- originalUrl domain mismatch count: $domainMismatchCount")
        appendLine("- Non-article page visible count: $excludedNonArticleCount")
        appendLine("- Boilerplate paragraphs detected/removed: $boilerplateParagraphCount")
        appendLine("- Duplicate normalizedOriginalUrl count: $duplicateNormalizedOriginalUrlCount")
        appendLine("- Card-detail-originalUrl mismatch count: $cardDetailOriginalUrlMismatchCount")
        appendLine("- Missing publishedAt count: $missingPublishedAtCount")
        appendLine("- publishedAt without source count: $publishedAtWithoutSourceCount")
        appendLine()
        appendLine("## Collection Summary")
        appendLine()
        appendLine("| Press | RSS | Crawling | Unavailable |")
        appendLine("|---|---|---|---:|")
        sourceSummaries.forEach { summary ->
            appendLine(
                "| ${summary.press.displayName} | ${summary.rssStatus} | " +
                    "${summary.crawlingStatus} | ${if (summary.unavailable) "yes" else "no"} |"
            )
        }
        appendLine()
        appendLine("## Press Summary")
        appendLine()
        appendLine("| Press | Status | Articles | Warnings | Fails | Boilerplate |")
        appendLine("|---|---:|---:|---:|---:|---:|")
        pressSummaries.forEach { summary ->
            appendLine(
                "| ${summary.press.displayName} | ${summary.status} | ${summary.articleCount} | " +
                    "${summary.warningCount} | ${summary.failCount} | ${summary.boilerplateParagraphCount} |"
            )
        }
        appendLine()
        appendLine("## Issues")
        appendLine()
        if (issues.isEmpty()) {
            appendLine("No issues.")
        } else {
            issues.forEach { issue ->
                appendLine(
                    "- ${issue.severity} ${issue.press?.displayName ?: "GLOBAL"} ${issue.code}: ${issue.message}"
                )
                issue.articleId?.let { appendLine("  - id: `$it`") }
                issue.title?.let { appendLine("  - title: $it") }
                issue.originalUrl?.let { appendLine("  - originalUrl: $it") }
            }
        }
    }

    fun toJson(): String = buildString {
        appendLine("{")
        appendLine("  \"status\": \"${if (failCount == 0) "PASS" else "FAIL"}\",")
        appendLine("  \"failCount\": $failCount,")
        appendLine("  \"warningCount\": $warningCount,")
        appendLine("  \"excludedNonArticleCount\": $excludedNonArticleCount,")
        appendLine("  \"domainMismatchCount\": $domainMismatchCount,")
        appendLine("  \"boilerplateParagraphCount\": $boilerplateParagraphCount,")
        appendLine("  \"duplicateNormalizedOriginalUrlCount\": $duplicateNormalizedOriginalUrlCount,")
        appendLine("  \"cardDetailOriginalUrlMismatchCount\": $cardDetailOriginalUrlMismatchCount,")
        appendLine("  \"missingPublishedAtCount\": $missingPublishedAtCount,")
        appendLine("  \"publishedAtWithoutSourceCount\": $publishedAtWithoutSourceCount,")
        appendLine("  \"fallbackPresses\": [${fallbackPresses.joinToString { "\"${it.name}\"" }}],")
        appendLine("  \"unavailablePresses\": [${unavailablePresses.joinToString { "\"${it.name}\"" }}],")
        appendLine("  \"sourceSummaries\": [")
        sourceSummaries.forEachIndexed { index, summary ->
            append("    {")
            append("\"press\":\"${summary.press.name}\",")
            append("\"rssStatus\":\"${summary.rssStatus.escapeJson()}\",")
            append("\"crawlingStatus\":\"${summary.crawlingStatus.escapeJson()}\",")
            append("\"unavailable\":${summary.unavailable},")
            append("\"rssArticleCount\":${summary.rssArticleCount},")
            append("\"crawlingArticleCount\":${summary.crawlingArticleCount}")
            append("}")
            appendLine(if (index == sourceSummaries.lastIndex) "" else ",")
        }
        appendLine("  ],")
        appendLine("  \"pressSummaries\": [")
        pressSummaries.forEachIndexed { index, summary ->
            append("    {")
            append("\"press\":\"${summary.press.name}\",")
            append("\"status\":\"${summary.status}\",")
            append("\"articleCount\":${summary.articleCount},")
            append("\"warningCount\":${summary.warningCount},")
            append("\"failCount\":${summary.failCount},")
            append("\"boilerplateParagraphCount\":${summary.boilerplateParagraphCount}")
            append("}")
            appendLine(if (index == pressSummaries.lastIndex) "" else ",")
        }
        appendLine("  ],")
        appendLine("  \"issues\": [")
        issues.forEachIndexed { index, issue ->
            append("    {")
            append("\"press\":${issue.press?.let { "\"${it.name}\"" } ?: "null"},")
            append("\"severity\":\"${issue.severity}\",")
            append("\"code\":\"${issue.code.escapeJson()}\",")
            append("\"message\":\"${issue.message.escapeJson()}\",")
            append("\"articleId\":${issue.articleId?.let { "\"${it.escapeJson()}\"" } ?: "null"},")
            append("\"title\":${issue.title?.let { "\"${it.escapeJson()}\"" } ?: "null"},")
            append("\"originalUrl\":${issue.originalUrl?.let { "\"${it.escapeJson()}\"" } ?: "null"}")
            append("}")
            appendLine(if (index == issues.lastIndex) "" else ",")
        }
        appendLine("  ]")
        appendLine("}")
    }

    private fun String.escapeJson(): String =
        replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
}

package com.example.fakenews.util

import com.example.fakenews.data.model.ArticleType
import com.example.fakenews.data.model.NewsArticle
import com.example.fakenews.data.model.NewsPress
import com.example.fakenews.data.model.NewsSourceStatus
import com.example.fakenews.data.model.NewsSourceType
import com.example.fakenews.data.validation.NewsIntegrityIssue
import com.example.fakenews.data.validation.NewsIntegrityPressSummary
import com.example.fakenews.data.validation.NewsIntegrityReport
import com.example.fakenews.data.validation.NewsIntegritySourceSummary

object NewsIntegrityValidator {
    fun validate(
        articles: List<NewsArticle>,
        sourceStatuses: List<NewsSourceStatus> = emptyList()
    ): NewsIntegrityReport {
        val issues = mutableListOf<NewsIntegrityIssue>()
        val cleanedChecks = articles.map(ArticleQualityValidator::cleanAndValidate)
        val cleanedArticles = cleanedChecks.map { check -> check.article }
        val checksById = cleanedChecks.associateBy { check -> check.article.id }

        validatePressConfiguration(issues)
        validateDuplicates(cleanedArticles, issues)

        cleanedChecks.forEach { check ->
            val article = check.article
            check.result.reasons.forEach { reason ->
                val severity = if (check.result.isValid) {
                    ArticleValidationSeverity.WARNING.takeIf { reason.contains("warning", ignoreCase = true) }
                        ?: ArticleValidationSeverity.INFO
                } else {
                    ArticleValidationSeverity.ERROR
                }
                if (severity != ArticleValidationSeverity.INFO || reason.startsWith("boilerplate").not()) {
                    issues += issue(
                        article = article,
                        severity = check.result.severity,
                        code = reason.substringBefore(":"),
                        message = reason
                    )
                }
            }

            if (article.id != ArticleIdentity.idFor(article.press, article.originalUrl, article.title)) {
                issues += issue(
                    article = article,
                    severity = ArticleValidationSeverity.ERROR,
                    code = "id_not_original_url_based",
                    message = "Article id is not based on normalized originalUrl."
                )
            }
            if (ArticleTitleCleaner.removeDuplicatedTitleFromParagraphs(article.title, article.bodyParagraphs).size !=
                article.bodyParagraphs.size
            ) {
                issues += issue(
                    article = article,
                    severity = ArticleValidationSeverity.WARNING,
                    code = "title_duplicated_in_body",
                    message = "First body paragraph duplicates the title."
                )
            }
            if (article.articleType in excludedArticleTypes) {
                issues += issue(
                    article = article,
                    severity = ArticleValidationSeverity.ERROR,
                    code = "excluded_article_type_visible",
                    message = "Non-article type is present in display list: ${article.articleType}."
                )
            }
            if ((article.isVideoNews || !article.videoUrl.isNullOrBlank()) && check.result.isValid.not()) {
                issues += issue(
                    article = article,
                    severity = ArticleValidationSeverity.ERROR,
                    code = "video_news_invalid",
                    message = "Video news should stay displayable when it has valid article body and URL."
                )
            }
            if (!hasDisplayableBody(article)) {
                issues += issue(
                    article = article,
                    severity = ArticleValidationSeverity.WARNING,
                    code = "detail_body_not_displayable",
                    message = "Detail screen has no bodyParagraphs/content/summary fallback."
                )
            }
            if (article.publishedAt != null && article.publishedAtSource.isNullOrBlank()) {
                issues += issue(
                    article = article,
                    severity = ArticleValidationSeverity.WARNING,
                    code = "published_at_without_source",
                    message = "publishedAt exists but its original source is not recorded."
                )
            }
        }

        NewsPress.articlePresses().forEach { press ->
            val count = cleanedArticles.count { article -> article.press == press }
            if (count > NewsFilter.MAX_ARTICLES_PER_PRESS) {
                issues += NewsIntegrityIssue(
                    press = press,
                    severity = ArticleValidationSeverity.ERROR,
                    code = "too_many_articles_for_press",
                    message = "Press has $count articles; max is ${NewsFilter.MAX_ARTICLES_PER_PRESS}."
                )
            }
            if (count == 0) {
                issues += NewsIntegrityIssue(
                    press = press,
                    severity = ArticleValidationSeverity.WARNING,
                    code = "no_articles_for_press",
                    message = "No displayable article was provided for this press."
                )
            }
        }

        val pressSummaries = NewsPress.articlePresses().map { press ->
            val pressIssues = issues.filter { issue -> issue.press == press }
            val failCount = pressIssues.count { issue -> issue.severity == ArticleValidationSeverity.ERROR }
            val warningCount = pressIssues.count { issue -> issue.severity == ArticleValidationSeverity.WARNING }
            NewsIntegrityPressSummary(
                press = press,
                articleCount = cleanedArticles.count { article -> article.press == press },
                issueCount = pressIssues.size,
                warningCount = warningCount,
                failCount = failCount,
                boilerplateParagraphCount = cleanedArticles
                    .filter { article -> article.press == press }
                    .sumOf { article -> checksById[article.id]?.result?.boilerplateParagraphCount ?: 0 },
                status = when {
                    failCount > 0 -> "FAIL"
                    warningCount > 0 -> "WARNING"
                    else -> "PASS"
                }
            )
        }

        return NewsIntegrityReport(
            pressSummaries = pressSummaries,
            sourceSummaries = sourceSummaries(sourceStatuses),
            issues = issues,
            fallbackPresses = sourceStatuses
                .filter { status -> status.sourceType == NewsSourceType.MOCK && status.success }
                .map { status -> status.press },
            unavailablePresses = unavailablePresses(sourceStatuses),
            excludedNonArticleCount = issues.count { issue -> issue.code.contains("article_type") },
            domainMismatchCount = issues.count { issue -> issue.code == "domain_mismatch" },
            boilerplateParagraphCount = cleanedChecks.sumOf { check -> check.result.boilerplateParagraphCount },
            duplicateNormalizedOriginalUrlCount = issues.count { issue ->
                issue.code == "duplicate_normalized_original_url"
            },
            cardDetailOriginalUrlMismatchCount = issues.count { issue ->
                issue.code == "id_not_original_url_based"
            },
            missingPublishedAtCount = cleanedArticles.count { article -> article.publishedAt == null },
            publishedAtWithoutSourceCount = issues.count { issue -> issue.code == "published_at_without_source" }
        )
    }

    private fun validatePressConfiguration(issues: MutableList<NewsIntegrityIssue>) {
        if (NewsPress.YTN !in NewsPress.articlePresses()) {
            issues += NewsIntegrityIssue(
                press = NewsPress.YTN,
                severity = ArticleValidationSeverity.ERROR,
                code = "ytn_missing",
                message = "YTN is not configured as an article press."
            )
        }
        NewsPress.articlePresses().forEach { press ->
            if (PressDomainMatcher.allowedDomains(press).isEmpty()) {
                issues += NewsIntegrityIssue(
                    press = press,
                    severity = ArticleValidationSeverity.ERROR,
                    code = "allowed_domains_missing",
                    message = "Allowed domain configuration is missing."
                )
            }
        }
    }

    private fun validateDuplicates(
        articles: List<NewsArticle>,
        issues: MutableList<NewsIntegrityIssue>
    ) {
        articles
            .groupBy { article -> UrlNormalizer.normalize(article.originalUrl) }
            .filterKeys { normalizedUrl -> normalizedUrl.isNotBlank() }
            .filterValues { duplicates -> duplicates.size > 1 }
            .forEach { (normalizedUrl, duplicates) ->
                duplicates.forEach { article ->
                    issues += issue(
                        article = article,
                        severity = ArticleValidationSeverity.ERROR,
                        code = "duplicate_normalized_original_url",
                        message = "Duplicate normalizedOriginalUrl: $normalizedUrl"
                    )
                }
            }

    }

    private fun sourceSummaries(sourceStatuses: List<NewsSourceStatus>): List<NewsIntegritySourceSummary> =
        NewsPress.articlePresses().map { press ->
            val pressStatuses = sourceStatuses.filter { status -> status.press == press }
            val rssStatuses = pressStatuses.filter { status -> status.sourceType == NewsSourceType.RSS }
            val crawlingStatuses = pressStatuses.filter { status -> status.sourceType == NewsSourceType.HTML_CRAWLING }
            NewsIntegritySourceSummary(
                press = press,
                rssStatus = statusLabel(rssStatuses),
                crawlingStatus = statusLabel(crawlingStatuses),
                unavailable = pressStatuses.any { status ->
                    status.sourceType == NewsSourceType.NOT_FOUND && !status.success
                },
                rssArticleCount = rssStatuses.filter { status -> status.success }.sumOf { status -> status.articleCount },
                crawlingArticleCount = crawlingStatuses.filter { status -> status.success }.sumOf { status -> status.articleCount }
            )
        }

    private fun unavailablePresses(sourceStatuses: List<NewsSourceStatus>): List<NewsPress> =
        sourceSummaries(sourceStatuses)
            .filter { summary -> summary.unavailable }
            .map { summary -> summary.press }

    private fun statusLabel(statuses: List<NewsSourceStatus>): String {
        if (statuses.isEmpty()) return "not run"
        val successfulCount = statuses
            .filter { status -> status.success }
            .sumOf { status -> status.articleCount }
        if (successfulCount > 0) {
            val successMessage = statuses
                .firstOrNull { status -> status.success }
                ?.message
                ?.takeIf { message -> message.isNotBlank() }
            return if (successMessage.isNullOrBlank()) {
                "success ($successfulCount)"
            } else {
                "success ($successfulCount): $successMessage"
            }
        }
        val message = statuses
            .mapNotNull { status -> status.message?.takeIf { it.isNotBlank() } }
            .firstOrNull()
        return if (message.isNullOrBlank()) {
            "failed"
        } else {
            "failed: $message"
        }
    }

    private fun issue(
        article: NewsArticle,
        severity: ArticleValidationSeverity,
        code: String,
        message: String
    ): NewsIntegrityIssue =
        NewsIntegrityIssue(
            press = article.press,
            severity = severity,
            code = code,
            message = message,
            articleId = article.id,
            title = article.title,
            originalUrl = article.originalUrl
        )

    private fun hasDisplayableBody(article: NewsArticle): Boolean =
        article.bodyParagraphs.any { paragraph -> paragraph.isNotBlank() } ||
            article.content.isNotBlank() ||
            article.summary.isNotBlank()

    private val excludedArticleTypes = setOf(
        ArticleType.LIVE_STREAM,
        ArticleType.PROGRAM_PAGE,
        ArticleType.SCHEDULE_PAGE,
        ArticleType.REPLAY_PAGE,
        ArticleType.OPINION_OR_REACTION_WIDGET,
        ArticleType.UNKNOWN_NON_ARTICLE
    )
}

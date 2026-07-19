package com.example.fakenews.util

import com.example.fakenews.data.model.ArticleType
import com.example.fakenews.data.model.NewsArticle
import com.example.fakenews.data.model.NewsPress

enum class ArticleValidationSeverity {
    INFO,
    WARNING,
    ERROR
}

data class ArticleValidationResult(
    val isValid: Boolean,
    val reasons: List<String>,
    val severity: ArticleValidationSeverity = ArticleValidationSeverity.INFO,
    val boilerplateParagraphCount: Int = 0
)

data class ArticleQualityCheck(
    val article: NewsArticle,
    val result: ArticleValidationResult
)

object ArticleQualityValidator {
    fun cleanAndValidate(article: NewsArticle): ArticleQualityCheck {
        val cleanedArticle = cleanArticle(article)
        return ArticleQualityCheck(
            article = cleanedArticle,
            result = validate(cleanedArticle)
        )
    }

    fun isDisplayableNewsArticle(article: NewsArticle): Boolean =
        validate(cleanArticle(article)).isValid

    fun validate(article: NewsArticle): ArticleValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val info = mutableListOf<String>()

        val title = article.title.trim()
        val originalUrl = UrlNormalizer.decodeHtmlEntities(article.originalUrl).trim()
        val rawParagraphs = article.bodyParagraphs
        val meaningfulParagraphs = meaningfulParagraphs(article)
        val boilerplateCount = rawParagraphs.count(ArticleBoilerplateCleaner::isBoilerplateParagraph)
        val articleType = ArticleTypeClassifier.classify(
            title = article.title,
            originalUrl = originalUrl,
            bodyText = bodyText(article),
            metaText = article.summary,
            videoUrl = article.videoUrl
        )

        if (title.isBlank()) {
            errors += "title_empty"
        }
        if (title.contains('\n') || title.contains('\r')) {
            warnings += "title_contains_line_break"
        }
        if (title.length > MAX_TITLE_LENGTH) {
            warnings += "title_too_long"
        }
        if (looksLikeTitleContainsBody(title)) {
            warnings += "title_may_contain_body"
        }
        if (isPressNameOnlyTitle(article.press, title)) {
            errors += "title_not_article_like"
        }

        if (originalUrl.isBlank()) {
            errors += "original_url_empty"
        } else if (!PressDomainMatcher.isUrlAllowedForPress(originalUrl, article.press)) {
            errors += "domain_mismatch"
        } else if (!ArticleUrlPolicy.isValidArticleUrl(article.press, originalUrl)) {
            errors += "article_url_policy_mismatch"
        }

        if (!ArticleTypeClassifier.isNewsArticle(articleType)) {
            errors += "non_article_type:${articleType.name}"
        }

        if (!hasMeaningfulBody(article, meaningfulParagraphs)) {
            errors += "body_missing_or_too_short"
        }

        if (boilerplateCount > 0) {
            info += "boilerplate_removed_or_detected:$boilerplateCount"
        }
        if (rawParagraphs.isNotEmpty() && boilerplateCount == rawParagraphs.size) {
            errors += "body_only_boilerplate"
        } else if (rawParagraphs.isNotEmpty() && boilerplateCount.toDouble() / rawParagraphs.size > 0.5) {
            warnings += "high_boilerplate_ratio"
        }

        if (firstBodyParagraphDuplicatesTitle(article.title, meaningfulParagraphs)) {
            warnings += "title_duplicated_in_body"
        }

        val reasons = errors + warnings + info
        val severity = when {
            errors.isNotEmpty() -> ArticleValidationSeverity.ERROR
            warnings.isNotEmpty() -> ArticleValidationSeverity.WARNING
            else -> ArticleValidationSeverity.INFO
        }

        return ArticleValidationResult(
            isValid = errors.isEmpty(),
            reasons = reasons,
            severity = severity,
            boilerplateParagraphCount = boilerplateCount
        )
    }

    fun cleanArticle(article: NewsArticle): NewsArticle {
        val decodedOriginalUrl = UrlNormalizer.decodeHtmlEntities(article.originalUrl).trim()
        val cleanedTitle = ArticleTitleCleaner.cleanTitle(article.title)
        val rawParagraphs = bodyParagraphCandidates(article)
        val cleanedParagraphs = ArticleTitleCleaner.removeDuplicatedTitleFromParagraphs(
            title = cleanedTitle,
            paragraphs = rawParagraphs
                .map(ArticleBoilerplateCleaner::cleanParagraph)
                .filter { paragraph -> paragraph.isNotBlank() }
                .filterNot(ArticleBoilerplateCleaner::isBoilerplateParagraph)
        ).distinct()
        val cleanedSummary = ArticleBoilerplateCleaner.cleanParagraph(article.summary)
        val cleanedContent = cleanedParagraphs.joinToString("\n\n")
            .ifBlank { ArticleBoilerplateCleaner.cleanParagraph(article.content) }
        val articleType = ArticleTypeClassifier.classify(
            title = cleanedTitle,
            originalUrl = decodedOriginalUrl,
            bodyText = cleanedParagraphs.joinToString(" ").ifBlank { cleanedContent },
            metaText = cleanedSummary,
            videoUrl = article.videoUrl
        )

        return article.copy(
            id = ArticleIdentity.idFor(
                press = article.press,
                originalUrl = decodedOriginalUrl,
                fallbackTitle = cleanedTitle
            ),
            title = cleanedTitle,
            summary = cleanedSummary,
            content = cleanedContent.ifBlank { cleanedSummary },
            bodyParagraphs = cleanedParagraphs,
            originalUrl = decodedOriginalUrl,
            articleType = articleType,
            isVideoNews = article.isVideoNews ||
                !article.videoUrl.isNullOrBlank() ||
                articleType == ArticleType.VIDEO_NEWS_ARTICLE
        )
    }

    private fun bodyParagraphCandidates(article: NewsArticle): List<String> =
        article.bodyParagraphs
            .ifEmpty { ArticleTextExtractor.splitParagraphs(article.content) }
            .ifEmpty { ArticleTextExtractor.splitParagraphs(article.summary) }

    private fun meaningfulParagraphs(article: NewsArticle): List<String> =
        bodyParagraphCandidates(article)
            .map(ArticleBoilerplateCleaner::cleanParagraph)
            .filter { paragraph -> paragraph.isNotBlank() }
            .filterNot(ArticleBoilerplateCleaner::isBoilerplateParagraph)

    private fun hasMeaningfulBody(
        article: NewsArticle,
        meaningfulParagraphs: List<String>
    ): Boolean =
        meaningfulParagraphs.any { paragraph -> paragraph.length >= MIN_PARAGRAPH_LENGTH } ||
            ArticleBoilerplateCleaner.cleanParagraph(article.content).length >= MIN_CONTENT_LENGTH ||
            ArticleBoilerplateCleaner.cleanParagraph(article.summary).length >= MIN_SUMMARY_LENGTH

    private fun bodyText(article: NewsArticle): String =
        article.bodyParagraphs.joinToString(" ")
            .ifBlank { article.content }
            .ifBlank { article.summary }

    private fun looksLikeTitleContainsBody(title: String): Boolean =
        title.length > BODY_LIKE_TITLE_LENGTH ||
            title.count { char -> char == '.' || char == '。' || char == '다' } >= BODY_LIKE_SENTENCE_MARKERS

    private fun isPressNameOnlyTitle(
        press: NewsPress,
        title: String
    ): Boolean {
        val compactTitle = title
            .replace(Regex("\\s+"), "")
            .uppercase()
        val compactPressName = press.displayName
            .replace(Regex("\\s+"), "")
            .uppercase()
        return compactTitle == compactPressName ||
            compactTitle == "${compactPressName}뉴스" ||
            compactTitle == "${compactPressName}NEWS"
    }

    private fun firstBodyParagraphDuplicatesTitle(
        title: String,
        paragraphs: List<String>
    ): Boolean {
        val first = paragraphs.firstOrNull() ?: return false
        val normalizedTitle = TitleNormalizer.normalize(title)
        val normalizedFirst = TitleNormalizer.normalize(first)
        return normalizedTitle.isNotBlank() && normalizedTitle == normalizedFirst
    }

    private const val MAX_TITLE_LENGTH = 140
    private const val BODY_LIKE_TITLE_LENGTH = 120
    private const val BODY_LIKE_SENTENCE_MARKERS = 5
    private const val MIN_PARAGRAPH_LENGTH = 12
    private const val MIN_CONTENT_LENGTH = 80
    private const val MIN_SUMMARY_LENGTH = 50
}

package com.example.fakenews.data.remote

import com.example.fakenews.data.model.NewsArticle
import com.example.fakenews.util.ArticleQualityCheck
import com.example.fakenews.util.ArticleTitleCleaner
import com.example.fakenews.util.ArticleUrlPolicy
import java.util.Locale

data class YtnPipelineDebugItem(
    val index: Int,
    val feedUrl: String,
    val rawTitle: String,
    val rawLink: String,
    val rawPubDate: String,
    val rawDescriptionLength: Int,
    var resolvedUrl: String? = null,
    var resolveSuccess: Boolean = false,
    var resolveFailureReason: String? = null,
    var host: String = "",
    var path: String = "",
    var allowedDomainPass: Boolean = false,
    var articleUrlPolicyPass: Boolean = false,
    var excludeReason: String? = null,
    var cleanedTitle: String = "",
    var titleValid: Boolean = false,
    var titleInvalidReason: String? = null,
    var detailFetchSuccess: Boolean? = null,
    var extractedTitle: String? = null,
    var paragraphCount: Int? = null,
    var contentLength: Int? = null,
    var boilerplateRemovedCount: Int? = null,
    var validationPass: Boolean? = null,
    var validationReasons: List<String> = emptyList(),
    var finalDisplay: Boolean = false,
    var finalId: String? = null,
    var finalTitle: String? = null,
    var finalPublishedAt: Long? = null,
    var finalBodyPreviewLength: Int? = null,
    var cardDetailOriginalUrlMatch: Boolean? = null
) {
    val effectiveExcludeReason: String?
        get() = when {
            finalDisplay -> null
            resolveSuccess.not() -> resolveFailureReason ?: "resolve_failed"
            allowedDomainPass.not() -> "domain_mismatch"
            articleUrlPolicyPass.not() -> excludeReason ?: "article_url_policy_mismatch"
            titleValid.not() -> titleInvalidReason ?: "title_not_article_like"
            validationPass == false -> validationReasons.joinToString("|").ifBlank { "validation_failed" }
            else -> excludeReason
    }
}

data class YtnRepositoryDebugTrace(
    val collectedArticleCount: Int,
    val displayableArticleCount: Int,
    val afterPressFilterCount: Int,
    val afterDedupCount: Int,
    val afterKeywordCount: Int,
    val finalRepositoryArticleCount: Int,
    val failedPress: Boolean,
    val keywords: List<String>
)

data class YtnUiStateDebugTrace(
    val selected: Boolean,
    val uiArticleCount: Int,
    val failedPress: Boolean,
    val keywords: List<String>,
    val emptyMessage: String?,
    val infoMessage: String?
)

data class YtnPipelineDebugSnapshot(
    val feedUrls: List<String>,
    val items: List<YtnPipelineDebugItem>,
    val repositoryTrace: YtnRepositoryDebugTrace? = null,
    val uiStateTrace: YtnUiStateDebugTrace? = null
) {
    val rawItemCount: Int
        get() = items.size
    val resolveSuccessCount: Int
        get() = items.count { item -> item.resolveSuccess }
    val resolveFailureCount: Int
        get() = items.count { item -> !item.resolveSuccess }
    val domainMismatchCount: Int
        get() = items.count { item -> item.resolveSuccess && !item.allowedDomainPass }
    val articleUrlPolicyMismatchCount: Int
        get() = items.count { item -> item.allowedDomainPass && !item.articleUrlPolicyPass }
    val titleNotArticleLikeCount: Int
        get() = items.count { item -> item.articleUrlPolicyPass && !item.titleValid }
    val titleValidCount: Int
        get() = items.count { item -> item.titleValid }
    val rssFinalDisplayItemCount: Int
        get() = items.count { item -> item.finalDisplay }
    val finalDisplayArticleCount: Int
        get() = repositoryTrace?.finalRepositoryArticleCount ?: rssFinalDisplayItemCount

    fun toMarkdownSection(): String {
        val finalUrls = items
            .filter { item -> item.finalDisplay }
            .mapNotNull { item -> item.resolvedUrl }
            .toSet()
        val excludedItems = items
            .filter { item -> !item.finalDisplay }
            .filterNot { item -> item.resolvedUrl != null && item.resolvedUrl in finalUrls }

        return buildString {
            appendLine()
            appendLine("## YTN Debug Section")
            appendLine()
            appendLine("- YTN raw RSS item count: $rawItemCount")
            appendLine("- YTN resolve success count: $resolveSuccessCount")
            appendLine("- YTN resolve failure count: $resolveFailureCount")
            appendLine("- YTN domain mismatch count: $domainMismatchCount")
            appendLine("- YTN articleUrlPolicy mismatch count: $articleUrlPolicyMismatchCount")
            appendLine("- YTN title_not_article_like count: $titleNotArticleLikeCount")
            appendLine("- YTN title valid/invalid count: $titleValidCount/${rawItemCount - titleValidCount}")
            appendLine("- YTN RSS final display item count: $rssFinalDisplayItemCount")
            appendLine("- YTN final display article count: $finalDisplayArticleCount")
            appendLine("- YTN repository final article count: ${repositoryTrace?.finalRepositoryArticleCount?.toString() ?: "not recorded"}")
            appendLine("- YTN UI state article count: ${uiStateTrace?.uiArticleCount?.toString() ?: "not recorded"}")
            appendLine()
            appendLine("### YTN Feed URLs")
            feedUrls.distinct().forEach { feedUrl ->
                appendLine("- $feedUrl")
            }
            appendLine()
            appendLine("### Runtime Stage Counts")
            if (repositoryTrace == null && uiStateTrace == null) {
                appendLine("- not recorded")
            } else {
                repositoryTrace?.let { trace ->
                    appendLine(
                        "- repository collected=${trace.collectedArticleCount} displayable=${trace.displayableArticleCount} " +
                            "afterPress=${trace.afterPressFilterCount} afterDedup=${trace.afterDedupCount} " +
                            "afterKeyword=${trace.afterKeywordCount} final=${trace.finalRepositoryArticleCount} " +
                            "failedPress=${trace.failedPress}"
                    )
                    appendLine("- repository keywords=${trace.keywords.joinToString().ifBlank { "none" }}")
                }
                uiStateTrace?.let { trace ->
                    appendLine(
                        "- uiState selected=${trace.selected} articleCount=${trace.uiArticleCount} " +
                            "failedPress=${trace.failedPress}"
                    )
                    appendLine("- uiState keywords=${trace.keywords.joinToString().ifBlank { "none" }}")
                    appendLine("- uiState emptyMessage=${trace.emptyMessage.orEmpty()}")
                    appendLine("- uiState infoMessage=${trace.infoMessage.orEmpty()}")
                }
            }
            appendLine()
            appendLine("### Excluded Items")
            if (excludedItems.isEmpty()) {
                appendLine("- none")
            } else {
                excludedItems.forEach { item ->
                    appendLine(
                        "- #${item.index} reason=${item.effectiveExcludeReason.orEmpty()} " +
                            "rawTitle=${item.rawTitle.markdownCell()} resolvedUrl=${item.resolvedUrl.orEmpty()}"
                    )
                    appendLine(
                        "  - rawLink=${item.rawLink} host=${item.host} path=${item.path} " +
                            "allowedDomain=${item.allowedDomainPass} policy=${item.articleUrlPolicyPass} " +
                            "titleValid=${item.titleValid}"
                    )
                }
            }
            appendLine()
            appendLine("### Final Display Items")
            val finalItems = items.filter { item -> item.finalDisplay }
            if (finalItems.isEmpty()) {
                appendLine("- none")
            } else {
                finalItems.forEach { item ->
                    appendLine(
                        "- id=${item.finalId.orEmpty()} title=${item.finalTitle.orEmpty().markdownCell()} " +
                            "originalUrl=${item.resolvedUrl.orEmpty()} publishedAt=${item.finalPublishedAt?.toString().orEmpty()} " +
                            "bodyPreviewLength=${item.finalBodyPreviewLength ?: 0} " +
                            "cardDetailOriginalUrlMatch=${item.cardDetailOriginalUrlMatch ?: false}"
                    )
                }
            }
            appendLine()
            appendLine("### Raw Item Trace")
            appendLine(
                "| # | raw title | raw link | pubDate | description length | resolvedUrl | resolve | " +
                    "host | path | domain | policy | cleanedTitle | titleValid | detailFetch | " +
                    "extractedTitle | paragraphs | contentLength | boilerplateRemoved | final |"
            )
            appendLine("|---:|---|---|---|---:|---|---:|---|---|---:|---:|---|---:|---:|---|---:|---:|---:|---:|")
            items.forEach { item ->
                appendLine(
                    "| ${item.index} | ${item.rawTitle.markdownCell()} | ${item.rawLink.markdownCell()} | " +
                        "${item.rawPubDate.markdownCell()} | ${item.rawDescriptionLength} | " +
                        "${item.resolvedUrl.orEmpty().markdownCell()} | ${item.resolveSuccess.yesNo()} | " +
                        "${item.host.markdownCell()} | ${item.path.markdownCell()} | " +
                        "${item.allowedDomainPass.yesNo()} | ${item.articleUrlPolicyPass.yesNo()} | " +
                        "${item.cleanedTitle.markdownCell()} | ${item.titleValid.yesNo()} | " +
                        "${(item.detailFetchSuccess == true).yesNo()} | " +
                        "${item.extractedTitle.orEmpty().markdownCell()} | ${item.paragraphCount ?: 0} | " +
                        "${item.contentLength ?: 0} | ${item.boilerplateRemovedCount ?: 0} | " +
                        "${item.finalDisplay.yesNo()} |"
                )
            }
        }
    }

    private fun Boolean.yesNo(): String =
        if (this) "yes" else "no"

    private fun String.markdownCell(): String =
        replace("|", "\\|")
            .replace("\n", " ")
            .replace("\r", " ")
            .take(160)
}

class YtnPipelineTraceCollector {
    private val feedUrls = mutableListOf<String>()
    private val items = mutableListOf<YtnPipelineDebugItem>()
    private var repositoryTrace: YtnRepositoryDebugTrace? = null
    private var uiStateTrace: YtnUiStateDebugTrace? = null

    @Synchronized
    fun startFeed(feedUrl: String) {
        feedUrls += feedUrl
    }

    @Synchronized
    fun beginItem(
        feedUrl: String,
        rawTitle: String,
        rawLink: String,
        rawPubDate: String,
        rawDescriptionLength: Int
    ): YtnPipelineDebugItem {
        val item = YtnPipelineDebugItem(
            index = items.size + 1,
            feedUrl = feedUrl,
            rawTitle = rawTitle,
            rawLink = rawLink,
            rawPubDate = rawPubDate,
            rawDescriptionLength = rawDescriptionLength
        )
        items += item
        logItem("raw", item)
        return item
    }

    @Synchronized
    fun recordResolve(
        item: YtnPipelineDebugItem,
        resolution: GoogleNewsOriginalUrlResolution
    ) {
        item.resolvedUrl = resolution.resolvedUrl
        item.resolveSuccess = resolution.resolveSuccess
        item.resolveFailureReason = resolution.resolveFailureReason
        logItem("resolve", item)
    }

    @Synchronized
    fun recordUrlPolicy(
        item: YtnPipelineDebugItem,
        inspection: ArticleUrlPolicy.Inspection
    ) {
        item.host = inspection.host
        item.path = inspection.path
        item.allowedDomainPass = inspection.allowedDomainPass
        item.articleUrlPolicyPass = inspection.articleUrlPolicyPass
        item.excludeReason = inspection.excludeReason
        logItem("url_policy", item)
    }

    @Synchronized
    fun recordTitle(
        item: YtnPipelineDebugItem,
        cleanedTitle: String,
        titleValid: Boolean,
        titleInvalidReason: String?
    ) {
        item.cleanedTitle = cleanedTitle
        item.titleValid = titleValid
        item.titleInvalidReason = titleInvalidReason
        if (!titleValid) item.excludeReason = titleInvalidReason
        logItem("title", item)
    }

    @Synchronized
    fun recordDetail(
        originalUrl: String,
        success: Boolean,
        extractedTitle: String?,
        paragraphCount: Int,
        contentLength: Int,
        boilerplateRemovedCount: Int
    ) {
        items.filter { item -> item.resolvedUrl == originalUrl }.forEach { item ->
            item.detailFetchSuccess = success
            item.extractedTitle = extractedTitle
            item.paragraphCount = paragraphCount
            item.contentLength = contentLength
            item.boilerplateRemovedCount = boilerplateRemovedCount
            logItem("detail", item)
        }
    }

    @Synchronized
    fun recordValidation(
        article: NewsArticle,
        check: ArticleQualityCheck,
        finalDisplay: Boolean
    ) {
        items.filter { item -> item.resolvedUrl == article.originalUrl }.forEach { item ->
            item.validationPass = check.result.isValid
            item.validationReasons = check.result.reasons
            if (!check.result.isValid) item.excludeReason = check.result.reasons.joinToString("|")
            if (finalDisplay) {
                item.finalDisplay = true
                item.finalId = check.article.id
                item.finalTitle = check.article.title
                item.finalPublishedAt = check.article.publishedAt
                item.finalBodyPreviewLength = check.article.content.take(240).length
                item.cardDetailOriginalUrlMatch = check.article.originalUrl == article.originalUrl
            }
            logItem(if (finalDisplay) "final" else "validation_excluded", item)
        }
    }

    @Synchronized
    fun recordRepository(trace: YtnRepositoryDebugTrace) {
        repositoryTrace = trace
        NewsFetchLogger.logYtnRepository(
            "collected=${trace.collectedArticleCount} displayable=${trace.displayableArticleCount} " +
                "afterPress=${trace.afterPressFilterCount} afterDedup=${trace.afterDedupCount} " +
                "afterKeyword=${trace.afterKeywordCount} final=${trace.finalRepositoryArticleCount} " +
                "failedPress=${trace.failedPress} keywords=${trace.keywords.joinToString()}"
        )
    }

    @Synchronized
    fun recordUiState(trace: YtnUiStateDebugTrace) {
        uiStateTrace = trace
        NewsFetchLogger.logYtnUiState(
            "selected=${trace.selected} articleCount=${trace.uiArticleCount} failedPress=${trace.failedPress} " +
                "keywords=${trace.keywords.joinToString()} emptyMessage=${trace.emptyMessage.orEmpty()} " +
                "infoMessage=${trace.infoMessage.orEmpty()}"
        )
    }

    @Synchronized
    fun snapshot(): YtnPipelineDebugSnapshot =
        YtnPipelineDebugSnapshot(
            feedUrls = feedUrls.toList(),
            items = items.map { item -> item.copy(validationReasons = item.validationReasons.toList()) },
            repositoryTrace = repositoryTrace,
            uiStateTrace = uiStateTrace
        )

    private fun logItem(
        stage: String,
        item: YtnPipelineDebugItem
    ) {
        NewsFetchLogger.logValidation(
            "ytn_pipeline stage=$stage index=${item.index} rawTitle=${item.rawTitle} " +
                "rawLink=${item.rawLink} resolvedUrl=${item.resolvedUrl.orEmpty()} " +
                "resolveSuccess=${item.resolveSuccess} allowedDomain=${item.allowedDomainPass} " +
                "policy=${item.articleUrlPolicyPass} titleValid=${item.titleValid} " +
                "final=${item.finalDisplay} reason=${item.effectiveExcludeReason.orEmpty()}"
        )
        NewsFetchLogger.logYtnRuntime(
            "stage=$stage index=${item.index} rawTitle=${item.rawTitle} " +
                "rawLink=${item.rawLink} resolvedUrl=${item.resolvedUrl.orEmpty()} " +
                "resolveSuccess=${item.resolveSuccess} allowedDomain=${item.allowedDomainPass} " +
                "policy=${item.articleUrlPolicyPass} titleValid=${item.titleValid} " +
                "final=${item.finalDisplay} reason=${item.effectiveExcludeReason.orEmpty()}"
        )
    }
}

object YtnPipelineDebugStore {
    @Volatile
    private var collector: YtnPipelineTraceCollector? = null

    @Synchronized
    fun start(): YtnPipelineTraceCollector =
        YtnPipelineTraceCollector().also { collector = it }

    @Synchronized
    fun current(): YtnPipelineTraceCollector? = collector

    @Synchronized
    fun latestSnapshot(): YtnPipelineDebugSnapshot =
        collector?.snapshot() ?: YtnPipelineDebugSnapshot(emptyList(), emptyList())

    @Synchronized
    fun recordRepository(trace: YtnRepositoryDebugTrace) {
        collector?.recordRepository(trace)
    }

    @Synchronized
    fun recordUiState(trace: YtnUiStateDebugTrace) {
        collector?.recordUiState(trace)
    }
}

object YtnTitlePolicy {
    fun validateTitle(rawTitle: String): Pair<Boolean, String?> {
        val cleanedTitle = ArticleTitleCleaner.cleanTitle(rawTitle)
        if (cleanedTitle.isBlank()) return false to "title_empty"
        val compactTitle = cleanedTitle
            .replace(Regex("\\s+"), "")
            .uppercase(Locale.ROOT)
        return if (compactTitle == "YTN" || compactTitle == "YTN뉴스" || compactTitle == "YTNNEWS") {
            false to "title_not_article_like"
        } else {
            true to null
        }
    }
}

fun ytnDebugSectionForLatestPipeline(): String =
    YtnPipelineDebugStore.latestSnapshot().toMarkdownSection()

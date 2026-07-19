package com.example.fakenews.data.remote

import com.example.fakenews.data.model.NewsArticle
import com.example.fakenews.data.model.NewsDataSourceResult
import com.example.fakenews.data.model.NewsPress
import com.example.fakenews.data.model.NewsSourceType
import com.example.fakenews.util.ArticleUrlPolicy
import com.example.fakenews.util.UrlNormalizer
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

class RssNewsDataSource(
    private val feedConfigs: List<RssFeedConfig> = RssFeedConfig.defaultFeeds,
    private val fetcher: RssFeedFetcher = OkHttpRssFeedFetcher(),
    private val originalUrlResolver: OriginalUrlResolver = HttpRedirectOriginalUrlResolver(),
    private val detailDataSource: ArticleDetailRemoteDataSource = ArticleDetailRemoteDataSource(),
    override val sourceName: String = "RSS",
    private val fetchBudgetMs: Long? = null,
    private val enableDetailEnrichment: Boolean = true,
    private val maxFeedsPerPress: Int = Int.MAX_VALUE,
    private val maxItemsPerFeed: Int = Int.MAX_VALUE
) : NewsDataSource {
    override val sourceType: NewsSourceType = NewsSourceType.RSS

    override suspend fun fetch(
        press: NewsPress,
        keywords: List<String>
    ): NewsDataSourceResult {
        val normalizedKeywords = keywords.normalizedSearchKeywords()
        val matchingConfigs = feedConfigs.filter { config -> config.press == press }
        val isKeywordGoogleNewsSearch = normalizedKeywords.isNotEmpty() &&
            matchingConfigs.any { config -> config.originalUrlMode == RssOriginalUrlMode.GOOGLE_NEWS }
        val configs = matchingConfigs.take(effectiveMaxFeedsPerPress(isKeywordGoogleNewsSearch))
        if (configs.isEmpty()) {
            return NewsDataSourceResult(
                articles = emptyList(),
                success = false,
                message = "RSS feed config not found"
            )
        }

        val ytnTraceCollector = if (press == NewsPress.YTN) {
            YtnPipelineDebugStore.start()
        } else {
            null
        }
        val failureMessages = mutableListOf<String>()
        val sourceStartedAt = System.currentTimeMillis()
        val sourceBudgetMs = effectiveFetchBudgetMs(isKeywordGoogleNewsSearch)
        val keywordSearchArticles = mutableListOf<NewsArticle>()

        configs.forEach configLoop@ { config ->
            val isKeywordGoogleNewsConfig = normalizedKeywords.isNotEmpty() &&
                config.originalUrlMode == RssOriginalUrlMode.GOOGLE_NEWS
            val requestVariants = config.requestVariantsFor(press, normalizedKeywords)
            requestVariants.forEach variantLoop@ { requestVariant ->
                if (
                    isKeywordGoogleNewsConfig &&
                    keywordSearchArticles.size >= NewsFetchConfig.SEARCH_GOOGLE_NEWS_RAW_CANDIDATE_LIMIT_PER_PRESS
                ) {
                    return@variantLoop
                }

                val requestUrl = requestVariant.url
                val remainingBeforeFeedMs = remainingBudgetMs(sourceStartedAt, sourceBudgetMs)
                if (remainingBeforeFeedMs != null && remainingBeforeFeedMs <= 0L) {
                    failureMessages += "$sourceName timeout before ${NewsFetchLogger.safeUrlForLog(requestUrl)}"
                    return@variantLoop
                }
                val requestTimeoutMs = requestTimeoutMs(
                    remainingBeforeFeedMs = remainingBeforeFeedMs,
                    isKeywordGoogleNewsSearch = isKeywordGoogleNewsConfig
                )
                var parsedItemCount = 0
                var responseBytes = 0
                var keywordQueryDiagnostics = KeywordGoogleNewsQueryDiagnostics()
                val requestStartedAt = System.currentTimeMillis()
                val result = runCatching {
                    if (isKeywordGoogleNewsConfig) {
                        val queryResult = fetchKeywordGoogleNewsQuery(
                            config = config,
                            press = press,
                            normalizedKeywords = normalizedKeywords,
                            requestUrl = requestUrl,
                            requestTimeoutMs = requestTimeoutMs
                                ?: NewsFetchConfig.SEARCH_GOOGLE_NEWS_QUERY_TIMEOUT_MS,
                            sourceStartedAt = sourceStartedAt,
                            sourceBudgetMs = sourceBudgetMs,
                            requestStartedAt = requestStartedAt,
                            ytnTraceCollector = ytnTraceCollector
                        )
                        keywordQueryDiagnostics = queryResult.diagnostics
                        parsedItemCount = queryResult.diagnostics.parsedItemCount
                        responseBytes = queryResult.diagnostics.responseBytes
                        queryResult.articles
                    } else {
                        withOptionalTimeout(requestTimeoutMs) {
                            val xml = fetcher.fetch(requestUrl)
                            responseBytes = xml.toByteArray(StandardCharsets.UTF_8).size
                            parsedItemCount = RssParser.countItems(xml)
                            ytnTraceCollector?.startFeed(requestUrl)
                            val parsedArticles = RssParser.parse(
                                xml = xml,
                                press = press,
                                sourceType = sourceType,
                                sourceLabel = config.label,
                                originalUrlMode = config.originalUrlMode,
                                originalUrlResolver = originalUrlResolver,
                                feedUrl = requestUrl,
                                ytnTraceCollector = ytnTraceCollector,
                                maxItems = config.maxItemsFor(normalizedKeywords),
                                maxArticles = config.maxArticlesToParseFor(normalizedKeywords)
                            )
                                .tagSearchKeywords(config = config, keywords = normalizedKeywords)
                                .sortedByDescending { article -> article.publishedAt ?: Long.MIN_VALUE }
                                .take(config.maxArticlesForSource(normalizedKeywords))

                            if (shouldEnrichDetails(config, isKeywordGoogleNewsConfig)) {
                                val detailTimeoutMs = if (isKeywordGoogleNewsConfig) {
                                    NewsFetchConfig.SEARCH_DETAIL_FETCH_TIMEOUT_MS
                                } else {
                                    NewsFetchConfig.DETAIL_FETCH_TIMEOUT_MS
                                }
                                parsedArticles.enrichFromOriginalUrls(
                                    maxPerArticleTimeoutMs = detailTimeoutMs
                                ) {
                                    remainingBudgetMs(sourceStartedAt, sourceBudgetMs)
                                }
                            } else {
                                parsedArticles
                            }
                        } ?: error("RSS_SOURCE_TIMEOUT")
                    }
                }

                val articles = result.getOrDefault(emptyList())
                val success = articles.isNotEmpty()
                if (press == NewsPress.YTN) {
                    val snapshot = YtnPipelineDebugStore.latestSnapshot()
                    NewsFetchLogger.logYtnRuntime(
                        "rss_feed url=${NewsFetchLogger.safeUrlForLog(requestUrl)} success=$success articleCount=${articles.size} " +
                            "rawItemCount=${snapshot.rawItemCount} resolveSuccess=${snapshot.resolveSuccessCount} " +
                            "resolveFailure=${snapshot.resolveFailureCount} domainMismatch=${snapshot.domainMismatchCount} " +
                            "policyMismatch=${snapshot.articleUrlPolicyMismatchCount}"
                    )
                }
                NewsFetchLogger.logAttempt(
                    tag = NewsFetchLogger.TAG_RSS,
                    press = press,
                    sourceType = sourceType,
                    sourceName = sourceName,
                    url = requestUrl,
                    success = success,
                    articleCount = articles.size,
                    errorMessage = result.exceptionOrNull()?.message
                )
                NewsFetchLogger.logAttempt(
                    tag = NewsFetchLogger.TAG_FETCH,
                    press = press,
                    sourceType = sourceType,
                    sourceName = sourceName,
                    url = requestUrl,
                    success = success,
                    articleCount = articles.size,
                    errorMessage = result.exceptionOrNull()?.message
                )
                if (isKeywordGoogleNewsConfig) {
                    logKeywordSearchQueryResult(
                        press = press,
                        requestVariant = requestVariant,
                        responseStatus = keywordQueryDiagnostics.responseStatus.ifBlank {
                            responseStatusFor(
                                success = result.isSuccess,
                                errorMessage = result.exceptionOrNull()?.message
                            )
                        },
                        responseBytes = responseBytes,
                        parsedItemCount = parsedItemCount,
                        originalUrlResolvedCount = keywordQueryDiagnostics.originalUrlResolvedCount,
                        candidateAfterRedirectCount = keywordQueryDiagnostics.candidateAfterRedirectCount,
                        candidateAfterDomainValidationCount = keywordQueryDiagnostics.candidateAfterDomainValidationCount,
                        validArticleCount = articles.size,
                        imageResolvedCount = articles.count { article -> !article.imageUrl.isNullOrBlank() },
                        bodyPreviewResolvedCount = articles.count { article -> article.hasBodyPreview() },
                        timeoutStage = keywordQueryDiagnostics.timeoutStage,
                        failReasonSample = keywordQueryDiagnostics.failReasonSample,
                        elapsedMs = System.currentTimeMillis() - requestStartedAt
                    )
                }

                if (isKeywordGoogleNewsConfig) {
                    if (success) {
                        keywordSearchArticles.mergeUnique(articles)
                        if (keywordSearchArticles.size >= NewsFetchConfig.MAX_ARTICLES_PER_PRESS) {
                            return NewsDataSourceResult(
                                articles = keywordSearchArticles
                                    .sortedByDescending { article -> article.publishedAt ?: Long.MIN_VALUE }
                                    .take(NewsFetchConfig.SEARCH_GOOGLE_NEWS_RAW_CANDIDATE_LIMIT_PER_PRESS)
                                    .prepareForSource(),
                                success = true,
                                message = "${config.label} ${keywordSearchArticles.size} keyword candidates collected"
                            )
                        }
                    }
                    failureMessages += result.exceptionOrNull()?.message
                        ?: keywordQueryDiagnostics.failReasonSample.takeUnless { reason -> reason == "none" }
                        ?: "Google News RSS keyword candidates not found"
                    return@variantLoop
                }

                if (success) {
                    return NewsDataSourceResult(
                        articles = articles.prepareForSource(),
                        success = true,
                        message = "${config.label} ${articles.size} articles collected"
                    )
                }

                failureMessages += result.exceptionOrNull()?.message ?: "RSS articles not found"
            }
        }

        if (isKeywordGoogleNewsSearch && keywordSearchArticles.isNotEmpty()) {
            return NewsDataSourceResult(
                articles = keywordSearchArticles
                    .sortedByDescending { article -> article.publishedAt ?: Long.MIN_VALUE }
                    .take(NewsFetchConfig.SEARCH_GOOGLE_NEWS_RAW_CANDIDATE_LIMIT_PER_PRESS)
                    .prepareForSource(),
                success = true,
                message = "Google News RSS ${keywordSearchArticles.size} keyword candidates collected"
            )
        }

        return NewsDataSourceResult(
            articles = emptyList(),
            success = false,
            message = failureMessages.joinToString("; ").ifBlank { "RSS collection failed" }
        )
    }

    private fun RssFeedConfig.requestUrlFor(keywords: List<String>): String {
        val normalizedKeywords = keywords.normalizedSearchKeywords()
        if (originalUrlMode != RssOriginalUrlMode.GOOGLE_NEWS || normalizedKeywords.isEmpty()) {
            return url
        }

        val encodedKeywords = normalizedKeywords.joinToString(" ").encodeQueryValue()
        val queryRegex = Regex("([?&]q=)([^&]*)")
        if (!queryRegex.containsMatchIn(url)) {
            val separator = if (url.contains("?")) "&" else "?"
            return "$url${separator}q=$encodedKeywords"
        }

        val match = queryRegex.find(url) ?: return url
        val currentQuery = match.groupValues[2]
        val separator = if (currentQuery.isBlank()) "" else "%20"
        val replacement = "${match.groupValues[1]}$currentQuery$separator$encodedKeywords"
        return url.replaceRange(match.range, replacement)
    }

    private fun RssFeedConfig.requestVariantsFor(
        press: NewsPress,
        normalizedKeywords: List<String>
    ): List<RssRequestVariant> {
        if (originalUrlMode != RssOriginalUrlMode.GOOGLE_NEWS || normalizedKeywords.isEmpty()) {
            return listOf(
                RssRequestVariant(
                    url = url,
                    queryPreview = googleNewsQueryPreview(url),
                    encodedQueryPreview = googleNewsEncodedQuery(url),
                    variantIndex = 1,
                    queryVariantCount = 1
                )
            )
        }

        val keywordText = normalizedKeywords.joinToString(" ")
        val pressQueryName = press.searchQueryName()
        val drafts = listOf(
            requestUrlFor(normalizedKeywords),
            googleNewsSearchUrl("$keywordText $pressQueryName"),
            googleNewsSearchUrl("$keywordText $pressQueryName \uB274\uC2A4"),
            googleNewsSearchUrl(keywordText)
        )
            .distinct()
            .take(NewsFetchConfig.SEARCH_GOOGLE_NEWS_QUERY_VARIANT_LIMIT)

        return drafts.mapIndexed { index, requestUrl ->
            RssRequestVariant(
                url = requestUrl,
                queryPreview = googleNewsQueryPreview(requestUrl),
                encodedQueryPreview = googleNewsEncodedQuery(requestUrl),
                variantIndex = index + 1,
                queryVariantCount = drafts.size
            )
        }
    }

    private fun List<NewsArticle>.tagSearchKeywords(
        config: RssFeedConfig,
        keywords: List<String>
    ): List<NewsArticle> {
        val normalizedKeywords = keywords.normalizedSearchKeywords()
        if (config.originalUrlMode != RssOriginalUrlMode.GOOGLE_NEWS || normalizedKeywords.isEmpty()) {
            return this
        }
        return map { article ->
            article.copy(keywords = (article.keywords + normalizedKeywords).distinct())
        }
    }

    private fun RssFeedConfig.maxItemsFor(keywords: List<String>): Int {
        if (originalUrlMode != RssOriginalUrlMode.GOOGLE_NEWS || keywords.normalizedSearchKeywords().isEmpty()) {
            return maxItemsPerFeed
        }
        return maxOf(maxItemsPerFeed, NewsFetchConfig.SEARCH_GOOGLE_NEWS_MAX_ITEMS_PER_QUERY)
    }

    private fun RssFeedConfig.maxArticlesForSource(keywords: List<String>): Int =
        when {
            originalUrlMode == RssOriginalUrlMode.GOOGLE_NEWS && keywords.normalizedSearchKeywords().isNotEmpty() ->
                NewsFetchConfig.SEARCH_GOOGLE_NEWS_RAW_CANDIDATE_LIMIT_PER_PRESS
            originalUrlMode != RssOriginalUrlMode.GOOGLE_NEWS && keywords.normalizedSearchKeywords().isNotEmpty() ->
                NewsFetchConfig.SEARCH_RSS_FALLBACK_RAW_CANDIDATE_LIMIT_PER_PRESS
            else -> NewsFetchConfig.MAX_ARTICLES_PER_PRESS
        }

    private fun RssFeedConfig.maxArticlesToParseFor(keywords: List<String>): Int =
        if (originalUrlMode == RssOriginalUrlMode.GOOGLE_NEWS && keywords.normalizedSearchKeywords().isNotEmpty()) {
            NewsFetchConfig.MAX_ARTICLES_PER_PRESS
        } else {
            Int.MAX_VALUE
        }

    private suspend fun fetchKeywordGoogleNewsQuery(
        config: RssFeedConfig,
        press: NewsPress,
        normalizedKeywords: List<String>,
        requestUrl: String,
        requestTimeoutMs: Long,
        sourceStartedAt: Long,
        sourceBudgetMs: Long?,
        requestStartedAt: Long,
        ytnTraceCollector: YtnPipelineTraceCollector?
    ): KeywordGoogleNewsQueryResult {
        fun remainingQueryMs(): Long =
            (requestTimeoutMs - (System.currentTimeMillis() - requestStartedAt)).coerceAtLeast(0L)

        fun remainingCombinedMs(): Long? {
            val queryRemaining = remainingQueryMs()
            val sourceRemaining = remainingBudgetMs(sourceStartedAt, sourceBudgetMs)
            return sourceRemaining?.let { remaining -> minOf(queryRemaining, remaining) } ?: queryRemaining
        }

        val fetchTimeoutMs = remainingCombinedMs()?.takeIf { it > 0L }
            ?: return KeywordGoogleNewsQueryResult(
                diagnostics = KeywordGoogleNewsQueryDiagnostics(
                    responseStatus = GoogleNewsOriginalUrlExtractor.RSS_FETCH_TIMEOUT,
                    timeoutStage = "fetch",
                    failReasonSample = GoogleNewsOriginalUrlExtractor.RSS_FETCH_TIMEOUT
                )
            )
        val xml = withOptionalTimeout(fetchTimeoutMs) {
            fetcher.fetch(requestUrl)
        } ?: return KeywordGoogleNewsQueryResult(
            diagnostics = KeywordGoogleNewsQueryDiagnostics(
                responseStatus = GoogleNewsOriginalUrlExtractor.RSS_FETCH_TIMEOUT,
                timeoutStage = "fetch",
                failReasonSample = GoogleNewsOriginalUrlExtractor.RSS_FETCH_TIMEOUT
            )
        )

        val responseBytes = xml.toByteArray(StandardCharsets.UTF_8).size
        val parsedItemCount = RssParser.countItems(xml)
        ytnTraceCollector?.startFeed(requestUrl)
        if (parsedItemCount == 0) {
            return KeywordGoogleNewsQueryResult(
                diagnostics = KeywordGoogleNewsQueryDiagnostics(
                    responseStatus = GoogleNewsOriginalUrlExtractor.RSS_PARSE_EMPTY,
                    responseBytes = responseBytes,
                    parsedItemCount = parsedItemCount,
                    failReasonSample = GoogleNewsOriginalUrlExtractor.RSS_PARSE_EMPTY
                )
            )
        }

        val preResolve = preResolveGoogleNewsUrls(
            xml = xml,
            press = press,
            maxItems = config.maxItemsFor(normalizedKeywords),
            remainingBudgetMs = ::remainingCombinedMs
        )
        val parsedArticles = RssParser.parse(
            xml = xml,
            press = press,
            sourceType = sourceType,
            sourceLabel = config.label,
            originalUrlMode = config.originalUrlMode,
            originalUrlResolver = preResolve.resolver,
            feedUrl = requestUrl,
            ytnTraceCollector = ytnTraceCollector,
            maxItems = config.maxItemsFor(normalizedKeywords),
            maxArticles = config.maxArticlesToParseFor(normalizedKeywords)
        )
            .tagSearchKeywords(config = config, keywords = normalizedKeywords)
            .sortedByDescending { article -> article.publishedAt ?: Long.MIN_VALUE }
            .take(config.maxArticlesForSource(normalizedKeywords))

        val baseArticleCount = parsedArticles.size
        val resolvedCount = maxOf(preResolve.resolvedCount, baseArticleCount)
        val enrichedArticles = if (parsedArticles.isNotEmpty() && shouldEnrichDetails(config, isKeywordGoogleNewsConfig = true)) {
            parsedArticles.enrichFromOriginalUrls(
                maxPerArticleTimeoutMs = NewsFetchConfig.SEARCH_DETAIL_FETCH_TIMEOUT_MS
            ) {
                remainingCombinedMs()
            }
        } else {
            parsedArticles
        }

        val timeoutStage = when {
            preResolve.timeoutOccurred -> "resolve"
            remainingQueryMs() <= 0L -> "query"
            else -> "none"
        }
        val failReasonSample = preResolve.failureReasons
            .distinct()
            .take(MAX_LOG_FAIL_REASONS)
            .joinToString("|")
            .ifBlank {
                if (enrichedArticles.isEmpty()) {
                    GoogleNewsOriginalUrlExtractor.FINAL_URL_EMPTY
                } else {
                    "none"
                }
            }

        return KeywordGoogleNewsQueryResult(
            articles = enrichedArticles,
            diagnostics = KeywordGoogleNewsQueryDiagnostics(
                responseStatus = if (enrichedArticles.isNotEmpty()) "success" else "no_candidates",
                responseBytes = responseBytes,
                parsedItemCount = parsedItemCount,
                originalUrlResolvedCount = resolvedCount,
                candidateAfterRedirectCount = resolvedCount,
                candidateAfterDomainValidationCount = baseArticleCount,
                timeoutStage = timeoutStage,
                failReasonSample = failReasonSample
            )
        )
    }

    private fun shouldEnrichDetails(
        config: RssFeedConfig,
        isKeywordGoogleNewsConfig: Boolean
    ): Boolean =
        config.enrichFromOriginalUrl && (enableDetailEnrichment || isKeywordGoogleNewsConfig)

    private suspend fun preResolveGoogleNewsUrls(
        xml: String,
        press: NewsPress,
        maxItems: Int,
        remainingBudgetMs: () -> Long?
    ): GoogleNewsPreResolveResult {
        val candidateUrls = RssParser.googleNewsCandidateUrls(
            xml = xml,
            press = press,
            maxItems = maxItems
        ).take(NewsFetchConfig.SEARCH_GOOGLE_NEWS_MAX_URL_RESOLVES_PER_QUERY)
        if (candidateUrls.isEmpty()) {
            return GoogleNewsPreResolveResult(
                resolver = OriginalUrlResolver { null },
                totalCandidateCount = 0,
                resolvedByUrl = emptyMap(),
                failureReasons = listOf(GoogleNewsOriginalUrlExtractor.ORIGINAL_URL_PARAM_NOT_FOUND)
            )
        }

        val resolvedByUrl = mutableMapOf<String, String?>()
        val failureReasons = mutableListOf<String>()
        var timeoutOccurred = false

        for (chunk in candidateUrls.chunked(NewsFetchConfig.SEARCH_GOOGLE_NEWS_RESOLVE_CONCURRENCY)) {
            val remainingMs = remainingBudgetMs()
            if (remainingMs != null && remainingMs <= 0L) {
                timeoutOccurred = true
                failureReasons += GoogleNewsOriginalUrlExtractor.REDIRECT_TIMEOUT
                break
            }

            val itemTimeoutMs = remainingMs
                ?.let { remaining -> minOf(remaining, NewsFetchConfig.SEARCH_GOOGLE_NEWS_ITEM_RESOLVE_TIMEOUT_MS) }
                ?: NewsFetchConfig.SEARCH_GOOGLE_NEWS_ITEM_RESOLVE_TIMEOUT_MS
            val attempts = coroutineScope {
                chunk.map { url ->
                    async {
                        resolveGoogleNewsCandidate(
                            url = url,
                            press = press,
                            timeoutMs = itemTimeoutMs
                        )
                    }
                }.awaitAll()
            }
            attempts.forEach { attempt ->
                resolvedByUrl[attempt.url] = attempt.resolvedUrl
                attempt.failureReason?.let(failureReasons::add)
            }
            if (resolvedByUrl.values.count { resolvedUrl ->
                    !resolvedUrl.isNullOrBlank() && ArticleUrlPolicy.isValidArticleUrl(press, resolvedUrl)
                } >= NewsFetchConfig.MAX_ARTICLES_PER_PRESS) {
                break
            }
        }
        return GoogleNewsPreResolveResult(
            resolver = OriginalUrlResolver { url -> resolvedByUrl[url] },
            totalCandidateCount = candidateUrls.size,
            resolvedByUrl = resolvedByUrl,
            failureReasons = failureReasons,
            timeoutOccurred = timeoutOccurred
        )
    }

    private suspend fun resolveGoogleNewsCandidate(
        url: String,
        press: NewsPress,
        timeoutMs: Long
    ): GoogleNewsResolveAttempt {
        if (timeoutMs <= 0L) {
            return GoogleNewsResolveAttempt(
                url = url,
                resolvedUrl = null,
                failureReason = GoogleNewsOriginalUrlExtractor.REDIRECT_TIMEOUT
            )
        }

        val resolvedUrl = runCatching {
            withTimeoutOrNull(timeoutMs) {
                originalUrlResolver.resolve(url)
            }
        }.getOrNull()

        return GoogleNewsResolveAttempt(
            url = url,
            resolvedUrl = resolvedUrl?.takeIf { candidate -> !candidate.isGoogleNewsUrl() },
            failureReason = when {
                resolvedUrl.isNullOrBlank() -> GoogleNewsOriginalUrlExtractor.FINAL_URL_EMPTY
                resolvedUrl.isGoogleNewsUrl() -> GoogleNewsOriginalUrlExtractor.FINAL_URL_STILL_GOOGLE
                !ArticleUrlPolicy.isValidArticleUrl(press, resolvedUrl) -> GoogleNewsOriginalUrlExtractor.DOMAIN_MISMATCH
                else -> null
            }
        )
    }

    private fun effectiveMaxFeedsPerPress(isKeywordGoogleNewsSearch: Boolean): Int =
        if (isKeywordGoogleNewsSearch) {
            maxOf(maxFeedsPerPress, NewsFetchConfig.SEARCH_GOOGLE_NEWS_MAX_FEEDS_PER_PRESS)
        } else {
            maxFeedsPerPress
        }

    private fun effectiveFetchBudgetMs(isKeywordGoogleNewsSearch: Boolean): Long? =
        if (isKeywordGoogleNewsSearch) {
            maxOf(fetchBudgetMs ?: 0L, NewsFetchConfig.SEARCH_GOOGLE_NEWS_SOURCE_TIMEOUT_MS)
        } else {
            fetchBudgetMs
        }

    private fun requestTimeoutMs(
        remainingBeforeFeedMs: Long?,
        isKeywordGoogleNewsSearch: Boolean
    ): Long? {
        if (!isKeywordGoogleNewsSearch) return remainingBeforeFeedMs
        return remainingBeforeFeedMs
            ?.let { remaining -> minOf(remaining, NewsFetchConfig.SEARCH_GOOGLE_NEWS_QUERY_TIMEOUT_MS) }
            ?: NewsFetchConfig.SEARCH_GOOGLE_NEWS_QUERY_TIMEOUT_MS
    }

    private fun List<String>.normalizedSearchKeywords(): List<String> =
        map { keyword -> keyword.trim() }
            .filter { keyword -> keyword.isNotBlank() }
            .distinct()

    private fun String.encodeQueryValue(): String =
        URLEncoder.encode(this, StandardCharsets.UTF_8.name()).replace("+", "%20")

    private fun googleNewsSearchUrl(query: String): String =
        "https://news.google.com/rss/search?q=${query.encodeQueryValue()}&hl=ko&gl=KR&ceid=KR:ko"

    private fun googleNewsEncodedQuery(url: String): String =
        runCatching {
            URI(url).rawQuery.orEmpty()
                .split('&')
                .firstOrNull { part -> part.substringBefore('=') == "q" }
                ?.substringAfter('=', missingDelimiterValue = "")
                .orEmpty()
                .take(MAX_LOG_QUERY_LENGTH)
        }.getOrDefault("")

    private fun googleNewsQueryPreview(url: String): String =
        googleNewsEncodedQuery(url)
            .decodeQueryValue()
            .ifBlank { NewsFetchLogger.safeUrlForLog(url) }
            .take(MAX_LOG_QUERY_LENGTH)

    private fun String.decodeQueryValue(): String =
        runCatching {
            URLDecoder.decode(this, StandardCharsets.UTF_8.name())
        }.getOrDefault(this)

    private fun NewsPress.searchQueryName(): String =
        when (this) {
            NewsPress.YONHAP -> displayName
            NewsPress.MBC -> "MBC"
            NewsPress.SBS -> "SBS"
            NewsPress.KBS -> "KBS"
            NewsPress.YTN -> "YTN"
            NewsPress.ALL -> "news"
        }

    private fun MutableList<NewsArticle>.mergeUnique(articles: List<NewsArticle>) {
        val seenKeys = map { article -> article.uniqueArticleKey() }.toMutableSet()
        articles.forEach { article ->
            val key = article.uniqueArticleKey()
            if (seenKeys.add(key)) {
                add(article)
            }
        }
    }

    private fun NewsArticle.uniqueArticleKey(): String =
        UrlNormalizer.normalize(originalUrl).ifBlank { "$press|$title" }

    private fun logKeywordSearchQueryResult(
        press: NewsPress,
        requestVariant: RssRequestVariant,
        responseStatus: String,
        responseBytes: Int,
        parsedItemCount: Int,
        originalUrlResolvedCount: Int,
        candidateAfterRedirectCount: Int,
        candidateAfterDomainValidationCount: Int,
        validArticleCount: Int,
        imageResolvedCount: Int,
        bodyPreviewResolvedCount: Int,
        timeoutStage: String,
        failReasonSample: String,
        elapsedMs: Long
    ) {
        NewsFetchLogger.logSearchPerf(
            "sourceName=$sourceName press=${press.displayName} " +
                "queryVariantIndex=${requestVariant.variantIndex} queryVariantCount=${requestVariant.queryVariantCount} " +
                "keyword=${requestVariant.queryPreview} queryPreview=${requestVariant.queryPreview} " +
                "encodedQueryPreview=${requestVariant.encodedQueryPreview} " +
                "requestHost=${requestHostForLog(requestVariant.url)} responseStatus=$responseStatus " +
                "responseBytes=$responseBytes parsedItemCount=$parsedItemCount " +
                "originalUrlResolvedCount=$originalUrlResolvedCount " +
                "candidateAfterRedirectCount=$candidateAfterRedirectCount " +
                "candidateAfterDomainValidationCount=$candidateAfterDomainValidationCount " +
                "keywordMatchedCount=$validArticleCount validArticleCount=$validArticleCount " +
                "imageResolvedCount=$imageResolvedCount bodyPreviewResolvedCount=$bodyPreviewResolvedCount " +
                "timeoutStage=$timeoutStage failReasonSample=$failReasonSample " +
                "sourceElapsedMs=$elapsedMs"
        )
    }

    private fun responseStatusFor(
        success: Boolean,
        errorMessage: String?
    ): String {
        if (success) return "success"
        return Regex("RSS HTTP (\\d+)").find(errorMessage.orEmpty())
            ?.groupValues
            ?.getOrNull(1)
            ?: "unknown"
    }

    private fun requestHostForLog(url: String): String =
        runCatching { URI(url).host.orEmpty() }
            .getOrDefault("")
            .ifBlank { NewsFetchLogger.safeUrlForLog(url) }

    private fun List<NewsArticle>.prepareForSource(): List<NewsArticle> =
        map { article ->
            article.copy(
                sourceType = sourceType,
                sourceLabel = article.sourceLabel.ifBlank { sourceType.displayName }
            )
        }

    private suspend fun List<NewsArticle>.enrichFromOriginalUrls(
        maxPerArticleTimeoutMs: Long,
        remainingBudgetMs: () -> Long?
    ): List<NewsArticle> =
        map { article ->
            runCatching {
                val timeoutMs = minDetailTimeoutMs(
                    remainingBudgetMs = remainingBudgetMs(),
                    maxPerArticleTimeoutMs = maxPerArticleTimeoutMs
                )
                    ?: error("DETAIL_FETCH_TIMEOUT")
                withTimeoutOrNull(timeoutMs) {
                    detailDataSource.fetchDetail(article)
                } ?: error("DETAIL_FETCH_TIMEOUT")
            }.getOrElse { error ->
                if (article.press == NewsPress.YTN) {
                    YtnPipelineDebugStore.current()?.recordDetail(
                        originalUrl = article.originalUrl,
                        success = false,
                        extractedTitle = null,
                        paragraphCount = article.bodyParagraphs.size,
                        contentLength = article.content.length,
                        boilerplateRemovedCount = 0
                    )
                }
                NewsFetchLogger.logDetail(
                    "rss_detail_enrichment_failed press=${article.press.displayName} " +
                        "originalUrl=${NewsFetchLogger.safeUrlForLog(article.originalUrl)} error=${error.message.orEmpty()}"
                )
                article
            }
        }

    private fun remainingBudgetMs(
        sourceStartedAt: Long,
        sourceBudgetMs: Long?
    ): Long? =
        sourceBudgetMs?.let { budget ->
            (budget - (System.currentTimeMillis() - sourceStartedAt)).coerceAtLeast(0L)
        }

    private fun minDetailTimeoutMs(
        remainingBudgetMs: Long?,
        maxPerArticleTimeoutMs: Long
    ): Long? {
        val timeoutMs = remainingBudgetMs
            ?.let { remaining -> minOf(maxPerArticleTimeoutMs, remaining) }
            ?: maxPerArticleTimeoutMs
        return timeoutMs.takeIf { it > 0L }
    }

    private fun NewsArticle.hasBodyPreview(): Boolean =
        bodyParagraphs.any { paragraph -> paragraph.isNotBlank() } ||
            content.isNotBlank() ||
            summary.isNotBlank()

    private suspend fun <T> withOptionalTimeout(
        timeoutMs: Long?,
        block: suspend () -> T
    ): T? =
        if (timeoutMs == null) {
            block()
        } else if (timeoutMs <= 0L) {
            null
        } else {
            withTimeoutOrNull(timeoutMs) { block() }
        }

    private fun String.isGoogleNewsUrl(): Boolean =
        runCatching { URI(this).host.orEmpty().lowercase() }
            .getOrDefault("")
            .let { host -> host == "news.google.com" || host.endsWith(".news.google.com") }

    private data class KeywordGoogleNewsQueryResult(
        val articles: List<NewsArticle> = emptyList(),
        val diagnostics: KeywordGoogleNewsQueryDiagnostics = KeywordGoogleNewsQueryDiagnostics()
    )

    private data class KeywordGoogleNewsQueryDiagnostics(
        val responseStatus: String = "",
        val responseBytes: Int = 0,
        val parsedItemCount: Int = 0,
        val originalUrlResolvedCount: Int = 0,
        val candidateAfterRedirectCount: Int = 0,
        val candidateAfterDomainValidationCount: Int = 0,
        val timeoutStage: String = "none",
        val failReasonSample: String = "none"
    )

    private data class GoogleNewsPreResolveResult(
        val resolver: OriginalUrlResolver,
        val totalCandidateCount: Int,
        val resolvedByUrl: Map<String, String?>,
        val failureReasons: List<String>,
        val timeoutOccurred: Boolean = false
    ) {
        val resolvedCount: Int
            get() = resolvedByUrl.values.count { resolvedUrl -> !resolvedUrl.isNullOrBlank() }
    }

    private data class GoogleNewsResolveAttempt(
        val url: String,
        val resolvedUrl: String?,
        val failureReason: String?
    )

    private data class RssRequestVariant(
        val url: String,
        val queryPreview: String,
        val encodedQueryPreview: String,
        val variantIndex: Int,
        val queryVariantCount: Int
    )

    private companion object {
        const val MAX_LOG_QUERY_LENGTH = 120
        const val MAX_LOG_FAIL_REASONS = 3
    }
}

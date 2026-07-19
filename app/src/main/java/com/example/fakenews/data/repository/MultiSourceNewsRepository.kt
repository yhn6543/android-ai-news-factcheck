package com.example.fakenews.data.repository

import android.util.Log
import com.example.fakenews.BuildConfig
import com.example.fakenews.data.model.CollectionMode
import com.example.fakenews.data.model.NewsArticle
import com.example.fakenews.data.model.NewsDataSourceResult
import com.example.fakenews.data.model.NewsFetchResult
import com.example.fakenews.data.model.NewsPress
import com.example.fakenews.data.model.NewsSourceStatus
import com.example.fakenews.data.model.NewsSourceType
import com.example.fakenews.data.remote.HtmlCrawlingNewsDataSource
import com.example.fakenews.data.remote.NewsDataSource
import com.example.fakenews.data.remote.NewsFetchConfig
import com.example.fakenews.data.remote.NewsFetchLogger
import com.example.fakenews.data.remote.RssFeedConfig
import com.example.fakenews.data.remote.RssNewsDataSource
import com.example.fakenews.data.remote.YtnPipelineDebugStore
import com.example.fakenews.data.remote.YtnRepositoryDebugTrace
import com.example.fakenews.util.ArticleIdentity
import com.example.fakenews.util.ArticleQualityValidator
import com.example.fakenews.util.NewsDeduplicator
import com.example.fakenews.util.NewsFilter
import com.example.fakenews.util.UiText
import com.example.fakenews.util.UrlNormalizer
import java.util.Collections
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

open class MultiSourceNewsRepository(
    private val dataSources: List<NewsDataSource> = listOf(
        RssNewsDataSource(
            feedConfigs = RssFeedConfig.officialFeeds,
            sourceName = "Official RSS",
            fetchBudgetMs = NewsFetchConfig.MAIN_DEFAULT_RSS_SOURCE_TIMEOUT_MS
        ),
        HtmlCrawlingNewsDataSource(),
        RssNewsDataSource(
            feedConfigs = RssFeedConfig.keywordSearchGoogleNewsFeeds,
            sourceName = "Google News RSS",
            fetchBudgetMs = NewsFetchConfig.MAIN_DEFAULT_GOOGLE_NEWS_RSS_SOURCE_TIMEOUT_MS,
            enableDetailEnrichment = false,
            maxFeedsPerPress = NewsFetchConfig.MAIN_DEFAULT_GOOGLE_NEWS_MAX_FEEDS_PER_PRESS,
            maxItemsPerFeed = NewsFetchConfig.MAIN_DEFAULT_GOOGLE_NEWS_MAX_ITEMS_PER_FEED
        )
    ),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nowMillis: () -> Long = { System.currentTimeMillis() }
) : NewsRepository, NewsRepositoryStatusProvider {
    private val cacheLock = Any()
    private var cachedArticles: List<NewsArticle> = emptyList()
    final override var lastLoadStatus: NewsLoadStatus? = null
        private set

    override suspend fun getLatestNews(): NewsFetchResult =
        searchNews(
            selectedPresses = NewsPress.articlePresses().toSet(),
            keywords = emptyList(),
            collectionMode = CollectionMode.MAIN_DEFAULT
        )

    override suspend fun searchNews(
        selectedPresses: Set<NewsPress>,
        keywords: List<String>
    ): NewsFetchResult =
        searchNews(
            selectedPresses = selectedPresses,
            keywords = keywords,
            collectionMode = CollectionMode.SEARCH
        )

    override suspend fun searchNews(
        selectedPresses: Set<NewsPress>,
        keywords: List<String>,
        collectionMode: CollectionMode
    ): NewsFetchResult {
        val targetPresses = resolveActualPressesForCollection(selectedPresses)
        if (targetPresses.isEmpty()) {
            lastLoadStatus = null
            return noSelectedPressResult()
        }

        if (NewsPress.YTN in targetPresses) {
            NewsFetchLogger.logYtnRepository(
                "request selectedPresses=${targetPresses.joinToString { press -> press.displayName }} " +
                    "keywords=${keywords.joinToString()}"
            )
        }

        val totalStartedAt = nowMillis()
        val completedPressResults = Collections.synchronizedList(mutableListOf<PressCollectionResult>())
        logCollectionLifecycle("repository main collection start mode=$collectionMode actualPresses=${targetPresses.joinToString { it.name }}")
        logCollectionPerf("totalCollectionStart mode=$collectionMode pressCount=${targetPresses.size}")
        logSearchPerf(
            collectionMode = collectionMode,
            message = "searchStart keyword=${keywordPreview(keywords)} selectedPresses=${targetPresses.joinToString { it.name }}"
        )
        val pressResults = withContext(ioDispatcher) {
            withTimeoutOrNull(NewsFetchConfig.MAIN_COLLECTION_TIMEOUT_MS) {
                collectPressesInParallel(
                    targetPresses = targetPresses,
                    keywords = keywords,
                    collectionMode = collectionMode,
                    onPressStarted = {},
                    onPressResult = { result -> completedPressResults += result }
                )
            } ?: mainTimeoutResults(
                targetPresses = targetPresses,
                completedResults = completedPressResults.toList(),
                startedAt = totalStartedAt,
                collectionMode = collectionMode
            )
        }
        logCollectionPerf(
            "totalCollectionEnd mode=$collectionMode totalElapsedMs=${nowMillis() - totalStartedAt}"
        )
        logCollectionLifecycle(
            "repository main collection end mode=$collectionMode finalPressResultCount=${pressResults.size} " +
                "elapsedMs=${nowMillis() - totalStartedAt}"
        )

        val result = buildFetchResult(
            targetPresses = targetPresses,
            keywords = keywords,
            pressResults = pressResults,
            updateRepositoryState = true,
            collectionMode = collectionMode
        )
        logSearchPerf(
            collectionMode = collectionMode,
            message = "searchFinished totalArticleCount=${result.articles.size}"
        )
        return result
    }

    override fun collectNewsByPressFlow(
        selectedPresses: Set<NewsPress>,
        keywords: List<String>,
        collectionMode: CollectionMode
    ): Flow<NewsCollectionEvent> = channelFlow {
        val targetPresses = resolveActualPressesForCollection(selectedPresses)
        if (targetPresses.isEmpty()) {
            send(
                NewsCollectionEvent.AllPressCollectionFinished(
                    NewsFetchResult(
                        articles = emptyList(),
                        message = UiText.NO_SELECTED_PRESS
                    )
                )
            )
            return@channelFlow
        }

        val totalStartedAt = nowMillis()
        val completedPressResults = Collections.synchronizedList(mutableListOf<PressCollectionResult>())
        logCollectionLifecycle(
            "repository incremental collection start mode=$collectionMode actualPresses=${targetPresses.joinToString { it.name }}"
        )
        logSearchPerf(
            collectionMode = collectionMode,
            message = "searchStart keyword=${keywordPreview(keywords)} selectedPresses=${targetPresses.joinToString { it.name }}"
        )
        val pressResults = withContext(ioDispatcher) {
            withTimeoutOrNull(NewsFetchConfig.MAIN_COLLECTION_TIMEOUT_MS) {
                collectPressesInParallel(
                    targetPresses = targetPresses,
                    keywords = keywords,
                    collectionMode = collectionMode,
                    onPressStarted = { press ->
                        send(NewsCollectionEvent.PressCollectionStarted(press))
                    },
                    onPressResult = { result ->
                        completedPressResults += result
                        rememberArticles(result.articles)
                        send(result.toCollectionEvent())
                    }
                )
            } ?: mainTimeoutResults(
                targetPresses = targetPresses,
                completedResults = completedPressResults.toList(),
                startedAt = totalStartedAt,
                collectionMode = collectionMode
            ).also { timedOutResults ->
                val completedPresses = completedPressResults.map { result -> result.press }.toSet()
                timedOutResults
                    .filterNot { result -> result.press in completedPresses }
                    .forEach { result -> send(result.toCollectionEvent()) }
            }
        }
        val finalResult = buildFetchResult(
            targetPresses = targetPresses,
            keywords = keywords,
            pressResults = pressResults,
            updateRepositoryState = true,
            collectionMode = collectionMode
        )
        logCollectionLifecycle(
            "repository incremental collection finished mode=$collectionMode " +
                "articleCount=${finalResult.articles.size} elapsedMs=${nowMillis() - totalStartedAt}"
        )
        logSearchPerf(
            collectionMode = collectionMode,
            message = "searchFinished totalArticleCount=${finalResult.articles.size}"
        )
        send(NewsCollectionEvent.AllPressCollectionFinished(finalResult))
    }

    override suspend fun getNewsArticleById(articleId: String): NewsArticle? {
        val decodedArticleId = UrlNormalizer.decodeHtmlEntities(articleId).trim()
        val normalizedLookup = UrlNormalizer.normalize(decodedArticleId)
        return synchronized(cacheLock) {
            cachedArticles.firstOrNull { article ->
                val decodedOriginalUrl = UrlNormalizer.decodeHtmlEntities(article.originalUrl).trim()
                article.id == articleId ||
                    article.id == decodedArticleId ||
                    decodedOriginalUrl == decodedArticleId ||
                    (normalizedLookup.isNotBlank() && UrlNormalizer.normalize(decodedOriginalUrl) == normalizedLookup) ||
                    ArticleIdentity.idFor(article.press, decodedOriginalUrl, article.title) == decodedArticleId
            }
        }
    }

    private fun noSelectedPressResult(): NewsFetchResult =
        NewsFetchResult(
            articles = emptyList(),
            message = UiText.NO_SELECTED_PRESS
        )

    private fun buildFetchResult(
        targetPresses: List<NewsPress>,
        keywords: List<String>,
        pressResults: List<PressCollectionResult>,
        updateRepositoryState: Boolean,
        collectionMode: CollectionMode
    ): NewsFetchResult {
        val statuses = pressResults.flatMap { result -> result.statuses }
        val collectedArticles = pressResults.flatMap { result -> result.articles }
        val failedPresses = pressResults
            .filter { result -> result.articles.isEmpty() }
            .map { result -> result.press }

        val filteredArticles = NewsFilter.filter(
            articles = collectedArticles,
            selectedPresses = targetPresses.toSet(),
            keywords = keywords
        )
        if (NewsPress.YTN in targetPresses) {
            val ytnFilterCounts = NewsFilter.debugCounts(
                articles = collectedArticles,
                selectedPresses = setOf(NewsPress.YTN),
                keywords = keywords
            )
            val collectedYtnCount = collectedArticles.count { article -> article.press == NewsPress.YTN }
            YtnPipelineDebugStore.recordRepository(
                YtnRepositoryDebugTrace(
                    collectedArticleCount = collectedYtnCount,
                    displayableArticleCount = collectedYtnCount,
                    afterPressFilterCount = ytnFilterCounts.afterPressFilterCount,
                    afterDedupCount = ytnFilterCounts.afterDedupCount,
                    afterKeywordCount = ytnFilterCounts.afterKeywordCount,
                    finalRepositoryArticleCount = filteredArticles.count { article -> article.press == NewsPress.YTN },
                    failedPress = NewsPress.YTN in failedPresses,
                    keywords = keywords
                )
            )
        }

        if (updateRepositoryState) {
            rememberArticles(filteredArticles)
            lastLoadStatus = when {
                failedPresses.isEmpty() -> NewsLoadStatus.Remote
                filteredArticles.isEmpty() -> NewsLoadStatus.Failed
                else -> NewsLoadStatus.PartialFailure
            }
        }

        if (filteredArticles.isEmpty()) {
            logSearchPerf(
                collectionMode = collectionMode,
                message = "emptySearchResult keyword=${keywordPreview(keywords)}"
            )
        }

        return NewsFetchResult(
            articles = filteredArticles,
            sourceStatuses = statuses,
            failedPresses = failedPresses,
            usedMockFallback = false,
            fallbackPresses = emptyList(),
            message = buildUserMessage(
                articles = filteredArticles,
                failedPresses = failedPresses
            )
        )
    }

    private suspend fun collectPressesInParallel(
        targetPresses: List<NewsPress>,
        keywords: List<String>,
        collectionMode: CollectionMode,
        onPressStarted: suspend (NewsPress) -> Unit,
        onPressResult: suspend (PressCollectionResult) -> Unit
    ): List<PressCollectionResult> = supervisorScope {
        targetPresses.map { press ->
            async(ioDispatcher) {
                val pressStartedAt = nowMillis()
                onPressStarted(press)
                logCollectionLifecycle("press async start press=${press.name} mode=$collectionMode")
                val result = try {
                    fetchPressNewsSequential(
                        press = press,
                        keywords = keywords,
                        collectionMode = collectionMode
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    logCollectionLifecycle("press async failure press=${press.name} error=${error.message.orEmpty()}")
                    failedPressCollectionResult(
                        press = press,
                        error = error,
                        elapsedMs = nowMillis() - pressStartedAt,
                        collectionMode = collectionMode
                    )
                }
                onPressResult(result)
                logCollectionLifecycle(
                    "press async end press=${press.name} articleCount=${result.articles.size} " +
                        "elapsedMs=${result.elapsedMs}"
                )
                result
            }
        }.map { deferred ->
            deferred.await()
        }
    }

    private suspend fun fetchPressNewsSequential(
        press: NewsPress,
        keywords: List<String>,
        collectionMode: CollectionMode
    ): PressCollectionResult {
        val pressStartedAt = nowMillis()
        val statuses = mutableListOf<NewsSourceStatus>()
        var rawCandidateCount = 0
        var processedCandidateCount = 0
        var keywordMatchedCount = 0
        var validArticleCount = 0
        var skippedInvalidCount = 0
        var skippedKeywordMismatchCount = 0
        var failedReason: String? = null
        var fallbackStarted = false
        var timeoutStage: String? = null
        val collectedArticles = mutableListOf<NewsArticle>()
        val sources = dataSourcesForMode(
            collectionMode = collectionMode,
            keywords = keywords
        )

        logCollectionPerf("pressStart press=${press.displayName} collectionMode=$collectionMode")
        logSearchPerf(
            collectionMode = collectionMode,
            message = "pressStart press=${press.displayName} collectionMode=SEARCH keyword=${keywordPreview(keywords)}"
        )

        for ((sourceIndex, dataSource) in sources.withIndex()) {
            val sourceStartedAt = nowMillis()
            if (sourceIndex > 0) {
                fallbackStarted = true
            }
            val remainingBeforeSourceMs = remainingPressTimeMs(pressStartedAt)
            if (remainingBeforeSourceMs <= 0L) {
                timeoutStage = "press"
                val message = "press timeout before source timeoutStage=press"
                failedReason = message
                statuses += NewsSourceStatus(
                    press = press,
                    sourceType = dataSource.sourceType,
                    sourceName = dataSource.sourceName,
                    success = false,
                    articleCount = 0,
                    message = message
                )
                logCollectionPerf(
                    "sourceTimeout press=${press.displayName} sourceName=${dataSource.sourceName} " +
                        "sourceType=${dataSource.sourceType} sourceElapsedMs=0 rawCandidateCount=0 " +
                        "processedCandidateCount=0 validArticleCount=0 skippedInvalidCount=0 " +
                        "timeoutOccurred=true timeoutStage=press fallbackStarted=$fallbackStarted " +
                        "topRejectReasons=${DiagnosticRejectReason.TIMEOUT.name} failedReason=$message"
                )
                logSearchPerf(
                    collectionMode = collectionMode,
                    message = "timeoutOccurred timeoutStage=press press=${press.displayName} sourceName=${dataSource.sourceName} " +
                        "searchModeSourceType=${searchModeSourceType(collectionMode, keywords, dataSource)} " +
                        "queryPreview=${queryPreviewFor(press, dataSource, keywords)}"
                )
                logCollectionDiag(
                    "press=${press.displayName} sourceName=${dataSource.sourceName} rawCandidateCount=0 " +
                        "processedCandidateCount=0 validArticleCount=0 timeoutStage=press " +
                        "failedReason=$message rejectReasons=${DiagnosticRejectReason.TIMEOUT.name}"
                )
                return finishPressCollectionResult(
                    press = press,
                    pressStartedAt = pressStartedAt,
                    articles = collectedArticles,
                    statuses = statuses,
                    collectionMode = collectionMode,
                    rawCandidateCount = rawCandidateCount,
                    processedCandidateCount = processedCandidateCount,
                    keywordMatchedCount = keywordMatchedCount,
                    validArticleCount = validArticleCount,
                    skippedInvalidCount = skippedInvalidCount,
                    skippedKeywordMismatchCount = skippedKeywordMismatchCount,
                    fallbackStarted = fallbackStarted,
                    fallbackSkippedReason = null,
                    failedReason = failedReason,
                    timeoutOccurred = true,
                    timeoutStage = "press",
                    topRejectReasons = DiagnosticRejectReason.TIMEOUT.name
                )
            }

            logCollectionPerf(
                "sourceStart press=${press.displayName} sourceName=${dataSource.sourceName} " +
                    "sourceType=${dataSource.sourceType} collectionMode=$collectionMode " +
                    "remainingTimeMs=$remainingBeforeSourceMs fallbackStarted=$fallbackStarted"
            )
            logSearchPerf(
                collectionMode = collectionMode,
                message = "sourceStart press=${press.displayName} sourceName=${dataSource.sourceName} " +
                    "searchModeSourceType=${searchModeSourceType(collectionMode, keywords, dataSource)} " +
                    "queryPreview=${queryPreviewFor(press, dataSource, keywords)} keyword=${keywordPreview(keywords)}"
            )
            var sourceTimedOut = false
            var sourceFailed = false
            val sourceTimeoutMs = minOf(
                sourceTimeoutBudgetMs(collectionMode, dataSource),
                remainingBeforeSourceMs
            )
            val result = try {
                withTimeoutOrNull(sourceTimeoutMs) {
                    dataSource.fetch(
                        press = press,
                        keywords = keywords
                    )
                } ?: run {
                    sourceTimedOut = true
                    timeoutStage = "source"
                    NewsDataSourceResult(
                        articles = emptyList(),
                        success = false,
                        message = "source timeout after ${sourceTimeoutMs}ms timeoutStage=source"
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                sourceFailed = true
                NewsDataSourceResult(
                    articles = emptyList(),
                    success = false,
                    message = error.message ?: UiText.NEWS_LOAD_FAILED
                )
            }

            val searchModeSourceType = searchModeSourceType(
                collectionMode = collectionMode,
                keywords = keywords,
                dataSource = dataSource
            )
            rawCandidateCount += result.articles.size
            failedReason = result.message ?: failedReason

            val validation = validateArticlesForPress(
                press = press,
                articles = result.articles,
                allowMissingBody = searchModeSourceType == SearchModeSourceType.KEYWORD_QUERY
            )
            val relevance = applySearchRelevance(
                press = press,
                keywords = keywords,
                collectionMode = collectionMode,
                searchModeSourceType = searchModeSourceType,
                articles = validation.articles
            )
            val articles = relevance.articles
            val sourceImageResolvedCount = imageResolvedCount(articles)
            val sourceBodyPreviewResolvedCount = bodyPreviewResolvedCount(articles)
            val sourceMatchedKeywordsPreview = matchedKeywordsPreview(articles)
            val sourceElapsedMs = nowMillis() - sourceStartedAt
            processedCandidateCount += validation.articles.size
            keywordMatchedCount += relevance.keywordMatchedCount
            validArticleCount += articles.size
            skippedInvalidCount += (result.articles.size - validation.articles.size).coerceAtLeast(0)
            skippedKeywordMismatchCount += relevance.skippedBecauseKeywordMismatchCount
            val success = result.success && articles.isNotEmpty()
            val sourceRejectReasons = sourceRejectReasons(
                result = result,
                validation = validation,
                keywordMismatchCount = relevance.skippedBecauseKeywordMismatchCount,
                sourceTimedOut = sourceTimedOut,
                sourceFailed = sourceFailed
            )
            val sourceLogName = when {
                sourceTimedOut -> "sourceTimeout"
                sourceFailed -> "sourceFailure"
                else -> "sourceEnd"
            }
            val sourceTimeoutStage = if (sourceTimedOut) "source" else "none"
            val sanitizedFailedReason = sanitizeReasonForLog(result.message)
            logCollectionPerf(
                "$sourceLogName press=${press.displayName} sourceName=${dataSource.sourceName} " +
                    "sourceType=${dataSource.sourceType} sourceElapsedMs=$sourceElapsedMs " +
                    "rawCandidateCount=${result.articles.size} processedCandidateCount=${validation.articles.size} " +
                    "keywordMatchedCount=${relevance.keywordMatchedCount} validArticleCount=${articles.size} " +
                    "skippedInvalidCount=${(result.articles.size - validation.articles.size).coerceAtLeast(0)} " +
                    "skippedBecauseKeywordMismatchCount=${relevance.skippedBecauseKeywordMismatchCount} " +
                    "imageResolvedCount=$sourceImageResolvedCount bodyPreviewResolvedCount=$sourceBodyPreviewResolvedCount " +
                    "matchedKeywordsPreview=$sourceMatchedKeywordsPreview " +
                    "timeoutOccurred=$sourceTimedOut timeoutStage=$sourceTimeoutStage fallbackStarted=$fallbackStarted " +
                    "topRejectReasons=${sourceRejectReasons.joinToString("|")} failedReason=$sanitizedFailedReason"
            )
            logSearchPerf(
                collectionMode = collectionMode,
                message = "$sourceLogName press=${press.displayName} sourceName=${dataSource.sourceName} " +
                    "searchModeSourceType=$searchModeSourceType queryPreview=${queryPreviewFor(press, dataSource, keywords)} " +
                    "rawCandidateCount=${result.articles.size} processedCandidateCount=${validation.articles.size} " +
                    "keywordMatchedCount=${relevance.keywordMatchedCount} validArticleCount=${articles.size} " +
                    "finalArticleCount=${articles.size} skippedBecauseKeywordMismatchCount=${relevance.skippedBecauseKeywordMismatchCount} " +
                    "imageResolvedCount=$sourceImageResolvedCount bodyPreviewResolvedCount=$sourceBodyPreviewResolvedCount " +
                    "matchedKeywordsPreview=$sourceMatchedKeywordsPreview " +
                    "timeoutOccurred=$sourceTimedOut timeoutStage=$sourceTimeoutStage"
            )
            logCollectionDiag(
                "press=${press.displayName} sourceName=${dataSource.sourceName} " +
                    "rawCandidateCount=${result.articles.size} processedCandidateCount=${validation.articles.size} " +
                    "keywordMatchedCount=${relevance.keywordMatchedCount} validArticleCount=${articles.size} " +
                    "timeoutStage=$sourceTimeoutStage " +
                    "failedReason=$sanitizedFailedReason rejectReasons=${sourceRejectReasons.joinToString("|")}"
            )
            if (press == NewsPress.YTN) {
                NewsFetchLogger.logYtnRepository(
                    "source=${dataSource.sourceType} sourceName=${dataSource.sourceName} " +
                        "resultSuccess=${result.success} rawArticleCount=${result.articles.size} " +
                        "displayableArticleCount=${articles.size} success=$success " +
                        "message=$sanitizedFailedReason"
                )
            }
            statuses += NewsSourceStatus(
                press = press,
                sourceType = dataSource.sourceType,
                sourceName = dataSource.sourceName,
                success = success,
                articleCount = articles.size,
                message = result.message
            )

            if (success && collectionMode == CollectionMode.MAIN_DEFAULT) {
                val mergedArticles = NewsDeduplicator
                    .deduplicate(collectedArticles + articles)
                    .take(NewsFilter.MAX_ARTICLES_PER_PRESS)
                collectedArticles.clear()
                collectedArticles += mergedArticles
                if (collectedArticles.size < NewsFilter.MAX_ARTICLES_PER_PRESS) {
                    continue
                }
            }

            if (success && isKeywordSearch(collectionMode, keywords)) {
                val mergedArticles = NewsDeduplicator
                    .deduplicate(collectedArticles + articles)
                    .take(NewsFilter.MAX_ARTICLES_PER_PRESS)
                collectedArticles.clear()
                collectedArticles += mergedArticles
                if (collectedArticles.size < NewsFilter.MAX_ARTICLES_PER_PRESS && sourceIndex + 1 < sources.size) {
                    continue
                }
                return finishPressCollectionResult(
                    press = press,
                    pressStartedAt = pressStartedAt,
                    articles = collectedArticles,
                    statuses = statuses,
                    collectionMode = collectionMode,
                    rawCandidateCount = rawCandidateCount,
                    processedCandidateCount = processedCandidateCount,
                    keywordMatchedCount = keywordMatchedCount,
                    validArticleCount = validArticleCount,
                    skippedInvalidCount = skippedInvalidCount,
                    skippedKeywordMismatchCount = skippedKeywordMismatchCount,
                    fallbackStarted = fallbackStarted,
                    fallbackSkippedReason = if (sourceIndex + 1 < sources.size) {
                        "keyword_article_limit_reached"
                    } else {
                        null
                    },
                    failedReason = null
                )
            }

            if (success) {
                return finishPressCollectionResult(
                    press = press,
                    pressStartedAt = pressStartedAt,
                    articles = collectedArticles.ifEmpty { articles },
                    statuses = statuses,
                    collectionMode = collectionMode,
                    rawCandidateCount = rawCandidateCount,
                    processedCandidateCount = processedCandidateCount,
                    keywordMatchedCount = keywordMatchedCount,
                    validArticleCount = validArticleCount,
                    skippedInvalidCount = skippedInvalidCount,
                    skippedKeywordMismatchCount = skippedKeywordMismatchCount,
                    fallbackStarted = fallbackStarted,
                    fallbackSkippedReason = if (sourceIndex + 1 < sources.size) {
                        if (collectionMode == CollectionMode.MAIN_DEFAULT) {
                            "valid_article_limit_reached"
                        } else {
                            "source_success"
                        }
                    } else {
                        null
                    },
                    failedReason = null
                )
            }
        }

        if (collectionMode == CollectionMode.MAIN_DEFAULT && collectedArticles.isNotEmpty()) {
            return finishPressCollectionResult(
                press = press,
                pressStartedAt = pressStartedAt,
                articles = collectedArticles,
                statuses = statuses,
                collectionMode = collectionMode,
                rawCandidateCount = rawCandidateCount,
                processedCandidateCount = processedCandidateCount,
                keywordMatchedCount = keywordMatchedCount,
                validArticleCount = validArticleCount,
                skippedInvalidCount = skippedInvalidCount,
                skippedKeywordMismatchCount = skippedKeywordMismatchCount,
                fallbackStarted = fallbackStarted,
                fallbackSkippedReason = null,
                failedReason = null,
                timeoutOccurred = timeoutStage != null,
                timeoutStage = timeoutStage ?: "none",
                topRejectReasons = if (timeoutStage != null) DiagnosticRejectReason.TIMEOUT.name else "not_tracked"
            )
        }

        if (isKeywordSearch(collectionMode, keywords) && collectedArticles.isNotEmpty()) {
            return finishPressCollectionResult(
                press = press,
                pressStartedAt = pressStartedAt,
                articles = collectedArticles,
                statuses = statuses,
                collectionMode = collectionMode,
                rawCandidateCount = rawCandidateCount,
                processedCandidateCount = processedCandidateCount,
                keywordMatchedCount = keywordMatchedCount,
                validArticleCount = validArticleCount,
                skippedInvalidCount = skippedInvalidCount,
                skippedKeywordMismatchCount = skippedKeywordMismatchCount,
                fallbackStarted = fallbackStarted,
                fallbackSkippedReason = null,
                failedReason = null,
                timeoutOccurred = timeoutStage != null,
                timeoutStage = timeoutStage ?: "none",
                topRejectReasons = if (timeoutStage != null) DiagnosticRejectReason.TIMEOUT.name else "not_tracked"
            )
        }

        statuses += NewsSourceStatus(
            press = press,
            sourceType = NewsSourceType.NOT_FOUND,
            sourceName = "RSS -> HTML",
            success = false,
            articleCount = 0,
            message = "RSS/HTML collection did not find displayable articles."
        )
        return finishPressCollectionResult(
            press = press,
            pressStartedAt = pressStartedAt,
            articles = emptyList(),
            statuses = statuses,
            collectionMode = collectionMode,
            rawCandidateCount = rawCandidateCount,
            processedCandidateCount = processedCandidateCount,
            keywordMatchedCount = keywordMatchedCount,
            validArticleCount = validArticleCount,
            skippedInvalidCount = skippedInvalidCount,
            skippedKeywordMismatchCount = skippedKeywordMismatchCount,
            fallbackStarted = fallbackStarted,
            fallbackSkippedReason = null,
            failedReason = failedReason,
            timeoutOccurred = timeoutStage != null,
            timeoutStage = timeoutStage ?: "none",
            topRejectReasons = if (timeoutStage != null) DiagnosticRejectReason.TIMEOUT.name else DiagnosticRejectReason.UNKNOWN.name
        )
    }

    private fun finishPressCollectionResult(
        press: NewsPress,
        pressStartedAt: Long,
        articles: List<NewsArticle>,
        statuses: List<NewsSourceStatus>,
        collectionMode: CollectionMode,
        rawCandidateCount: Int,
        processedCandidateCount: Int,
        keywordMatchedCount: Int,
        validArticleCount: Int,
        skippedInvalidCount: Int,
        skippedKeywordMismatchCount: Int,
        fallbackStarted: Boolean,
        fallbackSkippedReason: String?,
        failedReason: String?,
        timeoutOccurred: Boolean = false,
        timeoutStage: String = "none",
        topRejectReasons: String = "not_tracked"
    ): PressCollectionResult {
        val elapsedMs = nowMillis() - pressStartedAt
        val sanitizedFailedReason = sanitizeReasonForLog(failedReason)
        val finalImageResolvedCount = imageResolvedCount(articles)
        val finalBodyPreviewResolvedCount = bodyPreviewResolvedCount(articles)
        val finalMatchedKeywordsPreview = matchedKeywordsPreview(articles)
        logCollectionPerf(
            "pressEnd press=${press.displayName} collectionMode=$collectionMode " +
                "rawCandidateCount=$rawCandidateCount processedCandidateCount=$processedCandidateCount " +
                "keywordMatchedCount=$keywordMatchedCount validArticleCount=$validArticleCount " +
                "skippedInvalidCount=$skippedInvalidCount skippedBecauseKeywordMismatchCount=$skippedKeywordMismatchCount " +
                "skippedDuplicateCount=0 skippedDomainMismatchCount=0 redirectFailCount=0 detailFailCount=0 " +
                "imageResolvedCount=$finalImageResolvedCount bodyPreviewResolvedCount=$finalBodyPreviewResolvedCount " +
                "matchedKeywordsPreview=$finalMatchedKeywordsPreview " +
                "fallbackStarted=$fallbackStarted fallbackSkippedReason=${fallbackSkippedReason.orEmpty()} " +
                "stoppedBecauseLimitReached=false timeoutOccurred=$timeoutOccurred timeoutStage=$timeoutStage " +
                "partialArticleCountBeforeTimeout=${if (timeoutOccurred) articles.size else 0} " +
                "topRejectReasons=$topRejectReasons finalArticleCount=${articles.size} elapsedMs=$elapsedMs " +
                "failedReason=$sanitizedFailedReason"
        )
        logSearchPerf(
            collectionMode = collectionMode,
            message = "pressEnd press=${press.displayName} rawCandidateCount=$rawCandidateCount " +
                "processedCandidateCount=$processedCandidateCount keywordMatchedCount=$keywordMatchedCount " +
                "validArticleCount=$validArticleCount finalArticleCount=${articles.size} " +
                "skippedBecauseKeywordMismatchCount=$skippedKeywordMismatchCount " +
                "imageResolvedCount=$finalImageResolvedCount bodyPreviewResolvedCount=$finalBodyPreviewResolvedCount " +
                "matchedKeywordsPreview=$finalMatchedKeywordsPreview " +
                "timeoutOccurred=$timeoutOccurred timeoutStage=$timeoutStage"
        )
        logCollectionDiag(
            "press=${press.displayName} sourceName=ALL rawCandidateCount=$rawCandidateCount " +
                "processedCandidateCount=$processedCandidateCount keywordMatchedCount=$keywordMatchedCount " +
                "validArticleCount=$validArticleCount skippedBecauseKeywordMismatchCount=$skippedKeywordMismatchCount " +
                "timeoutStage=$timeoutStage failedReason=$sanitizedFailedReason rejectReasons=$topRejectReasons"
        )
        return PressCollectionResult(
            press = press,
            articles = articles,
            statuses = statuses,
            elapsedMs = elapsedMs,
            terminalStatus = if (timeoutOccurred) {
                PressCollectionTerminalStatus.TIMEOUT
            } else if (articles.isEmpty()) {
                PressCollectionTerminalStatus.FAILED
            } else {
                PressCollectionTerminalStatus.COMPLETED
            }
        )
    }

    private fun failedPressCollectionResult(
        press: NewsPress,
        error: Exception,
        elapsedMs: Long = 0L,
        collectionMode: CollectionMode
    ): PressCollectionResult {
        val message = error.message ?: error::class.simpleName ?: "press collection failed"
        val sanitizedMessage = sanitizeReasonForLog(message)
        logCollectionPerf(
            "pressFailure press=${press.displayName} collectionMode=$collectionMode " +
                "rawCandidateCount=0 processedCandidateCount=0 validArticleCount=0 timeoutOccurred=false " +
                "timeoutStage=none fallbackStarted=false finalArticleCount=0 elapsedMs=$elapsedMs " +
                "topRejectReasons=${DiagnosticRejectReason.UNKNOWN.name} failedReason=$sanitizedMessage"
        )
        logSearchPerf(
            collectionMode = collectionMode,
            message = "pressFailure press=${press.displayName} finalArticleCount=0 failedReason=$sanitizedMessage"
        )
        logCollectionDiag(
            "press=${press.displayName} sourceName=ALL rawCandidateCount=0 processedCandidateCount=0 " +
                "validArticleCount=0 timeoutStage=none failedReason=$sanitizedMessage " +
                "rejectReasons=${DiagnosticRejectReason.UNKNOWN.name}"
        )
        return PressCollectionResult(
            press = press,
            articles = emptyList(),
            statuses = listOf(
                NewsSourceStatus(
                    press = press,
                    sourceType = NewsSourceType.NOT_FOUND,
                    sourceName = "RSS -> HTML",
                    success = false,
                    articleCount = 0,
                    message = message
                )
            ),
            elapsedMs = elapsedMs,
            terminalStatus = PressCollectionTerminalStatus.FAILED
        )
    }

    private fun timeoutPressCollectionResult(
        press: NewsPress,
        elapsedMs: Long,
        message: String,
        collectionMode: CollectionMode
    ): PressCollectionResult {
        val sanitizedMessage = sanitizeReasonForLog(message)
        logCollectionPerf(
            "pressTimeout press=${press.displayName} collectionMode=$collectionMode " +
                "rawCandidateCount=0 processedCandidateCount=0 validArticleCount=0 timeoutOccurred=true " +
                "timeoutStage=main fallbackStarted=false partialArticleCountBeforeTimeout=0 " +
                "finalArticleCount=0 elapsedMs=$elapsedMs topRejectReasons=${DiagnosticRejectReason.TIMEOUT.name} " +
                "failedReason=$sanitizedMessage"
        )
        logSearchPerf(
            collectionMode = collectionMode,
            message = "timeoutOccurred timeoutStage=main press=${press.displayName} finalArticleCount=0"
        )
        logCollectionDiag(
            "press=${press.displayName} sourceName=ALL rawCandidateCount=0 processedCandidateCount=0 " +
                "validArticleCount=0 timeoutStage=main failedReason=$sanitizedMessage " +
                "rejectReasons=${DiagnosticRejectReason.TIMEOUT.name}"
        )
        return PressCollectionResult(
            press = press,
            articles = emptyList(),
            statuses = listOf(
                NewsSourceStatus(
                    press = press,
                    sourceType = NewsSourceType.NOT_FOUND,
                    sourceName = "RSS -> HTML",
                    success = false,
                    articleCount = 0,
                    message = message
                )
            ),
            elapsedMs = elapsedMs,
            terminalStatus = PressCollectionTerminalStatus.TIMEOUT
        )
    }

    private fun mainTimeoutResults(
        targetPresses: List<NewsPress>,
        completedResults: List<PressCollectionResult>,
        startedAt: Long,
        collectionMode: CollectionMode
    ): List<PressCollectionResult> {
        val elapsedMs = nowMillis() - startedAt
        val completedByPress = completedResults.associateBy { result -> result.press }
        logCollectionLifecycle(
            "repository main collection timeout elapsedMs=$elapsedMs " +
                "completedPressCount=${completedResults.size} targetPressCount=${targetPresses.size}"
        )
        return targetPresses.map { press ->
            completedByPress[press] ?: timeoutPressCollectionResult(
                press = press,
                elapsedMs = elapsedMs,
                message = "main collection timeout after ${NewsFetchConfig.MAIN_COLLECTION_TIMEOUT_MS}ms timeoutStage=main",
                collectionMode = collectionMode
            )
        }
    }

    private fun rememberArticles(articles: List<NewsArticle>) {
        if (articles.isEmpty()) return
        synchronized(cacheLock) {
            cachedArticles = NewsDeduplicator.deduplicate(cachedArticles + articles)
        }
    }

    private fun PressCollectionResult.toCollectionEvent(): NewsCollectionEvent {
        val reason = statuses.lastOrNull()?.message
        return when (terminalStatus) {
            PressCollectionTerminalStatus.COMPLETED ->
                NewsCollectionEvent.PressCollectionCompleted(
                    press = press,
                    articles = articles,
                    sourceStatuses = statuses
                )
            PressCollectionTerminalStatus.FAILED ->
                NewsCollectionEvent.PressCollectionFailed(
                    press = press,
                    reason = reason,
                    partialArticles = articles,
                    sourceStatuses = statuses
                )
            PressCollectionTerminalStatus.TIMEOUT ->
                NewsCollectionEvent.PressCollectionTimeout(
                    press = press,
                    reason = reason,
                    partialArticles = articles,
                    sourceStatuses = statuses
                )
        }
    }

    private fun resolveActualPressesForCollection(selectedPresses: Set<NewsPress>): List<NewsPress> {
        if (selectedPresses.isEmpty()) return emptyList()
        val articlePresses = NewsPress.articlePresses()
        if (NewsPress.ALL in selectedPresses) return articlePresses
        val normalized = NewsFilter.normalizeSelectedPresses(selectedPresses)
        return articlePresses.filter { press -> press in normalized }
    }

    private fun dataSourcesForMode(
        collectionMode: CollectionMode,
        keywords: List<String>
    ): List<NewsDataSource> {
        if (!isKeywordSearch(collectionMode, keywords)) {
            return dataSources
        }
        val (keywordQuerySources, latestFallbackSources) = dataSources.partition(::isKeywordQuerySource)
        return keywordQuerySources + latestFallbackSources
    }

    private fun isKeywordSearch(
        collectionMode: CollectionMode,
        keywords: List<String>
    ): Boolean =
        collectionMode == CollectionMode.SEARCH && NewsFilter.normalizeKeywords(keywords).isNotEmpty()

    private fun isKeywordQuerySource(dataSource: NewsDataSource): Boolean =
        dataSource.sourceType == NewsSourceType.RSS &&
            dataSource.sourceName.contains("Google News", ignoreCase = true)

    private fun searchModeSourceType(
        collectionMode: CollectionMode,
        keywords: List<String>,
        dataSource: NewsDataSource
    ): SearchModeSourceType =
        if (isKeywordSearch(collectionMode, keywords) && isKeywordQuerySource(dataSource)) {
            SearchModeSourceType.KEYWORD_QUERY
        } else {
            SearchModeSourceType.LATEST_FALLBACK
        }

    private fun applySearchRelevance(
        press: NewsPress,
        keywords: List<String>,
        collectionMode: CollectionMode,
        searchModeSourceType: SearchModeSourceType,
        articles: List<NewsArticle>
    ): SearchRelevanceResult {
        if (!isKeywordSearch(collectionMode, keywords)) {
            return SearchRelevanceResult(
                articles = articles.map { article -> article.copy(matchedKeywords = emptyList()) },
                keywordMatchedCount = articles.size,
                skippedBecauseKeywordMismatchCount = 0
            )
        }

        if (searchModeSourceType == SearchModeSourceType.KEYWORD_QUERY) {
            val searchKeywords = NewsFilter.normalizeKeywords(keywords)
            val searchKeywordLabels = NewsFilter.normalizeKeywordLabels(keywords)
            return SearchRelevanceResult(
                articles = articles
                    .map { article ->
                        val matchedKeywords = NewsFilter
                            .matchedKeywords(article, searchKeywordLabels)
                            .ifEmpty { searchKeywordLabels }
                        article.copy(
                            keywords = (article.keywords + searchKeywords).distinct(),
                            matchedKeywords = matchedKeywords
                        )
                    }
                    .take(NewsFilter.MAX_ARTICLES_PER_PRESS),
                keywordMatchedCount = articles.size,
                skippedBecauseKeywordMismatchCount = 0
            )
        }

        val keywordMatchedArticles = NewsFilter.filter(
            articles = articles,
            selectedPresses = setOf(press),
            keywords = keywords,
            maxArticlesPerPress = Int.MAX_VALUE
        )
        return SearchRelevanceResult(
            articles = keywordMatchedArticles
                .map { article ->
                    article.copy(matchedKeywords = NewsFilter.matchedKeywords(article, keywords))
                }
                .take(NewsFilter.MAX_ARTICLES_PER_PRESS),
            keywordMatchedCount = keywordMatchedArticles.size,
            skippedBecauseKeywordMismatchCount = (articles.size - keywordMatchedArticles.size).coerceAtLeast(0)
        )
    }

    private fun queryPreviewFor(
        press: NewsPress,
        dataSource: NewsDataSource,
        keywords: List<String>
    ): String =
        if (isKeywordQuerySource(dataSource)) {
            "${keywordPreview(keywords)} site:${primarySearchSiteFor(press)}".take(MAX_LOG_KEYWORD_LENGTH)
        } else {
            "latest_fallback"
        }

    private fun primarySearchSiteFor(press: NewsPress): String =
        when (press) {
            NewsPress.YONHAP -> "yna.co.kr"
            NewsPress.MBC -> "imnews.imbc.com"
            NewsPress.SBS -> "news.sbs.co.kr"
            NewsPress.KBS -> "news.kbs.co.kr"
            NewsPress.YTN -> "ytn.co.kr"
            NewsPress.ALL -> "none"
        }

    private fun remainingPressTimeMs(pressStartedAt: Long): Long =
        (NewsFetchConfig.PRESS_COLLECTION_TIMEOUT_MS - (nowMillis() - pressStartedAt)).coerceAtLeast(0L)

    private fun sourceTimeoutBudgetMs(
        collectionMode: CollectionMode,
        dataSource: NewsDataSource
    ): Long =
        if (collectionMode == CollectionMode.MAIN_DEFAULT) {
            when (dataSource.sourceType) {
                NewsSourceType.RSS -> if (dataSource.sourceName == "Google News RSS") {
                    NewsFetchConfig.MAIN_DEFAULT_GOOGLE_NEWS_RSS_SOURCE_TIMEOUT_MS
                } else {
                    NewsFetchConfig.MAIN_DEFAULT_RSS_SOURCE_TIMEOUT_MS
                }
                NewsSourceType.HTML_CRAWLING -> NewsFetchConfig.MAIN_DEFAULT_HTML_SOURCE_TIMEOUT_MS
                else -> NewsFetchConfig.SOURCE_COLLECTION_TIMEOUT_MS
            }
        } else {
            NewsFetchConfig.SOURCE_COLLECTION_TIMEOUT_MS
        }

    private fun sourceRejectReasons(
        result: NewsDataSourceResult,
        validation: SourceValidationResult,
        keywordMismatchCount: Int,
        sourceTimedOut: Boolean,
        sourceFailed: Boolean
    ): List<String> =
        when {
            sourceTimedOut -> listOf(DiagnosticRejectReason.TIMEOUT.name)
            sourceFailed -> listOf(DiagnosticRejectReason.UNKNOWN.name)
            keywordMismatchCount > 0 && validation.articles.isNotEmpty() -> listOf(DiagnosticRejectReason.KEYWORD_MISMATCH.name)
            result.articles.isNotEmpty() && validation.articles.isEmpty() -> validation.rejectReasons
            result.articles.isEmpty() && result.message.orEmpty().contains("redirect", ignoreCase = true) ->
                listOf(DiagnosticRejectReason.REDIRECT_RESOLVE_FAILED.name)
            result.articles.isEmpty() -> listOf(DiagnosticRejectReason.UNKNOWN.name)
            else -> validation.rejectReasons
        }.distinct()

    private fun validateArticlesForPress(
        press: NewsPress,
        articles: List<NewsArticle>,
        allowMissingBody: Boolean = false
    ): SourceValidationResult {
        val rejectReasons = mutableListOf<String>()
        val validArticles = articles
            .filter { article -> article.press == press }
            .map(ArticleQualityValidator::cleanAndValidate)
            .mapNotNull { check ->
                val article = check.article
                val reasons = check.result.reasons.joinToString("|")
                val effectiveValid = check.result.isValid ||
                    (allowMissingBody && check.hasOnlyMissingBodyBlockingReason())
                NewsFetchLogger.logArticleLinkCheck(
                    "press=${press.displayName} id=${article.id} title=${article.title} " +
                        "originalUrl=${article.originalUrl} normalizedOriginalUrl=${UrlNormalizer.normalize(article.originalUrl)} " +
                        "valid=$effectiveValid rawValid=${check.result.isValid} reasons=$reasons"
                )
                if (press == NewsPress.YTN) {
                    YtnPipelineDebugStore.current()?.recordValidation(
                        article = article,
                        check = check,
                        finalDisplay = effectiveValid
                    )
                }
                if (effectiveValid) {
                    article
                } else {
                    rejectReasons += check.result.reasons.map(::diagnosticReasonForValidationReason)
                    NewsFetchLogger.logValidation(
                        "excluded press=${press.displayName} id=${article.id} title=${article.title} " +
                            "url=${NewsFetchLogger.safeUrlForLog(article.originalUrl)} reasons=$reasons"
                    )
                    null
                }
            }

        return SourceValidationResult(
            articles = validArticles,
            rejectReasons = rejectReasons.distinct().ifEmpty { listOf(DiagnosticRejectReason.UNKNOWN.name) }
        )
    }

    private fun com.example.fakenews.util.ArticleQualityCheck.hasOnlyMissingBodyBlockingReason(): Boolean {
        val blockingReasons = result.reasons.filter(::isBlockingValidationReason)
        return blockingReasons.isNotEmpty() &&
            blockingReasons.all { reason -> reason == "body_missing_or_too_short" }
    }

    private fun isBlockingValidationReason(reason: String): Boolean =
        reason in blockingValidationReasons ||
            reason.startsWith("non_article_type")

    private fun diagnosticReasonForValidationReason(reason: String): String =
        when {
            reason == "title_empty" -> DiagnosticRejectReason.EMPTY_TITLE.name
            reason == "original_url_empty" -> DiagnosticRejectReason.EMPTY_URL.name
            reason == "domain_mismatch" -> DiagnosticRejectReason.DOMAIN_MISMATCH.name
            reason == "article_url_policy_mismatch" -> DiagnosticRejectReason.EXCLUDED_PATH.name
            reason.startsWith("non_article_type") -> DiagnosticRejectReason.EXCLUDED_PATH.name
            reason == "body_missing_or_too_short" -> DiagnosticRejectReason.DETAIL_PARSE_EMPTY.name
            reason == "body_only_boilerplate" -> DiagnosticRejectReason.ARTICLE_QUALITY_FAILED.name
            else -> DiagnosticRejectReason.ARTICLE_QUALITY_FAILED.name
        }

    private fun List<NewsPress>.displayNames(): String =
        joinToString { press -> press.displayName }

    private fun buildUserMessage(
        articles: List<NewsArticle>,
        failedPresses: List<NewsPress>
    ): String? =
        when {
            articles.isEmpty() && failedPresses.isNotEmpty() -> UiText.SOME_PRESS_ARTICLES_NOT_FOUND
            articles.isEmpty() -> UiText.NO_SEARCH_RESULTS
            failedPresses.isNotEmpty() -> "${UiText.SOME_PRESS_ARTICLES_NOT_FOUND}: ${failedPresses.displayNames()}"
            else -> null
        }

    private fun logCollectionPerf(message: String) {
        if (BuildConfig.DEBUG) {
            runCatching { Log.d(TAG_COLLECTION_PERF, message) }
        }
    }

    private fun logCollectionDiag(message: String) {
        if (BuildConfig.DEBUG) {
            runCatching { Log.d(TAG_COLLECTION_DIAG, message) }
        }
    }

    private fun logSearchPerf(
        collectionMode: CollectionMode,
        message: String
    ) {
        if (collectionMode == CollectionMode.SEARCH && BuildConfig.DEBUG) {
            runCatching { Log.d(TAG_SEARCH_PERF, message) }
        }
    }

    private fun logCollectionLifecycle(message: String) {
        if (BuildConfig.DEBUG) {
            runCatching { Log.d(TAG_COLLECTION_LIFECYCLE, message) }
        }
    }

    private fun keywordPreview(keywords: List<String>): String =
        keywords
            .map { keyword -> keyword.trim() }
            .filter { keyword -> keyword.isNotBlank() }
            .joinToString("|")
            .ifBlank { "none" }
            .take(MAX_LOG_KEYWORD_LENGTH)

    private fun sanitizeReasonForLog(reason: String?): String =
        reason.orEmpty()
            .replace(Regex("https?://\\S+")) { matchResult ->
                NewsFetchLogger.safeUrlForLog(matchResult.value)
            }
            .take(MAX_LOG_REASON_LENGTH)

    private fun imageResolvedCount(articles: List<NewsArticle>): Int =
        articles.count { article -> !article.imageUrl.isNullOrBlank() }

    private fun bodyPreviewResolvedCount(articles: List<NewsArticle>): Int =
        articles.count { article ->
            article.bodyParagraphs.any { paragraph -> paragraph.isNotBlank() } ||
                article.content.isNotBlank() ||
                article.summary.isNotBlank()
        }

    private fun matchedKeywordsPreview(articles: List<NewsArticle>): String =
        articles
            .flatMap { article -> article.matchedKeywords }
            .map { keyword -> keyword.trim() }
            .filter { keyword -> keyword.isNotBlank() }
            .distinct()
            .take(MAX_LOG_MATCHED_KEYWORDS)
            .joinToString("|")
            .ifBlank { "none" }
            .take(MAX_LOG_KEYWORD_LENGTH)

    private data class PressCollectionResult(
        val press: NewsPress,
        val articles: List<NewsArticle>,
        val statuses: List<NewsSourceStatus>,
        val elapsedMs: Long,
        val terminalStatus: PressCollectionTerminalStatus
    )

    private data class SourceValidationResult(
        val articles: List<NewsArticle>,
        val rejectReasons: List<String>
    )

    private data class SearchRelevanceResult(
        val articles: List<NewsArticle>,
        val keywordMatchedCount: Int,
        val skippedBecauseKeywordMismatchCount: Int
    )

    private enum class PressCollectionTerminalStatus {
        COMPLETED,
        FAILED,
        TIMEOUT
    }

    private enum class SearchModeSourceType {
        KEYWORD_QUERY,
        LATEST_FALLBACK
    }

    private enum class DiagnosticRejectReason {
        EMPTY_TITLE,
        EMPTY_URL,
        INVALID_URL,
        DOMAIN_MISMATCH,
        EXCLUDED_PATH,
        DUPLICATE_URL,
        DUPLICATE_TITLE,
        REDIRECT_RESOLVE_FAILED,
        DETAIL_FETCH_FAILED,
        DETAIL_PARSE_EMPTY,
        ARTICLE_QUALITY_FAILED,
        KEYWORD_MISMATCH,
        TIMEOUT,
        UNKNOWN
    }

    private companion object {
        const val TAG_COLLECTION_PERF = "NEWS_COLLECTION_PERF"
        const val TAG_COLLECTION_DIAG = "NEWS_COLLECTION_DIAG"
        const val TAG_COLLECTION_LIFECYCLE = "NEWS_COLLECTION_LIFECYCLE"
        const val TAG_SEARCH_PERF = "NEWS_SEARCH_PERF"
        const val MAX_LOG_REASON_LENGTH = 180
        const val MAX_LOG_KEYWORD_LENGTH = 80
        const val MAX_LOG_MATCHED_KEYWORDS = 6
        val blockingValidationReasons = setOf(
            "title_empty",
            "title_not_article_like",
            "original_url_empty",
            "domain_mismatch",
            "article_url_policy_mismatch",
            "body_missing_or_too_short",
            "body_only_boilerplate"
        )
    }
}

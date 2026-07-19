package com.example.fakenews.ui.main

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fakenews.BuildConfig
import com.example.fakenews.data.model.CollectionMode
import com.example.fakenews.data.model.NewsArticle
import com.example.fakenews.data.model.NewsFetchResult
import com.example.fakenews.data.model.NewsPress
import com.example.fakenews.data.model.NewsSourceStatus
import com.example.fakenews.data.remote.NewsFetchConfig
import com.example.fakenews.data.remote.NewsFetchLogger
import com.example.fakenews.data.remote.YtnPipelineDebugStore
import com.example.fakenews.data.remote.YtnUiStateDebugTrace
import com.example.fakenews.data.repository.NewsCollectionEvent
import com.example.fakenews.data.repository.NewsRepositoryProvider
import com.example.fakenews.data.repository.NewsRepositoryStatusProvider
import com.example.fakenews.domain.usecase.GetFilteredNewsUseCase
import com.example.fakenews.util.NewsFilter
import com.example.fakenews.util.UiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class MainViewModel(
    @Suppress("UNUSED_PARAMETER")
    newsRepositoryStatusProvider: NewsRepositoryStatusProvider? =
        NewsRepositoryProvider.repository as? NewsRepositoryStatusProvider,
    private val getFilteredNewsUseCase: GetFilteredNewsUseCase =
        GetFilteredNewsUseCase(NewsRepositoryProvider.repository)
) : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    private var loadRequestVersion: Long = 0L
    private var inFlightLoadRequestKey: LoadRequestKey? = null

    init {
        loadInitialNews()
    }

    fun onPressToggle(press: NewsPress) {
        var shouldInvalidateLoads = false
        _uiState.update { currentState ->
            val allArticlePresses = NewsPress.articlePresses().toSet()
            val nextPresses = when {
                press == NewsPress.ALL && currentState.selectedPresses == allArticlePresses -> emptySet()
                press == NewsPress.ALL -> allArticlePresses
                press in currentState.selectedPresses -> currentState.selectedPresses - press
                else -> currentState.selectedPresses + press
            }
            shouldInvalidateLoads = nextPresses.isEmpty()

            currentState.copy(
                selectedPresses = nextPresses,
                articles = if (nextPresses.isEmpty()) emptyList() else currentState.articles,
                mainArticles = if (nextPresses.isEmpty()) emptyList() else currentState.mainArticles,
                searchArticles = if (nextPresses.isEmpty()) emptyList() else currentState.searchArticles,
                activeKeywords = if (nextPresses.isEmpty()) emptyList() else currentState.activeKeywords,
                isSearchMode = if (nextPresses.isEmpty()) false else currentState.isSearchMode,
                sourceStatuses = if (nextPresses.isEmpty()) emptyList() else currentState.sourceStatuses,
                failedPresses = if (nextPresses.isEmpty()) emptyList() else currentState.failedPresses,
                fallbackPresses = if (nextPresses.isEmpty()) emptyList() else currentState.fallbackPresses,
                usedMockFallback = if (nextPresses.isEmpty()) false else currentState.usedMockFallback,
                collectingPresses = if (nextPresses.isEmpty()) emptySet() else currentState.collectingPresses,
                completedPresses = if (nextPresses.isEmpty()) emptySet() else currentState.completedPresses,
                failedPressReasons = if (nextPresses.isEmpty()) emptyMap() else currentState.failedPressReasons,
                isInitialLoading = if (nextPresses.isEmpty()) false else currentState.isInitialLoading,
                isRefreshing = if (nextPresses.isEmpty()) false else currentState.isRefreshing,
                errorMessage = null,
                emptyMessage = if (nextPresses.isEmpty()) UiText.NO_SELECTED_PRESS else null,
                infoMessage = null
            )
        }
        if (shouldInvalidateLoads) {
            loadRequestVersion += 1
            inFlightLoadRequestKey = null
        }
    }

    fun onKeywordInputChange(value: String) {
        _uiState.update { currentState ->
            currentState.copy(keywordInput = value)
        }
    }

    fun onAddKeyword() {
        var keywordAdded = false
        _uiState.update { currentState ->
            val keyword = currentState.keywordInput.trim()
            if (keyword.isEmpty() || currentState.registeredKeywords.any { it.equals(keyword, ignoreCase = true) }) {
                currentState.copy(keywordInput = "")
            } else {
                keywordAdded = true
                currentState.copy(
                    keywordInput = "",
                    registeredKeywords = currentState.registeredKeywords + keyword
                )
            }
        }
        if (keywordAdded) {
            loadNews(collectionMode = CollectionMode.SEARCH)
        }
    }

    fun onRemoveKeyword(keyword: String) {
        var removed = false
        var remainingKeywords: List<String> = emptyList()
        _uiState.update { currentState ->
            val nextKeywords = currentState.registeredKeywords.filterNot { it == keyword }
            removed = nextKeywords.size != currentState.registeredKeywords.size
            remainingKeywords = nextKeywords
            currentState.copy(
                registeredKeywords = nextKeywords
            )
        }
        if (!removed) return
        if (remainingKeywords.isEmpty()) {
            loadRequestVersion += 1
            inFlightLoadRequestKey = null
            _uiState.update { currentState ->
                currentState.copy(
                    articles = currentState.mainArticles,
                    searchArticles = emptyList(),
                    activeKeywords = emptyList(),
                    isSearchMode = false,
                    isLoading = false,
                    isRefreshing = false,
                    errorMessage = null,
                    emptyMessage = if (currentState.selectedPresses.isEmpty()) {
                        UiText.NO_SELECTED_PRESS
                    } else if (currentState.mainArticles.isEmpty()) {
                        UiText.NO_SEARCH_RESULTS
                    } else {
                        null
                    },
                    infoMessage = null
                )
            }
        } else {
            loadNews(collectionMode = CollectionMode.SEARCH)
        }
    }

    fun onSearchClick() {
        val hasKeywords = _uiState.value.registeredKeywords.any { keyword -> keyword.isNotBlank() }
        loadNews(
            collectionMode = if (hasKeywords) {
                CollectionMode.SEARCH
            } else {
                CollectionMode.MAIN_DEFAULT
            }
        )
    }

    fun onRefreshClick() {
        onSearchClick()
    }

    fun loadInitialNews() {
        loadNews(collectionMode = CollectionMode.MAIN_DEFAULT)
    }

    private fun loadNews(collectionMode: CollectionMode = CollectionMode.SEARCH) {
        val requestState = _uiState.value
        val requestKeywords = if (collectionMode == CollectionMode.SEARCH) {
            requestState.registeredKeywords
        } else {
            emptyList()
        }
        val requestKey = LoadRequestKey(
            selectedPresses = requestState.selectedPresses,
            keywords = requestKeywords.map { keyword -> keyword.trim() }.filter { keyword -> keyword.isNotBlank() },
            collectionMode = collectionMode
        )
        if (requestState.selectedPresses.isNotEmpty() &&
            inFlightLoadRequestKey == requestKey &&
            (requestState.isLoading || requestState.isRefreshing || requestState.isCollectionInProgress)
        ) {
            logLifecycle("load skipped duplicate mode=$collectionMode keywords=${requestKeywords.joinToString()}")
            return
        }
        if (requestState.selectedPresses.isNotEmpty()) {
            inFlightLoadRequestKey = requestKey
        }
        val requestVersion = ++loadRequestVersion
        viewModelScope.launch {
            fun isLatestRequest(): Boolean = requestVersion == loadRequestVersion
            fun updateIfLatest(transform: (MainUiState) -> MainUiState) {
                _uiState.update { state ->
                    if (isLatestRequest()) {
                        transform(state)
                    } else {
                        state
                    }
                }
            }

            logLifecycle(
                "load start mode=$collectionMode selected=${requestState.selectedPresses.joinToString { it.name }} " +
                    "keywords=${requestKeywords.joinToString()}"
            )
            if (requestState.selectedPresses.isEmpty()) {
                updateIfLatest { currentState ->
                    currentState.copy(
                        articles = emptyList(),
                        mainArticles = emptyList(),
                        searchArticles = emptyList(),
                        activeKeywords = emptyList(),
                        isSearchMode = false,
                        sourceStatuses = emptyList(),
                        failedPresses = emptyList(),
                        fallbackPresses = emptyList(),
                        usedMockFallback = false,
                        collectingPresses = emptySet(),
                        completedPresses = emptySet(),
                        failedPressReasons = emptyMap(),
                        isInitialLoading = false,
                        isRefreshing = false,
                        isLoading = false,
                        errorMessage = null,
                        emptyMessage = UiText.NO_SELECTED_PRESS,
                        infoMessage = null
                    )
                }
                logLifecycle("load skipped emptySelection isLoading=false")
                return@launch
            }

            val targetPresses = requestState.selectedPresses.toActualPresses()
            val targetPressSet = targetPresses.toSet()
            val accumulator = IncrementalCollectionAccumulator(targetPresses)
            val loadStartedAt = System.currentTimeMillis()
            var firstArticleDisplayed = false
            var finalEventReceived = false

            fun updateIncrementalState() {
                val articles = accumulator.mergedArticles(
                    targetPresses = targetPressSet,
                    keywords = requestKeywords
                )
                if (articles.isNotEmpty() && !firstArticleDisplayed) {
                    firstArticleDisplayed = true
                    logLifecycle(
                        "first article displayed mode=$collectionMode " +
                            "elapsedMs=${System.currentTimeMillis() - loadStartedAt} articleCount=${articles.size}"
                    )
                }
                val collectingPresses = accumulator.collectingPresses.toSet()
                val failedPresses = accumulator.failedPresses()
                val inProgress = collectingPresses.isNotEmpty()
                updateIfLatest { state ->
                    state.withCollectionArticles(
                        collectionMode = collectionMode,
                        articles = articles,
                        activeKeywords = requestKeywords
                    ).copy(
                        articles = articles,
                        sourceStatuses = accumulator.sourceStatuses(),
                        failedPresses = failedPresses,
                        fallbackPresses = emptyList(),
                        usedMockFallback = false,
                        collectingPresses = collectingPresses,
                        completedPresses = accumulator.completedPresses.toSet(),
                        failedPressReasons = accumulator.failedReasons.toMap(),
                        isLoading = articles.isEmpty() && inProgress,
                        isInitialLoading = articles.isEmpty() && inProgress && collectionMode == CollectionMode.MAIN_DEFAULT,
                        isRefreshing = inProgress && (collectionMode == CollectionMode.SEARCH || articles.isNotEmpty()),
                        errorMessage = null,
                        emptyMessage = null,
                        infoMessage = incrementalInfoMessage(
                            collectingPresses = collectingPresses,
                            failedPresses = failedPresses,
                            hasArticles = articles.isNotEmpty()
                        ),
                        lastUpdatedAt = if (articles.isNotEmpty()) System.currentTimeMillis() else state.lastUpdatedAt
                    )
                }
            }

            updateIfLatest { currentState ->
                currentState.withCollectionArticles(
                    collectionMode = collectionMode,
                    articles = if (collectionMode == CollectionMode.SEARCH) {
                        emptyList()
                    } else {
                        currentState.mainArticles
                    },
                    activeKeywords = requestKeywords
                ).copy(
                    isLoading = true,
                    collectingPresses = targetPressSet,
                    completedPresses = emptySet(),
                    failedPressReasons = emptyMap(),
                    isInitialLoading = collectionMode == CollectionMode.MAIN_DEFAULT,
                    isRefreshing = collectionMode == CollectionMode.SEARCH,
                    errorMessage = null,
                    emptyMessage = null,
                    infoMessage = null
                )
            }

            try {
                val finished = withTimeoutOrNull(NewsFetchConfig.MAIN_VIEWMODEL_TIMEOUT_MS) {
                    getFilteredNewsUseCase.collectByPress(
                        selectedPresses = requestState.selectedPresses,
                        keywords = requestKeywords,
                        collectionMode = collectionMode
                    ).collect { event ->
                        if (!isLatestRequest()) return@collect
                        when (event) {
                            is NewsCollectionEvent.PressCollectionStarted -> {
                                accumulator.recordStarted(event.press)
                                updateIncrementalState()
                            }
                            is NewsCollectionEvent.PressCollectionCompleted -> {
                                accumulator.recordCompleted(
                                    press = event.press,
                                    articles = event.articles,
                                    sourceStatuses = event.sourceStatuses
                                )
                                updateIncrementalState()
                            }
                            is NewsCollectionEvent.PressCollectionFailed -> {
                                accumulator.recordFailed(
                                    press = event.press,
                                    reason = event.reason,
                                    partialArticles = event.partialArticles,
                                    sourceStatuses = event.sourceStatuses
                                )
                                updateIncrementalState()
                            }
                            is NewsCollectionEvent.PressCollectionTimeout -> {
                                accumulator.recordFailed(
                                    press = event.press,
                                    reason = event.reason ?: UiText.NEWS_COLLECTION_TIMEOUT,
                                    partialArticles = event.partialArticles,
                                    sourceStatuses = event.sourceStatuses
                                )
                                updateIncrementalState()
                            }
                            is NewsCollectionEvent.AllPressCollectionFinished -> {
                                finalEventReceived = true
                                val result = event.result
                                val emptyMessage = emptyMessageFor(result)
                                val infoMessage = infoMessageFor(result)
                                recordYtnUiState(
                                    requestState = requestState,
                                    result = result,
                                    keywords = requestKeywords,
                                    emptyMessage = emptyMessage,
                                    infoMessage = infoMessage
                                )
                                logLifecycle(
                                    "load success mode=$collectionMode articleCount=${result.articles.size} " +
                                        "failedPressCount=${result.failedPresses.size} empty=${result.articles.isEmpty()} " +
                                        "elapsedMs=${System.currentTimeMillis() - loadStartedAt}"
                                )
                                updateIfLatest { state ->
                                    state.withCollectionArticles(
                                        collectionMode = collectionMode,
                                        articles = result.articles,
                                        activeKeywords = requestKeywords
                                    ).copy(
                                        articles = result.articles,
                                        sourceStatuses = result.sourceStatuses,
                                        failedPresses = result.failedPresses,
                                        fallbackPresses = result.fallbackPresses,
                                        usedMockFallback = result.usedMockFallback,
                                        collectingPresses = emptySet(),
                                        completedPresses = targetPressSet - result.failedPresses.toSet(),
                                        failedPressReasons = failureReasonsFrom(
                                            failedPresses = result.failedPresses,
                                            sourceStatuses = result.sourceStatuses
                                        ),
                                        isLoading = false,
                                        isInitialLoading = false,
                                        isRefreshing = false,
                                        errorMessage = null,
                                        emptyMessage = emptyMessage,
                                        infoMessage = infoMessage,
                                        lastUpdatedAt = if (result.articles.isNotEmpty()) {
                                            System.currentTimeMillis()
                                        } else {
                                            state.lastUpdatedAt
                                        }
                                    )
                                }
                            }
                        }
                    }
                    true
                } ?: false

                if (!finished || (!finalEventReceived && _uiState.value.collectingPresses.isNotEmpty())) {
                    val result = viewModelTimeoutResult(
                        requestState = requestState,
                        accumulator = accumulator,
                        requestKeywords = requestKeywords
                    )
                    val emptyMessage = emptyMessageFor(result)
                    val infoMessage = infoMessageFor(result)
                    recordYtnUiState(
                        requestState = requestState,
                        result = result,
                        keywords = requestKeywords,
                        emptyMessage = emptyMessage,
                        infoMessage = infoMessage
                    )
                    logLifecycle(
                        "load timeout mode=$collectionMode articleCount=${result.articles.size} " +
                            "failedPressCount=${result.failedPresses.size} elapsedMs=${System.currentTimeMillis() - loadStartedAt}"
                    )
                    updateIfLatest { state ->
                        state.withCollectionArticles(
                            collectionMode = collectionMode,
                            articles = result.articles,
                            activeKeywords = requestKeywords
                        ).copy(
                            articles = result.articles,
                            sourceStatuses = result.sourceStatuses,
                            failedPresses = result.failedPresses,
                            fallbackPresses = result.fallbackPresses,
                            usedMockFallback = result.usedMockFallback,
                            collectingPresses = emptySet(),
                            completedPresses = targetPressSet - result.failedPresses.toSet(),
                            failedPressReasons = accumulator.failedReasons.toMap(),
                            isLoading = false,
                            isInitialLoading = false,
                            isRefreshing = false,
                            errorMessage = null,
                            emptyMessage = emptyMessage,
                            infoMessage = infoMessage,
                            lastUpdatedAt = if (result.articles.isNotEmpty()) {
                                System.currentTimeMillis()
                            } else {
                                state.lastUpdatedAt
                            }
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                logLifecycle("load failure mode=$collectionMode error=${error.message.orEmpty()}")
                if (NewsPress.YTN in requestState.selectedPresses) {
                    NewsFetchLogger.logYtnUiState(
                        "selected=true articleCount=0 failedPress=true error=${error.message.orEmpty()}"
                    )
                }
                updateIfLatest { state ->
                    state.copy(
                        isLoading = false,
                        collectingPresses = emptySet(),
                        isInitialLoading = false,
                        isRefreshing = false,
                        errorMessage = error.message ?: UiText.NEWS_LOAD_FAILED,
                        emptyMessage = null,
                        infoMessage = null
                    )
                }
            } finally {
                if (inFlightLoadRequestKey == requestKey) {
                    inFlightLoadRequestKey = null
                }
                updateIfLatest { state ->
                    if (state.isLoading || state.isInitialLoading || state.isRefreshing) {
                        state.copy(
                            isLoading = false,
                            isInitialLoading = false,
                            isRefreshing = false
                        )
                    } else {
                        state
                    }
                }
                logLifecycle(
                    "load finally loading=false articleCount=${_uiState.value.articles.size} " +
                        "isLoading=${_uiState.value.isLoading}"
                )
            }
        }
    }

    private fun MainUiState.withCollectionArticles(
        collectionMode: CollectionMode,
        articles: List<NewsArticle>,
        activeKeywords: List<String>
    ): MainUiState =
        if (collectionMode == CollectionMode.SEARCH) {
            copy(
                articles = articles,
                searchArticles = articles,
                activeKeywords = activeKeywords,
                isSearchMode = true
            )
        } else {
            copy(
                articles = articles,
                mainArticles = articles,
                searchArticles = emptyList(),
                activeKeywords = emptyList(),
                isSearchMode = false
            )
        }

    private fun recordYtnUiState(
        requestState: MainUiState,
        result: NewsFetchResult,
        keywords: List<String>,
        emptyMessage: String?,
        infoMessage: String?
    ) {
        val ytnSelected = NewsPress.YTN in requestState.selectedPresses
        if (!ytnSelected) return

        YtnPipelineDebugStore.recordUiState(
            YtnUiStateDebugTrace(
                selected = true,
                uiArticleCount = result.articles.count { article -> article.press == NewsPress.YTN },
                failedPress = NewsPress.YTN in result.failedPresses,
                keywords = keywords,
                emptyMessage = emptyMessage,
                infoMessage = infoMessage
            )
        )
    }

    private fun emptyMessageFor(result: NewsFetchResult): String? =
        if (result.articles.isEmpty()) {
            result.message ?: UiText.NO_SEARCH_RESULTS
        } else {
            null
        }

    private fun viewModelTimeoutResult(
        requestState: MainUiState,
        accumulator: IncrementalCollectionAccumulator,
        requestKeywords: List<String>
    ): NewsFetchResult {
        val timedOutPresses = _uiState.value.collectingPresses.ifEmpty {
            requestState.selectedPresses.toActualPresses().toSet()
        }
        timedOutPresses.forEach { press ->
            accumulator.recordFailed(
                press = press,
                reason = UiText.NEWS_COLLECTION_TIMEOUT,
                partialArticles = emptyList(),
                sourceStatuses = emptyList()
            )
        }
        val targetPresses = requestState.selectedPresses.toActualPresses().toSet()
        val articles = accumulator.mergedArticles(
            targetPresses = targetPresses,
            keywords = requestKeywords
        )
        val failedPresses = accumulator.failedPresses()
        return NewsFetchResult(
            articles = articles,
            sourceStatuses = accumulator.sourceStatuses(),
            failedPresses = failedPresses,
            usedMockFallback = false,
            fallbackPresses = emptyList(),
            message = if (articles.isEmpty()) {
                UiText.NEWS_COLLECTION_TIMEOUT_RETRY
            } else if (failedPresses.isNotEmpty()) {
                "${UiText.SOME_PRESS_COLLECTION_TIMEOUT}: ${failedPresses.displayNames()}"
            } else {
                null
            }
        )
    }

    private fun Set<NewsPress>.toActualPresses(): List<NewsPress> {
        if (isEmpty()) return emptyList()
        val articlePresses = NewsPress.articlePresses()
        if (NewsPress.ALL in this) return articlePresses
        return articlePresses.filter { press -> press in this }
    }

    private fun logLifecycle(message: String) {
        if (BuildConfig.DEBUG) {
            runCatching { Log.d(TAG_COLLECTION_LIFECYCLE, "MainViewModel $message") }
        }
    }

    private fun infoMessageFor(result: NewsFetchResult): String? =
        if (result.articles.isNotEmpty() && result.failedPresses.isNotEmpty()) {
            result.message ?: UiText.SOME_PRESS_ARTICLES_NOT_FOUND
        } else {
            null
        }

    private fun incrementalInfoMessage(
        collectingPresses: Set<NewsPress>,
        failedPresses: List<NewsPress>,
        hasArticles: Boolean
    ): String? =
        when {
            !hasArticles && collectingPresses.isNotEmpty() -> null
            collectingPresses.isNotEmpty() -> "${UiText.COLLECTION_IN_PROGRESS_PREFIX}: ${collectingPresses.toList().displayNames()}"
            failedPresses.isNotEmpty() -> "${UiText.SOME_PRESS_ARTICLES_NOT_FOUND}: ${failedPresses.displayNames()}"
            else -> null
        }

    private fun failureReasonsFrom(
        failedPresses: List<NewsPress>,
        sourceStatuses: List<NewsSourceStatus>
    ): Map<NewsPress, String> =
        failedPresses.associateWith { press ->
            sourceStatuses
                .lastOrNull { status -> status.press == press && !status.success }
                ?.message
                ?: UiText.ARTICLE_COLLECTION_FAILED
        }

    private fun List<NewsPress>.displayNames(): String =
        joinToString { press -> press.displayName }

    private class IncrementalCollectionAccumulator(
        targetPresses: List<NewsPress>
    ) {
        val collectingPresses: MutableSet<NewsPress> = targetPresses.toMutableSet()
        val completedPresses: MutableSet<NewsPress> = linkedSetOf()
        val failedReasons: MutableMap<NewsPress, String> = linkedMapOf()
        private val articlesByPress: MutableMap<NewsPress, List<NewsArticle>> = linkedMapOf()
        private val sourceStatusesByPress: MutableMap<NewsPress, List<NewsSourceStatus>> = linkedMapOf()

        fun recordStarted(press: NewsPress) {
            collectingPresses += press
        }

        fun recordCompleted(
            press: NewsPress,
            articles: List<NewsArticle>,
            sourceStatuses: List<NewsSourceStatus>
        ) {
            collectingPresses -= press
            completedPresses += press
            failedReasons -= press
            articlesByPress[press] = articles
            sourceStatusesByPress[press] = sourceStatuses
        }

        fun recordFailed(
            press: NewsPress,
            reason: String?,
            partialArticles: List<NewsArticle>,
            sourceStatuses: List<NewsSourceStatus>
        ) {
            collectingPresses -= press
            completedPresses -= press
            failedReasons[press] = reason ?: UiText.ARTICLE_COLLECTION_FAILED
            if (partialArticles.isNotEmpty()) {
                articlesByPress[press] = partialArticles
            }
            if (sourceStatuses.isNotEmpty()) {
                sourceStatusesByPress[press] = sourceStatuses
            }
        }

        fun mergedArticles(
            targetPresses: Set<NewsPress>,
            keywords: List<String>
        ): List<NewsArticle> =
            NewsFilter.filter(
                articles = articlesByPress.values.flatten(),
                selectedPresses = targetPresses,
                keywords = keywords
            )

        fun sourceStatuses(): List<NewsSourceStatus> =
            sourceStatusesByPress.values.flatten()

        fun failedPresses(): List<NewsPress> =
            failedReasons.keys.toList()
    }

    private data class LoadRequestKey(
        val selectedPresses: Set<NewsPress>,
        val keywords: List<String>,
        val collectionMode: CollectionMode
    )

    private companion object {
        const val TAG_COLLECTION_LIFECYCLE = "NEWS_COLLECTION_LIFECYCLE"
    }
}

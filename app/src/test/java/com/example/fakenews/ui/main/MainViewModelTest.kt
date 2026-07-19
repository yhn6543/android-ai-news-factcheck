package com.example.fakenews.ui.main

import com.example.fakenews.MainDispatcherRule
import com.example.fakenews.data.model.CollectionMode
import com.example.fakenews.data.model.NewsArticle
import com.example.fakenews.data.model.NewsFetchResult
import com.example.fakenews.data.model.NewsPress
import com.example.fakenews.data.model.NewsSourceStatus
import com.example.fakenews.data.model.NewsSourceType
import com.example.fakenews.data.remote.NewsFetchConfig
import com.example.fakenews.data.repository.MockNewsRepository
import com.example.fakenews.data.repository.NewsCollectionEvent
import com.example.fakenews.data.repository.NewsRepository
import com.example.fakenews.domain.usecase.GetFilteredNewsUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(repository: NewsRepository = MockNewsRepository()): MainViewModel =
        MainViewModel(
            newsRepositoryStatusProvider = null,
            getFilteredNewsUseCase = GetFilteredNewsUseCase(repository)
        )

    @Test
    fun defaultSelectedPressIsAllArticlePresses() = runTest {
        val viewModel = viewModel()

        assertEquals(NewsPress.articlePresses().toSet(), viewModel.uiState.value.selectedPresses)
        assertTrue(viewModel.uiState.value.isAllPressesSelected)
    }

    @Test
    fun togglingSelectedSpecificPressRemovesIt() = runTest {
        val viewModel = viewModel()

        viewModel.onPressToggle(NewsPress.MBC)

        assertEquals(NewsPress.articlePresses().toSet() - NewsPress.MBC, viewModel.uiState.value.selectedPresses)
        assertFalse(viewModel.uiState.value.isAllPressesSelected)
    }

    @Test
    fun removingLastSpecificPressKeepsEmptySelection() = runTest {
        val viewModel = viewModel()

        viewModel.onPressToggle(NewsPress.ALL)
        viewModel.onPressToggle(NewsPress.SBS)
        viewModel.onPressToggle(NewsPress.SBS)

        assertEquals(emptySet<NewsPress>(), viewModel.uiState.value.selectedPresses)
        assertEquals("선택된 뉴스사가 없습니다.", viewModel.uiState.value.emptyMessage)
    }

    @Test
    fun duplicateKeywordIsNotAdded() = runTest {
        val viewModel = viewModel()

        viewModel.onKeywordInputChange("경제")
        viewModel.onAddKeyword()
        viewModel.onKeywordInputChange("경제")
        viewModel.onAddKeyword()

        assertEquals(listOf("경제"), viewModel.uiState.value.registeredKeywords)
    }

    @Test
    fun blankKeywordIsNotAdded() = runTest {
        val viewModel = viewModel()

        viewModel.onKeywordInputChange("   ")
        viewModel.onAddKeyword()

        assertTrue(viewModel.uiState.value.registeredKeywords.isEmpty())
    }

    @Test
    fun removingKeywordUpdatesRegisteredKeywords() = runTest {
        val viewModel = viewModel()

        viewModel.onKeywordInputChange("경제")
        viewModel.onAddKeyword()
        viewModel.onKeywordInputChange("기후")
        viewModel.onAddKeyword()
        viewModel.onRemoveKeyword("경제")

        assertEquals(listOf("기후"), viewModel.uiState.value.registeredKeywords)
    }

    @Test
    fun initialLoadUpdatesSuccessState() = runTest {
        val viewModel = viewModel()

        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.articles.isNotEmpty())
        assertEquals(null, state.errorMessage)
        assertEquals(null, state.emptyMessage)
    }

    @Test
    fun emptySearchResultShowsEmptyMessage() = runTest {
        val viewModel = viewModel(
            repository = FakeNewsRepository(
                result = NewsFetchResult(
                    articles = emptyList(),
                    message = "검색 결과가 없습니다."
                )
            )
        )

        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.articles.isEmpty())
        assertEquals("검색 결과가 없습니다.", state.emptyMessage)
    }

    @Test
    fun repositoryFailureFinishesLoadingState() = runTest {
        val viewModel = viewModel(repository = ThrowingNewsRepository())

        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.articles.isEmpty())
        assertTrue(requireNotNull(state.errorMessage).contains("boom"))
    }

    @Test
    fun repositoryTimeoutFinishesLoadingState() = runTest {
        val viewModel = viewModel(
            repository = FakeNewsRepository(
                result = NewsFetchResult(articles = MockNewsRepository.mockArticles.take(1)),
                delayMillis = NewsFetchConfig.MAIN_VIEWMODEL_TIMEOUT_MS + 1_000
            )
        )

        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.articles.isEmpty())
        assertEquals(NewsPress.articlePresses().toSet(), state.failedPresses.toSet())
        assertTrue(requireNotNull(state.emptyMessage).isNotBlank())
    }

    @Test
    fun loadingStateIsVisibleWhileNewsRepositoryIsSuspended() = runTest {
        val viewModel = viewModel(
            repository = FakeNewsRepository(
                result = NewsFetchResult(articles = MockNewsRepository.mockArticles.take(1)),
                delayMillis = 1_000
            )
        )

        assertTrue(viewModel.uiState.value.isLoading)

        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.articles.isNotEmpty())
    }

    @Test
    fun completedPressArticlesAreDisplayedBeforeAllPressesFinish() = runTest {
        val repository = IncrementalFlowNewsRepository()
        val viewModel = viewModel(repository)

        mainDispatcherRule.testDispatcher.scheduler.runCurrent()

        val firstPressState = viewModel.uiState.value
        assertFalse(firstPressState.isLoading)
        assertEquals(listOf("incremental-mbc"), firstPressState.articles.map { article -> article.id })
        assertTrue(NewsPress.MBC in firstPressState.completedPresses)
        assertFalse(NewsPress.MBC in firstPressState.collectingPresses)
        assertTrue(firstPressState.collectingPresses.isNotEmpty())
        assertTrue(firstPressState.infoMessage?.contains("수집 중") == true)

        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        val finalState = viewModel.uiState.value
        assertFalse(finalState.isLoading)
        assertTrue(finalState.collectingPresses.isEmpty())
        assertEquals(
            listOf("incremental-sbs", "incremental-mbc"),
            finalState.articles.map { article -> article.id }
        )
    }

    @Test
    fun viewModelTimeoutKeepsAlreadyDisplayedPressArticles() = runTest {
        val repository = IncrementalFlowNewsRepository(emitFinalResult = false)
        val viewModel = viewModel(repository)

        mainDispatcherRule.testDispatcher.scheduler.runCurrent()
        assertEquals(listOf("incremental-mbc"), viewModel.uiState.value.articles.map { article -> article.id })

        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.collectingPresses.isEmpty())
        assertEquals(listOf("incremental-mbc"), state.articles.map { article -> article.id })
        assertTrue(NewsPress.MBC in state.completedPresses)
        assertTrue(state.failedPresses.isNotEmpty())
    }

    @Test
    fun searchClickAppliesSelectedPressAndRegisteredKeywords() = runTest {
        val viewModel = viewModel()

        viewModel.onPressToggle(NewsPress.ALL)
        viewModel.onPressToggle(NewsPress.MBC)
        viewModel.onKeywordInputChange("사회")
        viewModel.onAddKeyword()
        viewModel.onSearchClick()
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf("사회"), state.registeredKeywords)
        assertTrue(state.articles.isNotEmpty())
        assertTrue(state.articles.all { article -> article.press == NewsPress.MBC })
        assertTrue(
            state.articles.all { article ->
                article.title.contains("사회") ||
                    article.summary.contains("사회") ||
                    article.content.contains("사회") ||
                    article.keywords.any { keyword -> keyword.contains("사회") }
            }
        )
    }

    @Test
    fun searchClickWithoutKeywordsRequestsLatestArticlesForSelectedPresses() = runTest {
        val repository = RecordingNewsRepository(
            result = NewsFetchResult(articles = MockNewsRepository.mockArticles)
        )
        val viewModel = viewModel(repository)
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
        repository.searchRequests.clear()

        viewModel.onPressToggle(NewsPress.ALL)
        viewModel.onPressToggle(NewsPress.YTN)
        viewModel.onSearchClick()
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            listOf(SearchRequest(setOf(NewsPress.YTN), emptyList())),
            repository.searchRequests
        )
        assertEquals(setOf(NewsPress.YTN), viewModel.uiState.value.selectedPresses)
    }

    @Test
    fun refreshClickUsesSearchClickRequestState() = runTest {
        val repository = RecordingNewsRepository(
            result = NewsFetchResult(articles = MockNewsRepository.mockArticles)
        )
        val viewModel = viewModel(repository)
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
        repository.searchRequests.clear()

        viewModel.onPressToggle(NewsPress.ALL)
        viewModel.onPressToggle(NewsPress.SBS)
        viewModel.onKeywordInputChange("사회")
        viewModel.onAddKeyword()
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
        repository.searchRequests.clear()
        viewModel.onRefreshClick()
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            listOf(SearchRequest(setOf(NewsPress.SBS), listOf("사회"))),
            repository.searchRequests
        )
    }

    @Test
    fun keywordSearchRunsSearchCollectionInsteadOfFilteringMainArticles() = runTest {
        val repository = ModeAwareNewsRepository()
        val viewModel = viewModel(repository)
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("main-without-keyword"), viewModel.uiState.value.mainArticles.map { it.id })
        repository.requests.clear()

        viewModel.onKeywordInputChange("rare-keyword")
        viewModel.onAddKeyword()
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isSearchMode)
        assertEquals(listOf("rare-keyword"), state.activeKeywords)
        assertEquals(listOf("main-without-keyword"), state.mainArticles.map { it.id })
        assertEquals(listOf("search-rare-keyword"), state.searchArticles.map { it.id })
        assertEquals(listOf("search-rare-keyword"), state.articles.map { it.id })
        assertEquals(
            listOf(ModeRequest(NewsPress.articlePresses().toSet(), listOf("rare-keyword"), CollectionMode.SEARCH)),
            repository.requests
        )
    }

    @Test
    fun removingLastKeywordRestoresMainArticlesWithoutNewCollection() = runTest {
        val repository = ModeAwareNewsRepository()
        val viewModel = viewModel(repository)
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onKeywordInputChange("rare-keyword")
        viewModel.onAddKeyword()
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
        val requestCountAfterSearch = repository.requests.size

        viewModel.onRemoveKeyword("rare-keyword")

        val state = viewModel.uiState.value
        assertFalse(state.isSearchMode)
        assertTrue(state.searchArticles.isEmpty())
        assertTrue(state.activeKeywords.isEmpty())
        assertEquals(listOf("main-without-keyword"), state.articles.map { it.id })
        assertEquals(requestCountAfterSearch, repository.requests.size)
    }

    @Test
    fun emptySearchResultDoesNotShowMainArticles() = runTest {
        val repository = EmptySearchModeAwareNewsRepository()
        val viewModel = viewModel(repository)
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("main-without-keyword"), viewModel.uiState.value.mainArticles.map { it.id })

        viewModel.onKeywordInputChange("no-result-keyword")
        viewModel.onAddKeyword()
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.isSearchMode)
        assertTrue(state.articles.isEmpty())
        assertTrue(state.searchArticles.isEmpty())
        assertEquals(listOf("main-without-keyword"), state.mainArticles.map { it.id })
        assertTrue(requireNotNull(state.emptyMessage).isNotBlank())
    }

    @Test
    fun duplicateSearchRequestIsIgnoredWhileSameSearchIsInProgress() = runTest {
        val repository = DelayedSearchFlowRepository()
        val viewModel = viewModel(repository)
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
        repository.requestCount = 0

        viewModel.onKeywordInputChange("rare-keyword")
        viewModel.onAddKeyword()
        viewModel.onSearchClick()
        mainDispatcherRule.testDispatcher.scheduler.runCurrent()

        assertEquals(1, repository.requestCount)
    }

    private data class SearchRequest(
        val selectedPresses: Set<NewsPress>,
        val keywords: List<String>
    )

    private data class ModeRequest(
        val selectedPresses: Set<NewsPress>,
        val keywords: List<String>,
        val collectionMode: CollectionMode
    )

    private class ModeAwareNewsRepository : NewsRepository {
        val requests = mutableListOf<ModeRequest>()
        private val mainArticle = MockNewsRepository.mockArticles
            .first { article -> article.press == NewsPress.MBC }
            .copy(
                id = "main-without-keyword",
                title = "Main latest article",
                summary = "Latest summary without search term",
                content = "Latest body without search term",
                bodyParagraphs = listOf("Latest body without search term"),
                keywords = emptyList(),
                publishedAt = 1_000L,
                originalUrl = "https://imnews.imbc.com/news/2026/mock/main-without-keyword.html"
            )
        private val searchArticle = MockNewsRepository.mockArticles
            .first { article -> article.press == NewsPress.MBC }
            .copy(
                id = "search-rare-keyword",
                title = "rare-keyword search article",
                summary = "Search summary",
                content = "Search body",
                bodyParagraphs = listOf("Search body"),
                keywords = listOf("rare-keyword"),
                publishedAt = 2_000L,
                originalUrl = "https://imnews.imbc.com/news/2026/mock/search-rare-keyword.html"
            )

        override suspend fun getLatestNews(): NewsFetchResult =
            NewsFetchResult(articles = listOf(mainArticle))

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
            requests += ModeRequest(
                selectedPresses = selectedPresses,
                keywords = keywords,
                collectionMode = collectionMode
            )
            return if (collectionMode == CollectionMode.SEARCH) {
                NewsFetchResult(articles = listOf(searchArticle))
            } else {
                NewsFetchResult(articles = listOf(mainArticle))
            }
        }

        override suspend fun getNewsArticleById(articleId: String): NewsArticle? =
            listOf(mainArticle, searchArticle).firstOrNull { article -> article.id == articleId }
    }

    private class EmptySearchModeAwareNewsRepository : NewsRepository {
        private val mainArticle = MockNewsRepository.mockArticles
            .first { article -> article.press == NewsPress.MBC }
            .copy(
                id = "main-without-keyword",
                title = "Main latest article",
                summary = "Latest summary without search term",
                content = "Latest body without search term",
                bodyParagraphs = listOf("Latest body without search term"),
                keywords = emptyList(),
                publishedAt = 1_000L,
                originalUrl = "https://imnews.imbc.com/news/2026/mock/main-without-keyword.html"
            )

        override suspend fun getLatestNews(): NewsFetchResult =
            NewsFetchResult(articles = listOf(mainArticle))

        override suspend fun searchNews(
            selectedPresses: Set<NewsPress>,
            keywords: List<String>
        ): NewsFetchResult =
            searchNews(selectedPresses, keywords, CollectionMode.SEARCH)

        override suspend fun searchNews(
            selectedPresses: Set<NewsPress>,
            keywords: List<String>,
            collectionMode: CollectionMode
        ): NewsFetchResult =
            if (collectionMode == CollectionMode.SEARCH) {
                NewsFetchResult(articles = emptyList(), message = "empty")
            } else {
                NewsFetchResult(articles = listOf(mainArticle))
            }

        override suspend fun getNewsArticleById(articleId: String): NewsArticle? =
            mainArticle.takeIf { article -> article.id == articleId }
    }

    private class DelayedSearchFlowRepository : NewsRepository {
        var requestCount: Int = 0
        private val article = MockNewsRepository.mockArticles
            .first { item -> item.press == NewsPress.MBC }
            .copy(
                id = "delayed-search",
                title = "rare-keyword delayed search",
                keywords = listOf("rare-keyword"),
                originalUrl = "https://imnews.imbc.com/news/2026/mock/delayed-search.html"
            )

        override suspend fun getLatestNews(): NewsFetchResult =
            NewsFetchResult(articles = listOf(article))

        override suspend fun searchNews(
            selectedPresses: Set<NewsPress>,
            keywords: List<String>
        ): NewsFetchResult =
            NewsFetchResult(articles = listOf(article))

        override fun collectNewsByPressFlow(
            selectedPresses: Set<NewsPress>,
            keywords: List<String>,
            collectionMode: CollectionMode
        ): Flow<NewsCollectionEvent> = flow {
            requestCount += 1
            val press = NewsPress.MBC
            emit(NewsCollectionEvent.PressCollectionStarted(press))
            delay(1_000)
            emit(
                NewsCollectionEvent.PressCollectionCompleted(
                    press = press,
                    articles = listOf(article),
                    sourceStatuses = listOf(
                        NewsSourceStatus(
                            press = press,
                            sourceType = NewsSourceType.RSS,
                            sourceName = "Delayed search",
                            success = true,
                            articleCount = 1
                        )
                    )
                )
            )
            emit(NewsCollectionEvent.AllPressCollectionFinished(NewsFetchResult(articles = listOf(article))))
        }

        override suspend fun getNewsArticleById(articleId: String): NewsArticle? =
            article.takeIf { item -> item.id == articleId }
    }

    private class IncrementalFlowNewsRepository(
        private val emitFinalResult: Boolean = true
    ) : NewsRepository {
        private val mbcArticle = MockNewsRepository.mockArticles
            .first { article -> article.press == NewsPress.MBC }
            .copy(
                id = "incremental-mbc",
                title = "MBC incremental article",
                publishedAt = 1_000L,
                originalUrl = "https://imnews.imbc.com/news/2026/mock/incremental-mbc.html"
            )
        private val sbsArticle = MockNewsRepository.mockArticles
            .first { article -> article.press == NewsPress.SBS }
            .copy(
                id = "incremental-sbs",
                title = "SBS incremental article",
                publishedAt = 2_000L,
                originalUrl = "https://news.sbs.co.kr/news/endPage.do?news_id=incremental-sbs"
            )

        override suspend fun getLatestNews(): NewsFetchResult =
            finalResult(NewsPress.articlePresses().toSet())

        override suspend fun searchNews(
            selectedPresses: Set<NewsPress>,
            keywords: List<String>
        ): NewsFetchResult =
            finalResult(selectedPresses)

        override fun collectNewsByPressFlow(
            selectedPresses: Set<NewsPress>,
            keywords: List<String>,
            collectionMode: CollectionMode
        ): Flow<NewsCollectionEvent> = flow {
            val targetPresses = if (selectedPresses.isEmpty()) {
                emptySet()
            } else {
                selectedPresses
            }
            targetPresses.forEach { press ->
                emit(NewsCollectionEvent.PressCollectionStarted(press))
            }
            emit(
                NewsCollectionEvent.PressCollectionCompleted(
                    press = NewsPress.MBC,
                    articles = listOf(mbcArticle),
                    sourceStatuses = listOf(successStatus(NewsPress.MBC))
                )
            )
            delay(1_000)
            if (emitFinalResult) {
                emit(
                    NewsCollectionEvent.PressCollectionCompleted(
                        press = NewsPress.SBS,
                        articles = listOf(sbsArticle),
                        sourceStatuses = listOf(successStatus(NewsPress.SBS))
                    )
                )
                emit(NewsCollectionEvent.AllPressCollectionFinished(finalResult(targetPresses)))
            } else {
                delay(NewsFetchConfig.MAIN_VIEWMODEL_TIMEOUT_MS + 1_000)
            }
        }

        override suspend fun getNewsArticleById(articleId: String): NewsArticle? =
            listOf(mbcArticle, sbsArticle).firstOrNull { article -> article.id == articleId }

        private fun finalResult(selectedPresses: Set<NewsPress>): NewsFetchResult {
            val articles = listOf(sbsArticle, mbcArticle)
            val failedPresses = selectedPresses
                .filterNot { press -> press == NewsPress.MBC || press == NewsPress.SBS }
            return NewsFetchResult(
                articles = articles,
                sourceStatuses = listOf(
                    successStatus(NewsPress.MBC),
                    successStatus(NewsPress.SBS)
                ),
                failedPresses = failedPresses,
                message = if (failedPresses.isEmpty()) null else "partial"
            )
        }

        private fun successStatus(press: NewsPress): NewsSourceStatus =
            NewsSourceStatus(
                press = press,
                sourceType = NewsSourceType.RSS,
                sourceName = "Fake incremental RSS",
                success = true,
                articleCount = 1
            )
    }

    private class FakeNewsRepository(
        private val result: NewsFetchResult,
        private val delayMillis: Long = 0
    ) : NewsRepository {
        override suspend fun getLatestNews(): NewsFetchResult {
            if (delayMillis > 0) {
                delay(delayMillis)
            }
            return result
        }

        override suspend fun searchNews(
            selectedPresses: Set<NewsPress>,
            keywords: List<String>
        ): NewsFetchResult {
            if (delayMillis > 0) {
                delay(delayMillis)
            }
            return result
        }

        override suspend fun getNewsArticleById(articleId: String): NewsArticle? =
            result.articles.firstOrNull { article -> article.id == articleId }
    }

    private class RecordingNewsRepository(
        private val result: NewsFetchResult
    ) : NewsRepository {
        val searchRequests = mutableListOf<SearchRequest>()

        override suspend fun getLatestNews(): NewsFetchResult = result

        override suspend fun searchNews(
            selectedPresses: Set<NewsPress>,
            keywords: List<String>
        ): NewsFetchResult {
            searchRequests += SearchRequest(
                selectedPresses = selectedPresses,
                keywords = keywords
            )
            return result
        }

        override suspend fun getNewsArticleById(articleId: String): NewsArticle? =
            result.articles.firstOrNull { article -> article.id == articleId }
    }

    private class ThrowingNewsRepository : NewsRepository {
        override suspend fun getLatestNews(): NewsFetchResult =
            error("boom")

        override suspend fun searchNews(
            selectedPresses: Set<NewsPress>,
            keywords: List<String>
        ): NewsFetchResult =
            error("boom")

        override suspend fun getNewsArticleById(articleId: String): NewsArticle? = null
    }
}

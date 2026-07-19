package com.example.fakenews.ui.main

import com.example.fakenews.MainDispatcherRule
import com.example.fakenews.data.model.NewsArticle
import com.example.fakenews.data.model.NewsFetchResult
import com.example.fakenews.data.model.NewsPress
import com.example.fakenews.data.model.NewsSourceStatus
import com.example.fakenews.data.model.NewsSourceType
import com.example.fakenews.data.remote.YtnPipelineDebugStore
import com.example.fakenews.data.repository.MockNewsRepository
import com.example.fakenews.data.repository.NewsRepository
import com.example.fakenews.domain.usecase.GetFilteredNewsUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelYtnDisplayTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun ytnRepositoryArticlesReachUiStateAndRuntimeTrace() = runTest {
        val ytnArticle = MockNewsRepository.mockArticles
            .first { article -> article.press == NewsPress.YTN }
            .copy(
                originalUrl = "https://www.ytn.co.kr/_ln/0101_202606120001",
                title = "YTN runtime article reaches cards",
                content = "YTN runtime article body paragraph with enough detail for a card preview.",
                bodyParagraphs = listOf("YTN runtime article body paragraph with enough detail for a card preview."),
                sourceType = NewsSourceType.HTML_CRAWLING,
                sourceLabel = NewsSourceType.HTML_CRAWLING.displayName
            )
        val result = NewsFetchResult(
            articles = listOf(ytnArticle),
            sourceStatuses = listOf(
                NewsSourceStatus(
                    press = NewsPress.YTN,
                    sourceType = NewsSourceType.HTML_CRAWLING,
                    sourceName = NewsSourceType.HTML_CRAWLING.displayName,
                    success = true,
                    articleCount = 1
                )
            )
        )
        YtnPipelineDebugStore.start()

        val viewModel = MainViewModel(
            newsRepositoryStatusProvider = null,
            getFilteredNewsUseCase = GetFilteredNewsUseCase(FixedNewsRepository(result))
        )
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        val snapshot = YtnPipelineDebugStore.latestSnapshot()

        assertEquals(1, state.articles.count { article -> article.press == NewsPress.YTN })
        assertEquals(ytnArticle.originalUrl, state.articles.single { article -> article.press == NewsPress.YTN }.originalUrl)
        assertEquals(1, snapshot.uiStateTrace?.uiArticleCount)
        assertTrue(snapshot.uiStateTrace?.selected == true)
        assertFalse(snapshot.uiStateTrace?.failedPress == true)
    }

    private class FixedNewsRepository(
        private val result: NewsFetchResult
    ) : NewsRepository {
        override suspend fun getLatestNews(): NewsFetchResult = result

        override suspend fun searchNews(
            selectedPresses: Set<NewsPress>,
            keywords: List<String>
        ): NewsFetchResult = result

        override suspend fun getNewsArticleById(articleId: String): NewsArticle? =
            result.articles.firstOrNull { article -> article.id == articleId }
    }
}

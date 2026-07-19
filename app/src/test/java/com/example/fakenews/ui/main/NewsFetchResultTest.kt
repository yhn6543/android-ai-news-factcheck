package com.example.fakenews.ui.main

import com.example.fakenews.MainDispatcherRule
import com.example.fakenews.data.model.NewsArticle
import com.example.fakenews.data.model.NewsFetchResult
import com.example.fakenews.data.model.NewsPress
import com.example.fakenews.data.model.NewsSourceStatus
import com.example.fakenews.data.model.NewsSourceType
import com.example.fakenews.data.repository.MockNewsRepository
import com.example.fakenews.data.repository.NewsRepository
import com.example.fakenews.domain.usecase.GetFilteredNewsUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NewsFetchResultTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun failedPressesAreReflectedInUiStateForPartialFailure() = runTest {
        val article = MockNewsRepository.mockArticles.first { it.press == NewsPress.MBC }
        val result = NewsFetchResult(
            articles = listOf(article),
            sourceStatuses = listOf(notFoundStatus(NewsPress.KBS)),
            failedPresses = listOf(NewsPress.KBS),
            message = "일부 뉴스사의 기사를 찾을 수 없습니다: KBS"
        )
        val viewModel = viewModel(result)

        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf(NewsPress.KBS), state.failedPresses)
        assertEquals("일부 뉴스사의 기사를 찾을 수 없습니다: KBS", state.infoMessage)
        assertNull(state.emptyMessage)
        assertEquals(listOf(article), state.articles)
    }

    @Test
    fun totalFailureShowsEmptyMessage() = runTest {
        val result = NewsFetchResult(
            articles = emptyList(),
            sourceStatuses = listOf(notFoundStatus(NewsPress.YTN)),
            failedPresses = listOf(NewsPress.YTN),
            message = "기사를 찾을 수 없습니다."
        )
        val viewModel = viewModel(result)

        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.articles.isEmpty())
        assertEquals(listOf(NewsPress.YTN), state.failedPresses)
        assertEquals("기사를 찾을 수 없습니다.", state.emptyMessage)
        assertNull(state.infoMessage)
    }

    private fun viewModel(result: NewsFetchResult): MainViewModel =
        MainViewModel(
            newsRepositoryStatusProvider = null,
            getFilteredNewsUseCase = GetFilteredNewsUseCase(FixedNewsRepository(result))
        )

    private fun notFoundStatus(press: NewsPress): NewsSourceStatus =
        NewsSourceStatus(
            press = press,
            sourceType = NewsSourceType.NOT_FOUND,
            sourceName = "RSS -> HTML",
            success = false,
            articleCount = 0,
            message = "not found"
        )

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

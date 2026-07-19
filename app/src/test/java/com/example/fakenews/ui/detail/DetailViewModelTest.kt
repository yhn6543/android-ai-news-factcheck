package com.example.fakenews.ui.detail

import com.example.fakenews.MainDispatcherRule
import com.example.fakenews.data.model.NewsArticle
import com.example.fakenews.data.model.NewsFetchResult
import com.example.fakenews.data.model.NewsPress
import com.example.fakenews.data.repository.ArticleDetailRepository
import com.example.fakenews.data.repository.MockNewsRepository
import com.example.fakenews.data.repository.NewsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loadArticleFindsArticleById() = runTest {
        val expected = MockNewsRepository.mockArticles.first()
        val viewModel = DetailViewModel(
            repository = SingleArticleNewsRepository(expected),
            articleDetailRepository = NoOpArticleDetailRepository()
        )

        viewModel.loadArticle(expected.id)

        assertEquals(expected, viewModel.uiState.value.article)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun loadArticleShowsErrorForMissingId() = runTest {
        val viewModel = DetailViewModel(articleDetailRepository = NoOpArticleDetailRepository())

        viewModel.loadArticle("missing-id")

        assertNull(viewModel.uiState.value.article)
        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals("기사를 찾을 수 없습니다.", viewModel.uiState.value.emptyMessage)
    }

    @Test
    fun loadArticleShowsErrorForBlankId() = runTest {
        val viewModel = DetailViewModel(articleDetailRepository = NoOpArticleDetailRepository())

        viewModel.loadArticle("")

        assertNull(viewModel.uiState.value.article)
        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals("기사를 찾을 수 없습니다.", viewModel.uiState.value.emptyMessage)
    }

    @Test
    fun loadArticleShowsErrorWhenRepositoryFails() = runTest {
        val viewModel = DetailViewModel(
            repository = FailingNewsRepository(),
            articleDetailRepository = NoOpArticleDetailRepository()
        )

        viewModel.loadArticle("any-id")

        assertNull(viewModel.uiState.value.article)
        assertEquals("repository failed", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun loadArticleFinishesWithoutLoadingState() = runTest {
        val article = MockNewsRepository.mockArticles.first()
        val viewModel = DetailViewModel(
            repository = SingleArticleNewsRepository(article),
            articleDetailRepository = NoOpArticleDetailRepository()
        )

        viewModel.loadArticle(article.id)

        assertTrue(!viewModel.uiState.value.isLoading)
    }

    @Test
    fun loadArticleDecodesRouteAndHtmlEntityBeforeLookup() = runTest {
        val article = MockNewsRepository.mockArticles.first().copy(id = "article-sbs-route&value")
        val viewModel = DetailViewModel(
            repository = SingleArticleNewsRepository(article),
            articleDetailRepository = NoOpArticleDetailRepository()
        )

        viewModel.loadArticle("article-sbs-route%26amp%3Bvalue")

        assertEquals(article.id, viewModel.uiState.value.article?.id)
    }

    @Test
    fun loadArticleEnrichesBodyParagraphsFromDetailRepository() = runTest {
        val base = MockNewsRepository.mockArticles.first()
        val enriched = base.copy(
            bodyParagraphs = listOf("full paragraph one", "full paragraph two"),
            content = "full paragraph one\n\nfull paragraph two"
        )
        val viewModel = DetailViewModel(
            repository = SingleArticleNewsRepository(base),
            articleDetailRepository = FakeArticleDetailRepository(enriched)
        )

        viewModel.loadArticle(base.id)

        assertEquals(enriched.bodyParagraphs, viewModel.uiState.value.article?.bodyParagraphs)
    }

    @Test
    fun loadArticleFallsBackToExistingArticleWhenDetailFetchFails() = runTest {
        val base = MockNewsRepository.mockArticles.first()
        val viewModel = DetailViewModel(
            repository = SingleArticleNewsRepository(base),
            articleDetailRepository = ThrowingArticleDetailRepository()
        )

        viewModel.loadArticle(base.id)

        assertEquals(base, viewModel.uiState.value.article)
    }

    @Test
    fun loadArticleDoesNotMixContentIntoTitle() = runTest {
        val base = MockNewsRepository.mockArticles.first().copy(
            title = "Clean title",
            content = "Full body should stay in body"
        )
        val enriched = base.copy(
            title = "Clean title",
            bodyParagraphs = listOf("Full body should stay in body")
        )
        val viewModel = DetailViewModel(
            repository = SingleArticleNewsRepository(base),
            articleDetailRepository = FakeArticleDetailRepository(enriched)
        )

        viewModel.loadArticle(base.id)

        assertEquals("Clean title", viewModel.uiState.value.article?.title)
        assertEquals(listOf("Full body should stay in body"), viewModel.uiState.value.article?.bodyParagraphs)
    }

    @Test
    fun loadYtnArticleKeepsOriginalUrlThroughDetailEnrichment() = runTest {
        val base = MockNewsRepository.mockArticles.first { article -> article.press == NewsPress.YTN }
        val enriched = base.copy(
            bodyParagraphs = listOf("YTN full body paragraph"),
            content = "YTN full body paragraph"
        )
        val viewModel = DetailViewModel(
            repository = SingleArticleNewsRepository(base),
            articleDetailRepository = FakeArticleDetailRepository(enriched)
        )

        viewModel.loadArticle(base.id)

        assertEquals(base.originalUrl, viewModel.uiState.value.article?.originalUrl)
        assertEquals(listOf("YTN full body paragraph"), viewModel.uiState.value.article?.bodyParagraphs)
    }

    private class FailingNewsRepository : NewsRepository {
        override suspend fun getLatestNews(): NewsFetchResult = NewsFetchResult(emptyList())

        override suspend fun searchNews(
            selectedPresses: Set<NewsPress>,
            keywords: List<String>
        ): NewsFetchResult = NewsFetchResult(emptyList())

        override suspend fun getNewsArticleById(articleId: String): NewsArticle? {
            throw IllegalStateException("repository failed")
        }
    }

    private class SingleArticleNewsRepository(
        private val article: NewsArticle
    ) : NewsRepository {
        override suspend fun getLatestNews(): NewsFetchResult = NewsFetchResult(listOf(article))

        override suspend fun searchNews(
            selectedPresses: Set<NewsPress>,
            keywords: List<String>
        ): NewsFetchResult = NewsFetchResult(listOf(article))

        override suspend fun getNewsArticleById(articleId: String): NewsArticle? =
            article.takeIf { it.id == articleId }
    }

    private class NoOpArticleDetailRepository : ArticleDetailRepository {
        override suspend fun enrichArticle(article: NewsArticle): NewsArticle = article
    }

    private class FakeArticleDetailRepository(
        private val enrichedArticle: NewsArticle
    ) : ArticleDetailRepository {
        override suspend fun enrichArticle(article: NewsArticle): NewsArticle = enrichedArticle
    }

    private class ThrowingArticleDetailRepository : ArticleDetailRepository {
        override suspend fun enrichArticle(article: NewsArticle): NewsArticle {
            throw IllegalStateException("detail failed")
        }
    }
}

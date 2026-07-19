package com.example.fakenews.data.repository

import com.example.fakenews.data.model.NewsArticle
import com.example.fakenews.data.model.NewsDataSourceResult
import com.example.fakenews.data.model.NewsPress
import com.example.fakenews.data.model.NewsSourceType
import com.example.fakenews.data.remote.NewsDataSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryFetchOrderTest {
    @Test
    fun rssSuccessSkipsHtmlDataSource() = runTest {
        val rss = RecordingSource(NewsSourceType.RSS, listOf(article(NewsPress.YONHAP, "rss")))
        val html = RecordingSource(NewsSourceType.HTML_CRAWLING, listOf(article(NewsPress.YONHAP, "html")))
        val repository = MultiSourceNewsRepository(listOf(rss, html))

        val result = repository.searchNews(setOf(NewsPress.YONHAP), emptyList())

        assertEquals(1, rss.callCount)
        assertEquals(0, html.callCount)
        assertEquals(NewsSourceType.RSS, result.articles.single().sourceType)
    }

    @Test
    fun rssFailureCallsHtmlDataSource() = runTest {
        val rss = RecordingSource(NewsSourceType.RSS, emptyList(), success = false)
        val html = RecordingSource(NewsSourceType.HTML_CRAWLING, listOf(article(NewsPress.KBS, "html")))
        val repository = MultiSourceNewsRepository(listOf(rss, html))

        val result = repository.searchNews(setOf(NewsPress.KBS), emptyList())

        assertEquals(1, rss.callCount)
        assertEquals(1, html.callCount)
        assertEquals(NewsSourceType.HTML_CRAWLING, result.articles.single().sourceType)
    }

    @Test
    fun rssAndHtmlFailureAddsFailedPressWithoutMockArticles() = runTest {
        val repository = MultiSourceNewsRepository(
            listOf(
                RecordingSource(NewsSourceType.RSS, emptyList(), success = false),
                RecordingSource(NewsSourceType.HTML_CRAWLING, emptyList(), success = false)
            )
        )

        val result = repository.searchNews(setOf(NewsPress.YTN), emptyList())

        assertTrue(result.articles.isEmpty())
        assertEquals(listOf(NewsPress.YTN), result.failedPresses)
        assertFalse(result.usedMockFallback)
        assertTrue(result.sourceStatuses.any { status -> status.sourceType == NewsSourceType.NOT_FOUND })
    }

    @Test
    fun onePressFailureDoesNotRemoveOtherPressSuccess() = runTest {
        val rss = RecordingSource(
            sourceType = NewsSourceType.RSS,
            articles = listOf(article(NewsPress.MBC, "rss")),
            successfulPresses = setOf(NewsPress.MBC)
        )
        val html = RecordingSource(NewsSourceType.HTML_CRAWLING, emptyList(), success = false)
        val repository = MultiSourceNewsRepository(listOf(rss, html))

        val result = repository.searchNews(setOf(NewsPress.MBC, NewsPress.SBS), emptyList())

        assertEquals(listOf(NewsPress.SBS), result.failedPresses)
        assertEquals(setOf(NewsPress.MBC), result.articles.map { article -> article.press }.toSet())
    }

    private fun article(
        press: NewsPress,
        suffix: String
    ): NewsArticle =
        MockNewsRepository.mockArticles
            .first { article -> article.press == press }
            .copy(
                id = "${press.name.lowercase()}-$suffix",
                sourceType = NewsSourceType.RSS,
                sourceLabel = NewsSourceType.RSS.displayName
            )

    private class RecordingSource(
        override val sourceType: NewsSourceType,
        private val articles: List<NewsArticle>,
        private val success: Boolean = articles.isNotEmpty(),
        private val successfulPresses: Set<NewsPress>? = null
    ) : NewsDataSource {
        override val sourceName: String = sourceType.displayName
        var callCount: Int = 0

        override suspend fun fetch(
            press: NewsPress,
            keywords: List<String>
        ): NewsDataSourceResult {
            callCount += 1
            val articlesForPress = if (successfulPresses == null || press in successfulPresses) {
                articles.filter { article -> article.press == press }
            } else {
                emptyList()
            }

            return NewsDataSourceResult(
                articles = articlesForPress.map { article ->
                    article.copy(
                        sourceType = sourceType,
                        sourceLabel = sourceType.displayName
                    )
                },
                success = success && articlesForPress.isNotEmpty(),
                message = sourceType.displayName
            )
        }
    }
}

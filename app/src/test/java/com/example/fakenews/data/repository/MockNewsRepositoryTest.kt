package com.example.fakenews.data.repository

import com.example.fakenews.data.model.NewsPress
import com.example.fakenews.util.NewsFilter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MockNewsRepositoryTest {
    private val repository = MockNewsRepository()

    @Test
    fun mockDataHasAtLeastSevenArticlesPerPressAndNoAllPressArticles() {
        val countsByPress = MockNewsRepository.mockArticles.groupingBy { article -> article.press }.eachCount()

        NewsPress.articlePresses().forEach { press ->
            assertTrue("${press.displayName} mock articles", (countsByPress[press] ?: 0) >= 7)
        }
        assertEquals(35, MockNewsRepository.mockArticles.size)
        assertFalse(MockNewsRepository.mockArticles.any { article -> article.press == NewsPress.ALL })
    }

    @Test
    fun mockDataUsesYtnAndRemovedPressesAreGone() {
        removedPressNames().forEach { removedName ->
            assertFalse(MockNewsRepository.mockArticles.any { article -> article.press.name == removedName })
        }
        assertTrue(MockNewsRepository.mockArticles.any { article -> article.press == NewsPress.YTN })
        assertTrue(
            MockNewsRepository.mockArticles
                .filter { article -> article.press == NewsPress.YTN }
                .all { article -> article.originalUrl.contains("ytn.co.kr") }
        )
    }

    @Test
    fun ytnMockDataContainsVideoArticle() {
        assertTrue(
            MockNewsRepository.mockArticles.any { article ->
                article.press == NewsPress.YTN && !article.videoUrl.isNullOrBlank()
            }
        )
    }

    @Test
    fun mockDataContainsArticlesWithoutImageUrlForPlaceholderState() {
        assertTrue(MockNewsRepository.mockArticles.any { article -> article.imageUrl == null })
    }

    @Test
    fun emptySelectedPressesReturnsNoArticles() = runTest {
        val result = repository.searchNews(
            selectedPresses = emptySet(),
            keywords = emptyList()
        )

        assertTrue(result.articles.isEmpty())
    }

    @Test
    fun allArticlePressesReturnAllPresses() = runTest {
        val result = repository.searchNews(
            selectedPresses = NewsPress.articlePresses().toSet(),
            keywords = emptyList()
        )

        assertEquals(NewsPress.articlePresses().toSet(), result.articles.map { article -> article.press }.toSet())
    }

    @Test
    fun selectedPressReturnsOnlyThatPress() = runTest {
        val result = repository.searchNews(
            selectedPresses = setOf(NewsPress.MBC),
            keywords = emptyList()
        )

        assertTrue(result.articles.isNotEmpty())
        assertTrue(result.articles.all { article -> article.press == NewsPress.MBC })
    }

    @Test
    fun resultLimitsArticlesToFivePerPress() = runTest {
        val result = repository.searchNews(
            selectedPresses = NewsPress.articlePresses().toSet(),
            keywords = listOf("목업")
        )

        result
            .articles
            .groupingBy { article -> article.press }
            .eachCount()
            .forEach { (_, count) ->
                assertTrue(count <= NewsFilter.MAX_ARTICLES_PER_PRESS)
            }
    }

    @Test
    fun resultIsSortedByLatestPublishedAt() = runTest {
        val result = repository.getLatestNews().articles

        assertEquals(
            result.mapNotNull { article -> article.publishedAt }.sortedDescending(),
            result.mapNotNull { article -> article.publishedAt }
        )
    }

    @Test
    fun keywordSearchMatchesTitleSummaryContentAndKeywords() = runTest {
        val allPresses = NewsPress.articlePresses().toSet()
        val titleResult = repository.searchNews(allPresses, listOf("제목검색토큰"))
        val summaryResult = repository.searchNews(allPresses, listOf("요약검색토큰"))
        val contentResult = repository.searchNews(allPresses, listOf("본문검색토큰"))
        val keywordResult = repository.searchNews(allPresses, listOf("키워드검색토큰"))

        assertTrue(titleResult.articles.any { article -> article.title.contains("제목검색토큰") })
        assertTrue(summaryResult.articles.any { article -> article.summary.contains("요약검색토큰") })
        assertTrue(contentResult.articles.any { article -> article.content.contains("본문검색토큰") })
        assertTrue(keywordResult.articles.any { article -> article.keywords.contains("키워드검색토큰") })
    }

    @Test
    fun emptyKeywordsReturnLatestNewsForMainDisplay() = runTest {
        val latestNews = repository.getLatestNews().articles
        val searchResult = repository.searchNews(
            selectedPresses = NewsPress.articlePresses().toSet(),
            keywords = emptyList()
        ).articles

        assertEquals(latestNews, searchResult)
        assertEquals(NewsPress.articlePresses().size * NewsFilter.MAX_ARTICLES_PER_PRESS, searchResult.size)
    }

    @Test
    fun getNewsArticleByIdReturnsMatchingArticle() = runTest {
        val expected = MockNewsRepository.mockArticles.first()

        val result = repository.getNewsArticleById(expected.id)

        assertEquals(expected, result)
    }

    @Test
    fun getNewsArticleByIdReturnsNullForMissingId() = runTest {
        val result = repository.getNewsArticleById("missing-id")

        assertEquals(null, result)
    }

    private fun removedPressNames(): List<String> =
        listOf("KBS" + "_WORLD", "J" + "TBC")
}

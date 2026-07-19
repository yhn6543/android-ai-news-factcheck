package com.example.fakenews.util

import com.example.fakenews.data.model.NewsArticle
import com.example.fakenews.data.model.NewsPress
import com.example.fakenews.data.repository.MockNewsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsFilterTest {
    @Test
    fun allSelectedTargetsEveryArticlePress() {
        val result = NewsFilter.filter(
            articles = MockNewsRepository.mockArticles,
            selectedPresses = NewsPress.articlePresses().toSet()
        )

        assertEquals(NewsPress.articlePresses().toSet(), result.map { article -> article.press }.toSet())
        assertEquals(NewsPress.articlePresses().size * NewsFilter.MAX_ARTICLES_PER_PRESS, result.size)
    }

    @Test
    fun emptySelectedPressesReturnsEmptyResult() {
        val result = NewsFilter.filter(
            articles = MockNewsRepository.mockArticles,
            selectedPresses = emptySet()
        )

        assertEquals(emptyList<NewsArticle>(), result)
    }

    @Test
    fun mbcSelectedReturnsOnlyMbcArticles() {
        val result = NewsFilter.filter(
            articles = MockNewsRepository.mockArticles,
            selectedPresses = setOf(NewsPress.MBC)
        )

        assertTrue(result.isNotEmpty())
        assertTrue(result.all { article -> article.press == NewsPress.MBC })
        assertEquals(NewsFilter.MAX_ARTICLES_PER_PRESS, result.size)
    }

    @Test
    fun mbcAndSbsSelectedReturnOnlyThosePresses() {
        val result = NewsFilter.filter(
            articles = MockNewsRepository.mockArticles,
            selectedPresses = setOf(NewsPress.MBC, NewsPress.SBS)
        )

        assertEquals(setOf(NewsPress.MBC, NewsPress.SBS), result.map { article -> article.press }.toSet())
        assertEquals(NewsFilter.MAX_ARTICLES_PER_PRESS * 2, result.size)
    }

    @Test
    fun allWithSpecificPressIgnoresAllAndUsesSpecificPress() {
        val result = NewsFilter.filter(
            articles = MockNewsRepository.mockArticles,
            selectedPresses = setOf(NewsPress.ALL, NewsPress.MBC)
        )

        assertTrue(result.isNotEmpty())
        assertTrue(result.all { article -> article.press == NewsPress.MBC })
    }

    @Test
    fun koreanKeywordMatchesTitleSummaryContentOrKeywords() {
        val articles = listOf(
            article(id = "title", press = NewsPress.YONHAP, publishedAt = 40L, title = "경제 전망 테스트"),
            article(id = "summary", press = NewsPress.MBC, publishedAt = 39L, summary = "생활 경제 요약"),
            article(id = "content", press = NewsPress.SBS, publishedAt = 38L, content = "본문에서 경제 정보를 확인"),
            article(id = "keyword", press = NewsPress.KBS, publishedAt = 37L, keywords = listOf("경제")),
            article(id = "miss", press = NewsPress.YTN, publishedAt = 36L, title = "문화 소식")
        )

        val result = NewsFilter.filter(
            articles = articles,
            selectedPresses = NewsPress.articlePresses().toSet(),
            keywords = listOf("경제")
        )

        assertEquals(setOf("title", "summary", "content", "keyword"), result.map { article -> article.id }.toSet())
    }

    @Test
    fun keywordSearchIgnoresCase() {
        val result = NewsFilter.filter(
            articles = listOf(
                article(id = "case", press = NewsPress.YTN, publishedAt = 1L, title = "Global ECONOMY Update")
            ),
            selectedPresses = setOf(NewsPress.YTN),
            keywords = listOf("economy")
        )

        assertEquals(listOf("case"), result.map { article -> article.id })
    }

    @Test
    fun keywordSearchTrimsWhitespace() {
        val result = NewsFilter.filter(
            articles = listOf(
                article(id = "trim", press = NewsPress.YTN, publishedAt = 1L, title = "경제 자료 점검")
            ),
            selectedPresses = setOf(NewsPress.YTN),
            keywords = listOf("  경제  ")
        )

        assertEquals(listOf("trim"), result.map { article -> article.id })
    }

    @Test
    fun resultLimitsArticlesToFivePerPressAfterFiltering() {
        val result = NewsFilter.filter(
            articles = MockNewsRepository.mockArticles,
            selectedPresses = NewsPress.articlePresses().toSet(),
            keywords = listOf("목업")
        )

        val countsByPress = result.groupingBy { article -> article.press }.eachCount()
        NewsPress.articlePresses().forEach { press ->
            assertEquals(NewsFilter.MAX_ARTICLES_PER_PRESS, countsByPress[press])
        }
    }

    @Test
    fun finalResultIsSortedByLatestPublishedAtAcrossPresses() {
        val result = NewsFilter.filter(
            articles = MockNewsRepository.mockArticles,
            selectedPresses = setOf(NewsPress.MBC, NewsPress.SBS, NewsPress.KBS)
        )

        assertEquals(
            result.mapNotNull { article -> article.publishedAt }.sortedDescending(),
            result.mapNotNull { article -> article.publishedAt }
        )
    }

    @Test
    fun duplicatedArticlesAreRemovedBeforeLimitAndSorting() {
        val articles = listOf(
            article(
                id = "old",
                press = NewsPress.MBC,
                publishedAt = 10L,
                title = "[속보] 경제 뉴스 점검",
                originalUrl = "https://example.com/news/1?utm_source=a"
            ),
            article(
                id = "new",
                press = NewsPress.MBC,
                publishedAt = 20L,
                title = "경제 뉴스 점검",
                originalUrl = "http://example.com/news/1"
            ),
            article(
                id = "other",
                press = NewsPress.MBC,
                publishedAt = 15L,
                title = "다른 경제 뉴스",
                originalUrl = "https://example.com/news/2"
            )
        )

        val result = NewsFilter.filter(
            articles = articles,
            selectedPresses = setOf(NewsPress.MBC),
            keywords = emptyList()
        )

        assertEquals(listOf("new", "other"), result.map { article -> article.id })
    }

    @Test
    fun emptyKeywordsReturnLatestArticlesWithoutKeywordFiltering() {
        val result = NewsFilter.filter(
            articles = MockNewsRepository.mockArticles,
            selectedPresses = NewsPress.articlePresses().toSet(),
            keywords = emptyList()
        )

        assertEquals(NewsPress.articlePresses().size * NewsFilter.MAX_ARTICLES_PER_PRESS, result.size)
        assertEquals(NewsPress.articlePresses().toSet(), result.map { article -> article.press }.toSet())
    }

    @Test
    fun normalizeKeywordsRemovesBlankDuplicatesAndLowercasesEnglish() {
        val result = NewsFilter.normalizeKeywords(listOf("  Economy  ", "", "economy", "경제"))

        assertEquals(listOf("economy", "경제"), result)
    }

    @Test
    fun matchedKeywordsReturnOnlyKeywordsFoundInArticleTextOrKeywords() {
        val article = article(
            id = "matched",
            press = NewsPress.KBS,
            publishedAt = 1L,
            title = "AI policy update",
            summary = "Semiconductor investment plan",
            content = "Body text without the other term",
            keywords = listOf("KBS")
        )

        val result = NewsFilter.matchedKeywords(
            article = article,
            keywords = listOf("ai", "economy", "Semiconductor", "kbs")
        )

        assertEquals(listOf("ai", "Semiconductor", "kbs"), result)
    }

    private fun article(
        id: String,
        press: NewsPress,
        publishedAt: Long,
        title: String = "테스트 제목",
        summary: String = "테스트 요약",
        content: String = "테스트 본문",
        originalUrl: String = "https://example.com/$id",
        keywords: List<String> = emptyList()
    ): NewsArticle =
        NewsArticle(
            id = id,
            title = title,
            press = press,
            publishedAt = publishedAt,
            summary = summary,
            content = content,
            imageUrl = null,
            originalUrl = originalUrl,
            keywords = keywords
        )
}

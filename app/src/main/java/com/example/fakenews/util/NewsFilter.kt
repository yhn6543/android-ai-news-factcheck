package com.example.fakenews.util

import com.example.fakenews.data.model.NewsArticle
import com.example.fakenews.data.model.NewsPress
import java.util.Locale

object NewsFilter {
    const val MAX_ARTICLES_PER_PRESS = 5

    data class DebugCounts(
        val afterPressFilterCount: Int,
        val afterDedupCount: Int,
        val afterKeywordCount: Int,
        val finalCount: Int
    )

    fun filter(
        articles: List<NewsArticle>,
        selectedPresses: Set<NewsPress> = emptySet(),
        keywords: List<String> = emptyList(),
        maxArticlesPerPress: Int = MAX_ARTICLES_PER_PRESS
    ): List<NewsArticle> =
        filterResult(
            articles = articles,
            selectedPresses = selectedPresses,
            keywords = keywords,
            maxArticlesPerPress = maxArticlesPerPress
        ).articles

    fun debugCounts(
        articles: List<NewsArticle>,
        selectedPresses: Set<NewsPress> = emptySet(),
        keywords: List<String> = emptyList(),
        maxArticlesPerPress: Int = MAX_ARTICLES_PER_PRESS
    ): DebugCounts =
        filterResult(
            articles = articles,
            selectedPresses = selectedPresses,
            keywords = keywords,
            maxArticlesPerPress = maxArticlesPerPress
        ).debugCounts

    private fun filterResult(
        articles: List<NewsArticle>,
        selectedPresses: Set<NewsPress>,
        keywords: List<String>,
        maxArticlesPerPress: Int
    ): FilterResult {
        val targetPresses = normalizeSelectedPresses(selectedPresses)
        val normalizedKeywords = normalizeKeywords(keywords)

        val pressFilteredArticles = articles.filter { article -> article.press in targetPresses }
        val deduplicatedArticles = NewsDeduplicator.deduplicate(pressFilteredArticles)
        val keywordFilteredArticles = deduplicatedArticles
            .filter { article -> normalizedKeywords.isEmpty() || article.matchesAny(normalizedKeywords) }
        val finalArticles = keywordFilteredArticles
            .groupBy { article -> article.press }
            .values
            .flatMap { pressArticles ->
                pressArticles
                    .sortedByDescending { article -> article.publishedAt ?: Long.MIN_VALUE }
                    .take(maxArticlesPerPress)
            }
            .sortedByDescending { article -> article.publishedAt ?: Long.MIN_VALUE }

        return FilterResult(
            articles = finalArticles,
            debugCounts = DebugCounts(
                afterPressFilterCount = pressFilteredArticles.size,
                afterDedupCount = deduplicatedArticles.size,
                afterKeywordCount = keywordFilteredArticles.size,
                finalCount = finalArticles.size
            )
        )
    }

    fun normalizeSelectedPresses(selectedPresses: Set<NewsPress>): Set<NewsPress> {
        return selectedPresses
            .filterNot { press -> press == NewsPress.ALL }
            .toSet()
    }

    fun normalizeKeywords(keywords: List<String>): List<String> =
        keywords
            .map { keyword -> keyword.trim().lowercase(Locale.ROOT) }
            .filter { keyword -> keyword.isNotEmpty() }
            .distinct()

    fun normalizeKeywordLabels(keywords: List<String>): List<String> =
        keywords
            .map { keyword -> keyword.trim() }
            .filter { keyword -> keyword.isNotEmpty() }
            .distinctBy { keyword -> keyword.lowercase(Locale.ROOT) }

    fun matchedKeywords(
        article: NewsArticle,
        keywords: List<String>
    ): List<String> =
        normalizeKeywordLabels(keywords).filter { keyword ->
            article.matchesKeyword(keyword.lowercase(Locale.ROOT))
        }

    private fun NewsArticle.matchesAny(normalizedKeywords: List<String>): Boolean =
        normalizedKeywords.any { keyword -> matchesKeyword(keyword) }

    private fun NewsArticle.matchesKeyword(keyword: String): Boolean =
        title.containsKeyword(keyword) ||
            summary.containsKeyword(keyword) ||
            content.containsKeyword(keyword) ||
            bodyParagraphs.any { paragraph -> paragraph.containsKeyword(keyword) } ||
            keywords.any { articleKeyword -> articleKeyword.containsKeyword(keyword) }

    private fun String.containsKeyword(keyword: String): Boolean =
        lowercase(Locale.ROOT).contains(keyword)

    private data class FilterResult(
        val articles: List<NewsArticle>,
        val debugCounts: DebugCounts
    )
}

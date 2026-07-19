package com.example.fakenews.util

import com.example.fakenews.data.model.NewsArticle

object NewsDeduplicator {
    fun deduplicate(articles: List<NewsArticle>): List<NewsArticle> {
        val selectedArticles = mutableListOf<NewsArticle>()
        val seenUrls = mutableSetOf<String>()

        articles
            .sortedWith(bestArticleComparator)
            .forEach { article ->
                val urlKey = UrlNormalizer.normalize(article.originalUrl)
                val duplicatedByUrl = urlKey.isNotBlank() && urlKey in seenUrls
                if (!duplicatedByUrl) {
                    selectedArticles += article
                    if (urlKey.isNotBlank()) seenUrls += urlKey
                }
            }

        return selectedArticles
    }

    private val bestArticleComparator: Comparator<NewsArticle> =
        compareByDescending<NewsArticle> { article -> article.publishedAt ?: Long.MIN_VALUE }
            .thenByDescending { article ->
                article.content.length + article.summary.length + article.bodyParagraphs.sumOf { it.length }
            }
            .thenByDescending { article -> if (article.imageUrl.isNullOrBlank()) 0 else 1 }
}

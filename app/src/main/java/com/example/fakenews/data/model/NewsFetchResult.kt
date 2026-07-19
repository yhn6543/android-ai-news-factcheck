package com.example.fakenews.data.model

data class NewsFetchResult(
    val articles: List<NewsArticle>,
    val sourceStatuses: List<NewsSourceStatus> = emptyList(),
    val failedPresses: List<NewsPress> = emptyList(),
    val usedMockFallback: Boolean = false,
    val fallbackPresses: List<NewsPress> = emptyList(),
    val message: String? = null
)

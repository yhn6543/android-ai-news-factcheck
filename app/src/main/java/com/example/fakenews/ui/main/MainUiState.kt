package com.example.fakenews.ui.main

import com.example.fakenews.data.model.NewsArticle
import com.example.fakenews.data.model.NewsPress
import com.example.fakenews.data.model.NewsSourceStatus

data class MainUiState(
    val selectedPresses: Set<NewsPress> = NewsPress.articlePresses().toSet(),
    val keywordInput: String = "",
    val registeredKeywords: List<String> = emptyList(),
    val articles: List<NewsArticle> = emptyList(),
    val mainArticles: List<NewsArticle> = emptyList(),
    val searchArticles: List<NewsArticle> = emptyList(),
    val activeKeywords: List<String> = emptyList(),
    val isSearchMode: Boolean = false,
    val sourceStatuses: List<NewsSourceStatus> = emptyList(),
    val failedPresses: List<NewsPress> = emptyList(),
    val fallbackPresses: List<NewsPress> = emptyList(),
    val usedMockFallback: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val emptyMessage: String? = null,
    val infoMessage: String? = null,
    val collectingPresses: Set<NewsPress> = emptySet(),
    val completedPresses: Set<NewsPress> = emptySet(),
    val failedPressReasons: Map<NewsPress, String> = emptyMap(),
    val isInitialLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val lastUpdatedAt: Long? = null
) {
    val isAllPressesSelected: Boolean
        get() = selectedPresses == NewsPress.articlePresses().toSet()

    val isCollectionInProgress: Boolean
        get() = collectingPresses.isNotEmpty()

}

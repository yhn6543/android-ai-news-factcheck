package com.example.fakenews.data.repository

import com.example.fakenews.data.model.NewsArticle
import com.example.fakenews.data.model.NewsFetchResult
import com.example.fakenews.data.model.NewsPress
import com.example.fakenews.data.model.NewsSourceStatus

sealed interface NewsCollectionEvent {
    data class PressCollectionStarted(
        val press: NewsPress
    ) : NewsCollectionEvent

    data class PressCollectionCompleted(
        val press: NewsPress,
        val articles: List<NewsArticle>,
        val sourceStatuses: List<NewsSourceStatus>
    ) : NewsCollectionEvent

    data class PressCollectionFailed(
        val press: NewsPress,
        val reason: String?,
        val partialArticles: List<NewsArticle> = emptyList(),
        val sourceStatuses: List<NewsSourceStatus> = emptyList()
    ) : NewsCollectionEvent

    data class PressCollectionTimeout(
        val press: NewsPress,
        val reason: String?,
        val partialArticles: List<NewsArticle> = emptyList(),
        val sourceStatuses: List<NewsSourceStatus> = emptyList()
    ) : NewsCollectionEvent

    data class AllPressCollectionFinished(
        val result: NewsFetchResult
    ) : NewsCollectionEvent
}

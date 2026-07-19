package com.example.fakenews.data.repository

import com.example.fakenews.data.model.NewsArticle
import com.example.fakenews.data.model.CollectionMode
import com.example.fakenews.data.model.NewsFetchResult
import com.example.fakenews.data.model.NewsPress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface NewsRepository {
    suspend fun getLatestNews(): NewsFetchResult

    suspend fun searchNews(
        selectedPresses: Set<NewsPress>,
        keywords: List<String>
    ): NewsFetchResult

    suspend fun searchNews(
        selectedPresses: Set<NewsPress>,
        keywords: List<String>,
        collectionMode: CollectionMode
    ): NewsFetchResult =
        searchNews(
            selectedPresses = selectedPresses,
            keywords = keywords
        )

    fun collectNewsByPressFlow(
        selectedPresses: Set<NewsPress>,
        keywords: List<String>,
        collectionMode: CollectionMode
    ): Flow<NewsCollectionEvent> =
        flow {
            emit(
                NewsCollectionEvent.AllPressCollectionFinished(
                    searchNews(
                        selectedPresses = selectedPresses,
                        keywords = keywords,
                        collectionMode = collectionMode
                    )
                )
            )
        }

    suspend fun getNewsArticleById(articleId: String): NewsArticle?
}

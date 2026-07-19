package com.example.fakenews.domain.usecase

import com.example.fakenews.data.model.CollectionMode
import com.example.fakenews.data.model.NewsFetchResult
import com.example.fakenews.data.model.NewsPress
import com.example.fakenews.data.repository.NewsCollectionEvent
import com.example.fakenews.data.repository.NewsRepository
import kotlinx.coroutines.flow.Flow

class GetFilteredNewsUseCase(
    private val repository: NewsRepository
) {
    suspend operator fun invoke(
        selectedPresses: Set<NewsPress> = emptySet(),
        keywords: List<String> = emptyList(),
        collectionMode: CollectionMode = CollectionMode.SEARCH
    ): NewsFetchResult =
        repository.searchNews(
            selectedPresses = selectedPresses,
            keywords = keywords,
            collectionMode = collectionMode
        )

    fun collectByPress(
        selectedPresses: Set<NewsPress> = emptySet(),
        keywords: List<String> = emptyList(),
        collectionMode: CollectionMode = CollectionMode.SEARCH
    ): Flow<NewsCollectionEvent> =
        repository.collectNewsByPressFlow(
            selectedPresses = selectedPresses,
            keywords = keywords,
            collectionMode = collectionMode
        )
}

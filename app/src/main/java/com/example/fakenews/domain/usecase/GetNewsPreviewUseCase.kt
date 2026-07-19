package com.example.fakenews.domain.usecase

import com.example.fakenews.data.model.NewsArticle
import com.example.fakenews.data.model.CollectionMode
import com.example.fakenews.data.repository.NewsRepository

class GetNewsPreviewUseCase(
    private val repository: NewsRepository
) {
    suspend operator fun invoke(): List<NewsArticle> =
        repository.searchNews(
            selectedPresses = com.example.fakenews.data.model.NewsPress.articlePresses().toSet(),
            keywords = emptyList(),
            collectionMode = CollectionMode.MAIN_DEFAULT
        ).articles
}

package com.example.fakenews

import com.example.fakenews.data.repository.DefaultNewsRepository
import com.example.fakenews.data.repository.ArticleDetailRepository
import com.example.fakenews.data.repository.DefaultArticleDetailRepository
import com.example.fakenews.data.repository.FactCheckRepository
import com.example.fakenews.data.repository.GeminiFactCheckRepository
import com.example.fakenews.data.repository.MockFactCheckRepository
import com.example.fakenews.data.repository.NewsRepository

object AppContainer {
    const val USE_MOCK_FACTCHECK = false

    val newsRepository: NewsRepository by lazy {
        DefaultNewsRepository()
    }

    val articleDetailRepository: ArticleDetailRepository by lazy {
        DefaultArticleDetailRepository()
    }

    val factCheckRepository: FactCheckRepository by lazy {
        if (USE_MOCK_FACTCHECK) {
            MockFactCheckRepository()
        } else {
            GeminiFactCheckRepository()
        }
    }
}

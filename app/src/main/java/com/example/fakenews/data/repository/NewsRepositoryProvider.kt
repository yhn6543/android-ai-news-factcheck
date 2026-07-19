package com.example.fakenews.data.repository

import com.example.fakenews.AppContainer

object NewsRepositoryProvider {
    val repository: NewsRepository
        get() = AppContainer.newsRepository
}

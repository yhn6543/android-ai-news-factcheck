package com.example.fakenews.data.repository

import com.example.fakenews.AppContainer

object ArticleDetailRepositoryProvider {
    val repository: ArticleDetailRepository
        get() = AppContainer.articleDetailRepository
}

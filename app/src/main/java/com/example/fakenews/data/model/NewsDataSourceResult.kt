package com.example.fakenews.data.model

data class NewsDataSourceResult(
    val articles: List<NewsArticle>,
    val success: Boolean,
    val message: String? = null
)

package com.example.fakenews.data.repository

import com.example.fakenews.data.model.NewsArticle

interface ArticleDetailRepository {
    suspend fun enrichArticle(article: NewsArticle): NewsArticle
}

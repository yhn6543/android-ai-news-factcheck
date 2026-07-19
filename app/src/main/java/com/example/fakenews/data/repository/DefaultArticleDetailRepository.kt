package com.example.fakenews.data.repository

import com.example.fakenews.data.model.NewsArticle
import com.example.fakenews.data.remote.ArticleDetailRemoteDataSource

class DefaultArticleDetailRepository(
    private val remoteDataSource: ArticleDetailRemoteDataSource = ArticleDetailRemoteDataSource()
) : ArticleDetailRepository {
    override suspend fun enrichArticle(article: NewsArticle): NewsArticle =
        runCatching {
            remoteDataSource.fetchDetail(article)
        }.getOrDefault(article)
}

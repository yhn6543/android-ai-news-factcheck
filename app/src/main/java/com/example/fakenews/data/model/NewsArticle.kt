package com.example.fakenews.data.model

import kotlinx.serialization.Serializable

@Serializable
data class NewsArticle(
    val id: String,
    val title: String,
    val press: NewsPress,
    val publishedAt: Long?,
    val publishedAtSource: String? = null,
    val summary: String,
    val content: String,
    val bodyParagraphs: List<String> = emptyList(),
    val imageUrl: String? = null,
    val videoUrl: String? = null,
    val originalUrl: String,
    val keywords: List<String> = emptyList(),
    val matchedKeywords: List<String> = emptyList(),
    val sourceType: NewsSourceType = NewsSourceType.MOCK,
    val sourceLabel: String = NewsSourceType.MOCK.displayName,
    val articleType: ArticleType = ArticleType.NEWS_ARTICLE,
    val isVideoNews: Boolean = false
)

package com.example.fakenews.data.model

data class NewsSourceStatus(
    val press: NewsPress,
    val sourceType: NewsSourceType,
    val sourceName: String,
    val success: Boolean,
    val articleCount: Int,
    val message: String? = null
)

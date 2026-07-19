package com.example.fakenews.data.remote

import com.example.fakenews.data.model.NewsDataSourceResult
import com.example.fakenews.data.model.NewsPress
import com.example.fakenews.data.model.NewsSourceType

interface NewsDataSource {
    val sourceType: NewsSourceType
    val sourceName: String

    suspend fun fetch(
        press: NewsPress,
        keywords: List<String>
    ): NewsDataSourceResult
}

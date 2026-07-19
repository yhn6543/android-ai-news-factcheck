package com.example.fakenews.data.remote

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val PLACEHOLDER_BASE_URL = "https://example.com/"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(NewsFetchConfig.HTML_CRAWLING_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(NewsFetchConfig.HTML_CRAWLING_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(NewsFetchConfig.HTML_CRAWLING_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(PLACEHOLDER_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
}

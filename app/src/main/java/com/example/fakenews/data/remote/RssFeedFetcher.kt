package com.example.fakenews.data.remote

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

interface RssFeedFetcher {
    suspend fun fetch(url: String): String
}

class OkHttpRssFeedFetcher(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(NewsFetchConfig.RSS_FETCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(NewsFetchConfig.RSS_FETCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(NewsFetchConfig.RSS_FETCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
) : RssFeedFetcher {
    override suspend fun fetch(url: String): String =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "FakeNewsApp/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("RSS HTTP ${response.code}: $url")
                }

                response.body?.string()
                    ?: throw IOException("RSS empty body: $url")
            }
        }
}

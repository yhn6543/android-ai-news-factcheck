package com.example.fakenews.data.remote

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

interface HtmlPageFetcher {
    suspend fun fetch(url: String): String
}

class OkHttpHtmlPageFetcher(
    private val client: OkHttpClient
) : HtmlPageFetcher {
    override suspend fun fetch(url: String): String =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", NewsFetchConfig.USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTML HTTP ${response.code}: $url")
                }

                response.body?.string()
                    ?: throw IOException("HTML empty body: $url")
            }
        }
}

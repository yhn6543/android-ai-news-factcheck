package com.example.fakenews.data.remote

import android.util.Log
import com.example.fakenews.data.model.NewsArticle
import com.example.fakenews.data.model.NewsPress
import com.example.fakenews.util.ArticleQualityValidator

class RssNewsRemoteDataSource(
    private val feedConfigs: List<RssFeedConfig> = RssFeedConfig.defaultFeeds,
    private val fetcher: RssFeedFetcher = OkHttpRssFeedFetcher(),
    private val originalUrlResolver: OriginalUrlResolver = HttpRedirectOriginalUrlResolver()
) {
    suspend fun fetchLatestNews(targetPresses: Set<NewsPress>): List<NewsArticle> =
        feedConfigs
            .filter { config -> config.press in targetPresses }
            .flatMap { config ->
                runCatching {
                    val xml = fetcher.fetch(config.url)
                    RssParser.parse(
                        xml = xml,
                        press = config.press,
                        sourceLabel = config.label,
                        originalUrlMode = config.originalUrlMode,
                        originalUrlResolver = originalUrlResolver
                    )
                        .map(ArticleQualityValidator::cleanAndValidate)
                        .mapNotNull { check -> check.article.takeIf { check.result.isValid } }
                }.onFailure { error ->
                    logFeedFailure(config, error)
                }.getOrDefault(emptyList())
            }

    private fun logFeedFailure(
        config: RssFeedConfig,
        error: Throwable
    ) {
        runCatching {
            Log.d(TAG, "RSS feed failed: ${config.press.displayName} ${config.url}", error)
        }
    }

    private companion object {
        const val TAG = "RssNewsRemote"
    }
}

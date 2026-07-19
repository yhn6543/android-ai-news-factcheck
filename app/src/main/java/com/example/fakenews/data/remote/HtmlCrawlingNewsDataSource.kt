package com.example.fakenews.data.remote

import com.example.fakenews.data.model.NewsArticle
import com.example.fakenews.data.model.NewsDataSourceResult
import com.example.fakenews.data.model.NewsPress
import com.example.fakenews.data.model.NewsSourceType
import com.example.fakenews.util.NewsFilter
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient

class HtmlCrawlingNewsDataSource(
    private val targets: List<HtmlCrawlingTarget> = HtmlCrawlingTarget.defaultTargets,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(NewsFetchConfig.HTML_CRAWLING_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(NewsFetchConfig.HTML_CRAWLING_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(NewsFetchConfig.HTML_CRAWLING_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build(),
    private val enabled: Boolean = NewsFetchConfig.ENABLE_HTML_CRAWLING,
    private val fetcher: HtmlPageFetcher = OkHttpHtmlPageFetcher(client),
    private val detailDataSource: ArticleDetailRemoteDataSource = ArticleDetailRemoteDataSource(fetcher = fetcher)
) : NewsDataSource {
    override val sourceType: NewsSourceType = NewsSourceType.HTML_CRAWLING
    override val sourceName: String = "HTML Crawling"

    override suspend fun fetch(
        press: NewsPress,
        keywords: List<String>
    ): NewsDataSourceResult {
        if (!enabled) {
            return NewsDataSourceResult(
                articles = emptyList(),
                success = false,
                message = "HTML crawling disabled"
            )
        }

        // HTML crawling is only a last-resort demo fallback. Before production use,
        // check each site's robots.txt, terms of service, and copyright policy.
        val pressTargets = targets.filter { target -> target.press == press }
        if (pressTargets.isEmpty()) {
            return NewsDataSourceResult(
                articles = emptyList(),
                success = false,
                message = "HTML crawling target not found"
            )
        }

        val failureMessages = mutableListOf<String>()
        pressTargets.forEach { target ->
            val result = runCatching {
                val html = fetcher.fetch(target.url)
                if (html.isBlank()) {
                    error("HTML body empty")
                }

                HtmlCrawlerParser.parse(
                    html = html,
                    baseUrl = target.url,
                    press = press,
                    enforceAllowedDomain = true,
                    maxArticles = candidateLimitFor(
                        press = press,
                        keywords = keywords
                    )
                )
                    .enrichFromOriginalUrls(keywords = keywords)
            }

            val articles = result.getOrDefault(emptyList())
            val success = articles.isNotEmpty()
            NewsFetchLogger.logAttempt(
                tag = NewsFetchLogger.TAG_CRAWLING,
                press = press,
                sourceType = sourceType,
                sourceName = sourceName,
                url = target.url,
                success = success,
                articleCount = articles.size,
                errorMessage = result.exceptionOrNull()?.message
            )
            NewsFetchLogger.logAttempt(
                tag = NewsFetchLogger.TAG_FETCH,
                press = press,
                sourceType = sourceType,
                sourceName = sourceName,
                url = target.url,
                success = success,
                articleCount = articles.size,
                errorMessage = result.exceptionOrNull()?.message
            )

            if (success) {
                return NewsDataSourceResult(
                    articles = articles,
                    success = true,
                    message = "${articles.size} HTML crawling articles collected"
                )
            }

            failureMessages += result.exceptionOrNull()?.message ?: "HTML crawling articles not found"
        }

        return NewsDataSourceResult(
            articles = emptyList(),
            success = false,
            message = failureMessages.joinToString("; ").ifBlank { "HTML crawling failed" }
        )
    }

    private suspend fun List<NewsArticle>.enrichFromOriginalUrls(keywords: List<String>): List<NewsArticle> {
        val isSearchFallback = keywords.any { keyword -> keyword.isNotBlank() }
        val detailTimeoutMs = if (isSearchFallback) {
            NewsFetchConfig.SEARCH_HTML_DETAIL_FETCH_TIMEOUT_MS
        } else {
            NewsFetchConfig.DETAIL_FETCH_TIMEOUT_MS
        }
        val enrichedArticles = mutableListOf<NewsArticle>()
        var keywordMatchedCount = 0
        for (article in this) {
            val enrichedArticle = runCatching {
                withTimeoutOrNull(detailTimeoutMs) {
                    detailDataSource.fetchDetail(article)
                } ?: error("DETAIL_FETCH_TIMEOUT")
            }.getOrElse { error ->
                NewsFetchLogger.logDetail(
                    "html_detail_enrichment_failed press=${article.press.displayName} " +
                        "originalUrl=${NewsFetchLogger.safeUrlForLog(article.originalUrl)} error=${error.message.orEmpty()}"
                )
                article
            }
            enrichedArticles += enrichedArticle
            if (isSearchFallback && NewsFilter.matchedKeywords(enrichedArticle, keywords).isNotEmpty()) {
                keywordMatchedCount += 1
                if (keywordMatchedCount >= NewsFetchConfig.MAX_ARTICLES_PER_PRESS) break
            }
        }
        return enrichedArticles
    }

    private fun candidateLimitFor(
        press: NewsPress,
        keywords: List<String>
    ): Int {
        val isSearchFallback = keywords.any { keyword -> keyword.isNotBlank() }
        return when {
            isSearchFallback && press == NewsPress.YTN ->
                NewsFetchConfig.SEARCH_HTML_FALLBACK_YTN_RAW_CANDIDATE_LIMIT
            isSearchFallback ->
                NewsFetchConfig.SEARCH_HTML_FALLBACK_RAW_CANDIDATE_LIMIT_PER_PRESS
            press == NewsPress.YTN ->
                NewsFetchConfig.MAX_ARTICLES_PER_PRESS * 6
            else ->
                NewsFetchConfig.MAX_ARTICLES_PER_PRESS
        }
    }
}

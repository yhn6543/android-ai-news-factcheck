package com.example.fakenews.data.remote

import com.example.fakenews.data.model.NewsArticle
import com.example.fakenews.data.model.NewsDataSourceResult
import com.example.fakenews.data.model.NewsPress
import com.example.fakenews.data.model.NewsSourceType
import com.example.fakenews.util.ArticleUrlPolicy
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

// Kept for isolated experiments/tests only. The app display flow uses
// MultiSourceNewsRepository with official RSS -> HTML crawling -> NOT_FOUND.
class SearchRssNewsDataSource(
    private val fetcher: RssFeedFetcher = OkHttpRssFeedFetcher()
) : NewsDataSource {
    override val sourceType: NewsSourceType = NewsSourceType.SEARCH_RSS
    override val sourceName: String = "Google News RSS 검색"

    override suspend fun fetch(
        press: NewsPress,
        keywords: List<String>
    ): NewsDataSourceResult {
        val query = buildQuery(press, keywords)
        val url = "https://news.google.com/rss/search?q=${query.encode()}&hl=ko&gl=KR&ceid=KR:ko"

        val result = runCatching {
            val xml = fetcher.fetch(url)
            RssParser.parse(
                xml = xml,
                press = press,
                sourceType = sourceType,
                sourceLabel = sourceType.displayName
            )
        }
        val parsedArticles = result.getOrDefault(emptyList())
        val trustedArticles = parsedArticles
            .filter { article -> ArticleUrlPolicy.isValidArticleUrl(press, article.originalUrl) }
        val filteredArticles = trustedArticles
            .take(NewsFetchConfig.MAX_ARTICLES_PER_PRESS)
        val discardedCount = parsedArticles.size - trustedArticles.size
        val success = filteredArticles.isNotEmpty()

        if (discardedCount > 0) {
            NewsFetchLogger.logAttempt(
                tag = NewsFetchLogger.TAG_SEARCH_RSS,
                press = press,
                sourceType = sourceType,
                sourceName = sourceName,
                url = url,
                success = false,
                articleCount = discardedCount,
                errorMessage = "discarded search RSS articles with untrusted domains"
            )
        }

        NewsFetchLogger.logAttempt(
            tag = NewsFetchLogger.TAG_SEARCH_RSS,
            press = press,
            sourceType = sourceType,
            sourceName = sourceName,
            url = url,
            success = success,
            articleCount = filteredArticles.size,
            errorMessage = result.exceptionOrNull()?.message
        )
        NewsFetchLogger.logAttempt(
            tag = NewsFetchLogger.TAG_FETCH,
            press = press,
            sourceType = sourceType,
            sourceName = sourceName,
            url = url,
            success = success,
            articleCount = filteredArticles.size,
            errorMessage = result.exceptionOrNull()?.message
        )

        return NewsDataSourceResult(
            articles = filteredArticles.map { article ->
                article.copy(
                    sourceType = sourceType,
                    sourceLabel = sourceType.displayName
                )
            },
            success = success,
            message = result.exceptionOrNull()?.message ?: if (success) {
                "${filteredArticles.size}개 검색 RSS 기사 수집"
            } else {
                "검색 RSS 기사 없음"
            }
        )
    }

    private fun buildQuery(
        press: NewsPress,
        keywords: List<String>
    ): String {
        val normalizedKeywords = keywords
            .map { keyword -> keyword.trim() }
            .filter { keyword -> keyword.isNotBlank() }
        return if (normalizedKeywords.isEmpty()) {
            "${press.displayName} 최신뉴스"
        } else {
            "${press.displayName} ${normalizedKeywords.joinToString(" ")}"
        }
    }

    private fun String.encode(): String =
        URLEncoder.encode(this, StandardCharsets.UTF_8.name())

}

package com.example.fakenews.data.remote

import com.example.fakenews.data.model.NewsArticle
import com.example.fakenews.util.ArticleContentExtractor
import com.example.fakenews.util.ArticleQualityValidator
import com.example.fakenews.util.ArticleTitleCleaner
import com.example.fakenews.util.ArticleUrlPolicy
import com.example.fakenews.util.UrlNormalizer
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import org.jsoup.Jsoup

class ArticleDetailRemoteDataSource(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(NewsFetchConfig.HTML_CRAWLING_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(NewsFetchConfig.HTML_CRAWLING_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(NewsFetchConfig.HTML_CRAWLING_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build(),
    private val fetcher: HtmlPageFetcher = OkHttpHtmlPageFetcher(client)
) {
    suspend fun fetchDetail(article: NewsArticle): NewsArticle {
        val originalUrl = UrlNormalizer.decodeHtmlEntities(article.originalUrl).trim()
        NewsFetchLogger.logDetailLinkCheck(
            "fetchDetail id=${article.id} press=${article.press.displayName} " +
                "originalUrl=$originalUrl normalizedOriginalUrl=${UrlNormalizer.normalize(originalUrl)}"
        )
        if (!originalUrl.startsWith("http://") && !originalUrl.startsWith("https://")) {
            NewsFetchLogger.logDetail(
                "press=${article.press.displayName} originalUrl=$originalUrl validUrl=false"
            )
            return article
        }
        if (!ArticleUrlPolicy.isValidArticleUrl(article.press, originalUrl)) {
            NewsFetchLogger.logDetail(
                "press=${article.press.displayName} originalUrl=$originalUrl validArticleUrl=false"
            )
            return article
        }

        val html = fetcher.fetch(originalUrl)
        val document = Jsoup.parse(html, originalUrl)
        val extracted = ArticleContentExtractor.extract(document, article.press)
        val bodyParagraphs = extracted.paragraphs.ifEmpty { article.bodyParagraphs }
        val content = bodyParagraphs.joinToString("\n\n").ifBlank { article.content }
        val cleanedTitle = extracted.title
            ?.let(ArticleTitleCleaner::cleanTitle)
            ?.takeIf { it.isNotBlank() }
            ?: ArticleTitleCleaner.cleanTitle(article.title)
        val chosenPublishedAt = extracted.publishedTime?.epochMillis ?: article.publishedAt
        val chosenPublishedAtSource = extracted.publishedTime?.source ?: article.publishedAtSource

        val enriched = article.copy(
            title = cleanedTitle,
            publishedAt = chosenPublishedAt,
            publishedAtSource = chosenPublishedAtSource,
            summary = extracted.summary?.takeIf { it.isNotBlank() } ?: article.summary,
            content = content,
            bodyParagraphs = bodyParagraphs,
            imageUrl = article.imageUrl ?: extracted.imageUrl,
            videoUrl = article.videoUrl ?: extracted.videoUrl,
            originalUrl = originalUrl
        )
        if (article.press == com.example.fakenews.data.model.NewsPress.YTN) {
            YtnPipelineDebugStore.current()?.recordDetail(
                originalUrl = article.originalUrl,
                success = true,
                extractedTitle = extracted.title,
                paragraphCount = bodyParagraphs.size,
                contentLength = content.length,
                boilerplateRemovedCount = extracted.boilerplateRemovedCount
            )
        }
        return ArticleQualityValidator.cleanArticle(enriched).also { enrichedArticle ->
            NewsFetchLogger.logDetailLinkCheck(
                "enriched id=${enrichedArticle.id} press=${enrichedArticle.press.displayName} " +
                    "originalUrl=${enrichedArticle.originalUrl} normalizedOriginalUrl=${UrlNormalizer.normalize(enrichedArticle.originalUrl)}"
            )
            NewsFetchLogger.logDetail(
                "publishedAtChoice id=${enrichedArticle.id} source=${enrichedArticle.publishedAtSource.orEmpty()} " +
                    "publishedAt=${enrichedArticle.publishedAt?.toString().orEmpty()} " +
                    "detailSource=${extracted.publishedTime?.source.orEmpty()} policy=prefer-detail-when-present"
            )
        }
    }
}

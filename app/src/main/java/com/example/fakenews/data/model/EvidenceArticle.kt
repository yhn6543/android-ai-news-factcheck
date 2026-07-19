package com.example.fakenews.data.model

data class EvidenceArticle(
    val title: String,
    val summary: String,
    val press: String,
    val publishedAt: Long?,
    val originalUrl: String
) {
    companion object {
        fun from(article: NewsArticle): EvidenceArticle =
            EvidenceArticle(
                title = article.title,
                summary = article.bodyParagraphs.firstOrNull()
                    ?: article.summary.ifBlank { article.content },
                press = article.press.displayName,
                publishedAt = article.publishedAt,
                originalUrl = article.originalUrl
            )
    }
}

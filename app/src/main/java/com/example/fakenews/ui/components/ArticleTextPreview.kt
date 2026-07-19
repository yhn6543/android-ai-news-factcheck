package com.example.fakenews.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.fakenews.data.model.NewsArticle
import com.example.fakenews.util.ArticleTextExtractor
import com.example.fakenews.util.ArticleTitleCleaner

object ArticleTextFormatter {
    fun paragraphs(article: NewsArticle): List<String> =
        ArticleTitleCleaner.removeDuplicatedTitleFromParagraphs(
            title = article.title,
            paragraphs = ArticleTextExtractor.displayParagraphs(
                bodyParagraphs = article.bodyParagraphs,
                content = article.content,
                summary = article.summary
            )
        )

    fun previewParagraphs(
        article: NewsArticle,
        maxParagraphs: Int
    ): List<String> =
        paragraphs(article).take(maxParagraphs)
}

@Composable
fun ArticleTextPreview(
    article: NewsArticle,
    modifier: Modifier = Modifier,
    maxParagraphs: Int? = 3,
    maxLinesPerParagraph: Int? = 2,
    paragraphSpacing: Dp = 6.dp,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    val paragraphs = ArticleTextFormatter.paragraphs(article)
        .let { values -> maxParagraphs?.let(values::take) ?: values }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(paragraphSpacing)
    ) {
        paragraphs.forEach { paragraph ->
            if (maxLinesPerParagraph == null) {
                Text(
                    text = paragraph,
                    style = style,
                    color = color
                )
            } else {
                Text(
                    text = paragraph,
                    style = style,
                    color = color,
                    maxLines = maxLinesPerParagraph,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

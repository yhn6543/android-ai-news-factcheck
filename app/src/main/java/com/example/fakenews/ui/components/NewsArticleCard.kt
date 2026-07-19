package com.example.fakenews.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.fakenews.data.model.NewsArticle
import com.example.fakenews.data.remote.NewsFetchLogger
import com.example.fakenews.ui.theme.toComposeColor
import com.example.fakenews.util.TimeFormatter

@Composable
fun NewsArticleCard(
    article: NewsArticle,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    showMatchedKeywords: Boolean = false
) {
    val hasImage = !article.imageUrl.isNullOrBlank()
    val hasPlayableVideo = !article.videoUrl.isNullOrBlank()
    val isVideoNews = hasPlayableVideo || article.isVideoNews
    val matchedKeywords = if (showMatchedKeywords) {
        article.matchedKeywords
            .map { keyword -> keyword.trim() }
            .filter { keyword -> keyword.isNotBlank() }
            .distinct()
    } else {
        emptyList()
    }

    LaunchedEffect(article.id, article.originalUrl) {
        NewsFetchLogger.logArticleLinkCheck(
            "card press=${article.press.displayName} id=${article.id} title=${article.title} " +
                "originalUrl=${article.originalUrl}"
        )
    }

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = if (hasImage || hasPlayableVideo) 340.dp else 260.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = article.press.buttonColor.toComposeColor(),
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = article.press.displayName,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (isVideoNews) {
                        VideoBadge()
                    }
                }
                Text(
                    text = TimeFormatter.formatPublishedTime(article.publishedAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = article.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (matchedKeywords.isNotEmpty()) {
                MatchedKeywordRow(keywords = matchedKeywords)
            }

            if (hasImage) {
                NewsImageBox(
                    imageUrl = article.imageUrl,
                    contentDescription = if (isVideoNews) {
                        "${article.press.displayName} 영상 기사 썸네일"
                    } else {
                        "${article.press.displayName} 기사 이미지"
                    }
                )
            }

            ArticleTextPreview(
                article = article,
                modifier = Modifier.fillMaxWidth(),
                maxParagraphs = if (hasImage || hasPlayableVideo) 3 else 4,
                maxLinesPerParagraph = if (hasImage || hasPlayableVideo) 2 else 3,
                paragraphSpacing = 6.dp,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = article.originalUrl.shortUrlLabel(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MatchedKeywordRow(keywords: List<String>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        keywords.forEach { keyword ->
            MatchedKeywordChip(keyword = keyword)
        }
    }
}

@Composable
private fun MatchedKeywordChip(keyword: String) {
    Surface(
        modifier = Modifier.heightIn(min = 26.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Text(
            text = keyword,
            modifier = Modifier
                .widthIn(max = 132.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun String.shortUrlLabel(): String =
    removePrefix("https://")
        .removePrefix("http://")
        .take(42)

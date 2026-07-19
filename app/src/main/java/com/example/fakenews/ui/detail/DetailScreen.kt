package com.example.fakenews.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fakenews.data.model.NewsArticle
import com.example.fakenews.data.remote.NewsFetchLogger
import com.example.fakenews.data.repository.MockNewsRepository
import com.example.fakenews.ui.components.ArticleTextPreview
import com.example.fakenews.ui.components.EmptyStateView
import com.example.fakenews.ui.components.ErrorMessage
import com.example.fakenews.ui.components.LoadingView
import com.example.fakenews.ui.components.NewsImageBox
import com.example.fakenews.ui.components.VideoBadge
import com.example.fakenews.ui.theme.NewsAppTheme
import com.example.fakenews.util.OpenUrl
import com.example.fakenews.util.OpenUrlResult
import com.example.fakenews.util.TimeFormatter
import kotlinx.coroutines.launch

@Composable
fun DetailScreen(
    articleId: String?,
    onBackClick: () -> Unit,
    viewModel: DetailViewModel = viewModel()
) {
    LaunchedEffect(articleId) {
        viewModel.loadArticle(articleId)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DetailContent(
        uiState = uiState,
        onBackClick = onBackClick
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailContent(
    uiState: DetailUiState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = "기사 상세") },
                actions = {
                    OutlinedButton(
                        onClick = onBackClick,
                        modifier = Modifier.padding(end = 12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(text = "뒤로")
                    }
                }
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when {
                uiState.isLoading -> {
                    item {
                        LoadingView(message = "기사를 불러오는 중입니다.")
                    }
                }

                uiState.article != null -> {
                    item {
                        ArticleDetail(
                            article = uiState.article,
                            onOpenOriginalClick = {
                                NewsFetchLogger.logDetailLinkCheck(
                                    "openOriginal id=${uiState.article.id} press=${uiState.article.press.displayName} " +
                                        "originalUrl=${uiState.article.originalUrl}"
                                )
                                val result = OpenUrl.openExternalBrowser(context, uiState.article.originalUrl)
                                if (result != OpenUrlResult.Opened) {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(result.toMessage())
                                    }
                                }
                            }
                        )
                    }
                }

                else -> {
                    item {
                        if (uiState.errorMessage != null) {
                            ErrorMessage(message = uiState.errorMessage)
                        } else {
                            EmptyStateView(
                                message = uiState.emptyMessage ?: "기사를 찾을 수 없습니다."
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArticleDetail(
    article: NewsArticle,
    onOpenOriginalClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        FilledTonalButton(
            onClick = onOpenOriginalClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = "원본 보러가기")
        }

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = article.title,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontSize = 24.sp,
                        lineHeight = 31.sp
                    ),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = article.press.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = TimeFormatter.formatPublishedTime(article.publishedAt),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        val isVideoNews = article.isVideoNews || !article.videoUrl.isNullOrBlank()
        when {
            !article.imageUrl.isNullOrBlank() -> {
                if (isVideoNews) {
                    VideoBadge()
                }
                NewsImageBox(
                    imageUrl = article.imageUrl,
                    contentDescription = if (isVideoNews) {
                        "${article.press.displayName} 영상 기사 썸네일"
                    } else {
                        "${article.press.displayName} 기사 이미지"
                    },
                    height = 240.dp
                )
            }
            isVideoNews -> {
                VideoBadge()
            }
        }

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            ArticleTextPreview(
                article = article,
                modifier = Modifier.padding(18.dp),
                maxParagraphs = null,
                maxLinesPerParagraph = null,
                paragraphSpacing = 12.dp,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    lineHeight = 26.sp
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Text(
            text = if (article.originalUrl.isBlank()) {
                "원본 URL이 없습니다."
            } else {
                article.originalUrl
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun OpenUrlResult.toMessage(): String =
    when (this) {
        OpenUrlResult.Opened -> ""
        OpenUrlResult.InvalidUrl -> "원본 URL이 올바르지 않습니다."
        OpenUrlResult.NoHandler -> "원본 URL을 열 수 있는 앱을 찾지 못했습니다."
    }

@Preview(showBackground = true)
@Composable
private fun DetailContentPreview() {
    NewsAppTheme {
        DetailContent(
            uiState = DetailUiState(article = MockNewsRepository.mockArticles.first()),
            onBackClick = {}
        )
    }
}

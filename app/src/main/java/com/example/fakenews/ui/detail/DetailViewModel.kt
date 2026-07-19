package com.example.fakenews.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fakenews.data.remote.NewsFetchLogger
import com.example.fakenews.data.repository.ArticleDetailRepository
import com.example.fakenews.data.repository.ArticleDetailRepositoryProvider
import com.example.fakenews.data.repository.NewsRepository
import com.example.fakenews.data.repository.NewsRepositoryProvider
import com.example.fakenews.util.UiText
import com.example.fakenews.util.UrlNormalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class DetailViewModel(
    private val repository: NewsRepository = NewsRepositoryProvider.repository,
    private val articleDetailRepository: ArticleDetailRepository = ArticleDetailRepositoryProvider.repository
) : ViewModel() {
    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    fun loadArticle(articleId: String?) {
        if (articleId.isNullOrBlank()) {
            _uiState.value = DetailUiState(emptyMessage = UiText.ARTICLE_NOT_FOUND)
            return
        }

        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(
                    isLoading = true,
                    errorMessage = null,
                    emptyMessage = null
                )
            }

            runCatching {
                val decodedArticleId = decodeRouteArticleId(articleId)
                val article = repository.getNewsArticleById(decodedArticleId)
                article?.let { loadedArticle ->
                    val decodedOriginalUrl = UrlNormalizer.decodeHtmlEntities(loadedArticle.originalUrl).trim()
                    NewsFetchLogger.logDetailLinkCheck(
                        "routeArticleId=$decodedArticleId loadedId=${loadedArticle.id} " +
                            "originalUrl=$decodedOriginalUrl " +
                            "normalizedOriginalUrl=${UrlNormalizer.normalize(decodedOriginalUrl)} " +
                            "idMatches=${loadedArticle.id == decodedArticleId} finalDetailFound=true"
                    )
                    val detailArticle = runCatching {
                        articleDetailRepository.enrichArticle(loadedArticle)
                    }.getOrDefault(loadedArticle).copy(
                        id = loadedArticle.id,
                        press = loadedArticle.press,
                        originalUrl = decodedOriginalUrl
                    )
                    val enrichedArticle = detailArticle.copy(
                        publishedAt = loadedArticle.publishedAt ?: detailArticle.publishedAt,
                        publishedAtSource = loadedArticle.publishedAtSource ?: detailArticle.publishedAtSource
                    )
                    if (enrichedArticle.originalUrl != loadedArticle.originalUrl) {
                        NewsFetchLogger.logValidation(
                            "detail_original_url_changed loadedId=${loadedArticle.id} " +
                                "before=${loadedArticle.originalUrl} after=${enrichedArticle.originalUrl}"
                        )
                    }
                    enrichedArticle
                }
            }.onSuccess { article ->
                _uiState.value = if (article == null) {
                    NewsFetchLogger.logDetailLinkCheck(
                        "routeArticleId=${decodeRouteArticleId(articleId)} " +
                            "finalDetailFound=false failureReason=cache_miss"
                    )
                    DetailUiState(emptyMessage = UiText.ARTICLE_NOT_FOUND)
                } else {
                    DetailUiState(article = article)
                }
            }.onFailure { error ->
                _uiState.value = DetailUiState(
                    errorMessage = error.message ?: UiText.ARTICLE_LOAD_FAILED
                )
            }
        }
    }

    private fun decodeRouteArticleId(articleId: String): String =
        UrlNormalizer.decodeHtmlEntities(
            runCatching {
                URLDecoder.decode(articleId, StandardCharsets.UTF_8.name())
            }.getOrDefault(articleId)
        ).trim()
}

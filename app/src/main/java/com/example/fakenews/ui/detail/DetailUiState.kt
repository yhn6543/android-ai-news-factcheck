package com.example.fakenews.ui.detail

import com.example.fakenews.data.model.NewsArticle

data class DetailUiState(
    val article: NewsArticle? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val emptyMessage: String? = null
)

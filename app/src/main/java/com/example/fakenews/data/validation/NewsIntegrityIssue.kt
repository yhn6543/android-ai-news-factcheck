package com.example.fakenews.data.validation

import com.example.fakenews.data.model.NewsPress
import com.example.fakenews.util.ArticleValidationSeverity

data class NewsIntegrityIssue(
    val press: NewsPress?,
    val severity: ArticleValidationSeverity,
    val code: String,
    val message: String,
    val articleId: String? = null,
    val title: String? = null,
    val originalUrl: String? = null
)

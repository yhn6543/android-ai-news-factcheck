package com.example.fakenews.ui.factcheck

import com.example.fakenews.data.model.FactCheckResult

data class FactCheckUiState(
    val inputText: String = "",
    val lastInputText: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val canRetry: Boolean = false,
    val canRetryWithMoreContext: Boolean = false,
    val showApiKeySetupMessage: Boolean = false,
    val emptyMessage: String? = null,
    val helperQuestionText: String? = null,
    val shouldFocusInput: Boolean = false,
    val result: FactCheckResult? = null
)

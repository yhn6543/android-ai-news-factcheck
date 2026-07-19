package com.example.fakenews.ui.factcheck

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fakenews.data.model.FactCheckVerdict
import com.example.fakenews.data.repository.FactCheckException
import com.example.fakenews.data.repository.FactCheckRepository
import com.example.fakenews.data.repository.FactCheckRepositoryProvider
import com.example.fakenews.data.repository.GeminiErrorMapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FactCheckViewModel(
    private val repository: FactCheckRepository = FactCheckRepositoryProvider.create()
) : ViewModel() {
    private val _uiState = MutableStateFlow(FactCheckUiState())
    val uiState: StateFlow<FactCheckUiState> = _uiState.asStateFlow()
    private var lastAnalyzeText: String? = null

    fun onInputChange(value: String) {
        _uiState.update { currentState ->
            currentState.copy(
                inputText = value,
                errorMessage = null,
                canRetry = false,
                showApiKeySetupMessage = false,
                emptyMessage = null
            )
        }
    }

    fun onAnalyzeClick() {
        if (_uiState.value.isLoading) return

        val originalText = _uiState.value.inputText
        val textForValidation = originalText.trim()
        if (textForValidation.isEmpty()) {
            _uiState.update { currentState ->
                currentState.copy(
                    isLoading = false,
                    errorMessage = "분석할 내용을 입력해 주세요.",
                    canRetry = false,
                    canRetryWithMoreContext = false,
                    showApiKeySetupMessage = false,
                    emptyMessage = null
                )
            }
            return
        }
        if (textForValidation.length < MIN_CLAIM_LENGTH) {
            _uiState.update { currentState ->
                currentState.copy(
                    isLoading = false,
                    errorMessage = "판별할 주장을 더 구체적으로 입력해 주세요.",
                    canRetry = false,
                    canRetryWithMoreContext = false,
                    showApiKeySetupMessage = false,
                    emptyMessage = null
                )
            }
            return
        }

        lastAnalyzeText = originalText
        analyze(originalText)
    }

    fun clearError() {
        _uiState.update { currentState ->
            currentState.copy(
                errorMessage = null,
                canRetry = false,
                showApiKeySetupMessage = false
            )
        }
    }

    fun retryAnalyze() {
        if (_uiState.value.isLoading) return

        val text = lastAnalyzeText ?: _uiState.value.inputText.trim()
        if (text.isBlank()) {
            onAnalyzeClick()
            return
        }
        analyze(text)
    }

    fun onAskMoreContextClick() {
        val missingContext = _uiState.value.result?.missingContext.orEmpty()
        if (missingContext.isEmpty()) return

        _uiState.update { currentState ->
            currentState.copy(
                helperQuestionText = missingContext.toHelperQuestionText(),
                shouldFocusInput = true
            )
        }
    }

    fun onInputFocusHandled() {
        _uiState.update { currentState ->
            currentState.copy(shouldFocusInput = false)
        }
    }

    private fun analyze(text: String) {
        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(
                    lastInputText = text,
                    isLoading = true,
                    errorMessage = null,
                    canRetry = false,
                    canRetryWithMoreContext = false,
                    showApiKeySetupMessage = false,
                    emptyMessage = null,
                    helperQuestionText = null,
                    shouldFocusInput = false,
                    result = null
                )
            }

            runCatching {
                repository.analyze(text)
            }.onSuccess { result ->
                _uiState.update { currentState ->
                    currentState.copy(
                        isLoading = false,
                        result = result,
                        errorMessage = null,
                        canRetry = false,
                        canRetryWithMoreContext = result.verdict == FactCheckVerdict.NEEDS_MORE_CONTEXT &&
                            result.missingContext.isNotEmpty(),
                        showApiKeySetupMessage = false,
                        emptyMessage = if (result.sourceCount == 0) {
                            "참고 출처가 없습니다. 추가 확인이 필요합니다."
                        } else {
                            null
                        }
                    )
                }
            }.onFailure { error ->
                val mappedError = GeminiErrorMapper.fromThrowable(error)
                val canRetry = canRetry(mappedError)
                val showApiKeySetupMessage = mappedError == FactCheckException.MissingApiKey
                logFactCheck(
                    "analysis_failed errorType=${GeminiErrorMapper.errorType(mappedError)} " +
                        "canRetry=$canRetry"
                )
                _uiState.update { currentState ->
                    currentState.copy(
                        isLoading = false,
                        errorMessage = mappedError.message ?: "분석 중 오류가 발생했습니다.",
                        canRetry = canRetry,
                        canRetryWithMoreContext = false,
                        showApiKeySetupMessage = showApiKeySetupMessage,
                        emptyMessage = null,
                        shouldFocusInput = false,
                        result = null
                    )
                }
            }
        }
    }

    private fun canRetry(error: FactCheckException): Boolean =
        when (error) {
            FactCheckException.MissingApiKey,
            FactCheckException.ApiKeyOrPermission -> false
            else -> true
        }

    private fun logFactCheck(message: String) {
        runCatching {
            Log.d(TAG_FACT_CHECK, message)
        }
    }

    private fun List<String>.toHelperQuestionText(): String =
        "추가 정보: " + joinToString(" ") { question ->
            question.trim().removeSuffix(".").removeSuffix("?")
        } + " 입력해 주세요."

    private companion object {
        const val TAG_FACT_CHECK = "FACT_CHECK"
        const val MIN_CLAIM_LENGTH = 6
    }
}

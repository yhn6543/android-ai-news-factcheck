package com.example.fakenews.ui.factcheck

import com.example.fakenews.MainDispatcherRule
import com.example.fakenews.data.model.FactCheckVerdict
import com.example.fakenews.data.model.FactCheckResult
import com.example.fakenews.data.repository.FactCheckException
import com.example.fakenews.data.repository.FactCheckRepository
import com.example.fakenews.data.repository.GeminiFactCheckRepository
import com.example.fakenews.data.repository.MockFactCheckRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FactCheckViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun blankInputShowsErrorWithoutAnalyzing() = runTest {
        val viewModel = FactCheckViewModel(repository = MockFactCheckRepository())

        viewModel.onInputChange("   ")
        viewModel.onAnalyzeClick()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.result)
        assertTrue(state.errorMessage == "분석할 내용을 입력해 주세요.")
    }

    @Test
    fun tooShortInputShowsSpecificErrorWithoutAnalyzing() = runTest {
        val repository = RecordingFactCheckRepository(
            outcomes = listOf(Result.success(factCheckResult()))
        )
        val viewModel = FactCheckViewModel(repository = repository)

        viewModel.onInputChange("주장")
        viewModel.onAnalyzeClick()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.result)
        assertEquals("판별할 주장을 더 구체적으로 입력해 주세요.", state.errorMessage)
        assertTrue(repository.inputs.isEmpty())
    }

    @Test
    fun validInputShowsLoadingThenResult() = runTest {
        val viewModel = FactCheckViewModel(repository = MockFactCheckRepository())

        viewModel.onInputChange("경제 관련 의심 문장")
        viewModel.onAnalyzeClick()

        assertTrue(viewModel.uiState.value.isLoading)

        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertNotNull(state.result)
        assertEquals(0, state.result?.sourceCount)
    }

    @Test
    fun missingGeminiApiKeyShowsClearErrorState() = runTest {
        val viewModel = FactCheckViewModel(
            repository = GeminiFactCheckRepository(
                apiKey = ""
            )
        )

        viewModel.onInputChange("검증할 문장")
        viewModel.onAnalyzeClick()

        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.result)
        assertEquals("Gemini API Key가 설정되지 않았습니다.", state.errorMessage)
    }

    @Test
    fun parseFailureShowsClearErrorState() = runTest {
        val viewModel = FactCheckViewModel(
            repository = ThrowingFactCheckRepository(FactCheckException.ParseFailure)
        )

        viewModel.onInputChange("검증할 문장")
        viewModel.onAnalyzeClick()

        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.result)
        assertEquals("AI 응답을 해석하지 못했습니다. 다시 시도해 주세요.", state.errorMessage)
        assertTrue(state.canRetry)
    }

    @Test
    fun errorKeepsInputTextAndShowsRetryForTransientFailure() = runTest {
        val viewModel = FactCheckViewModel(
            repository = ThrowingFactCheckRepository(FactCheckException.ServerOverloaded)
        )

        viewModel.onInputChange("검증할 문장")
        viewModel.onAnalyzeClick()

        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("검증할 문장", state.inputText)
        assertEquals("Gemini 서버가 일시적으로 혼잡합니다. 잠시 후 다시 시도해 주세요.", state.errorMessage)
        assertTrue(state.canRetry)
        assertFalse(state.showApiKeySetupMessage)
    }

    @Test
    fun retryAnalyzeUsesLastSubmittedInput() = runTest {
        val repository = RecordingFactCheckRepository(
            outcomes = listOf(
                Result.failure(FactCheckException.ServerOverloaded),
                Result.success(factCheckResult())
            )
        )
        val viewModel = FactCheckViewModel(repository = repository)

        viewModel.onInputChange("처음 제출한 문장")
        viewModel.onAnalyzeClick()
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onInputChange("아직 제출하지 않은 수정 문장")
        viewModel.retryAnalyze()
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("처음 제출한 문장", "처음 제출한 문장"), repository.inputs)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNotNull(viewModel.uiState.value.result)
    }

    @Test
    fun noRelatedEvidenceKeepsResultAndShowsEmptyMessage() = runTest {
        val viewModel = FactCheckViewModel(
            repository = RecordingFactCheckRepository(
                outcomes = listOf(Result.success(factCheckResult()))
            )
        )

        viewModel.onInputChange("삼성전자 주가 40만원 돌파")
        viewModel.onAnalyzeClick()
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.result)
        assertEquals(0, state.result?.sourceCount)
        assertEquals("참고 출처가 없습니다. 추가 확인이 필요합니다.", state.emptyMessage)
    }

    @Test
    fun apiKeyMissingShowsSetupMessageWithoutRetry() = runTest {
        val viewModel = FactCheckViewModel(
            repository = ThrowingFactCheckRepository(FactCheckException.MissingApiKey)
        )

        viewModel.onInputChange("검증할 문장")
        viewModel.onAnalyzeClick()
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Gemini API Key가 설정되지 않았습니다.", state.errorMessage)
        assertFalse(state.canRetry)
        assertTrue(state.showApiKeySetupMessage)
    }

    @Test
    fun needsMoreContextResultKeepsMissingContextAndEnablesContextRetry() = runTest {
        val result = factCheckResult(
            verdict = FactCheckVerdict.NEEDS_MORE_CONTEXT,
            missingContext = listOf("어느 연도와 대회 기준인가요?")
        )
        val viewModel = FactCheckViewModel(
            repository = RecordingFactCheckRepository(
                outcomes = listOf(Result.success(result))
            )
        )

        viewModel.onInputChange("한국 월드컵 체코한테 패배")
        viewModel.onAnalyzeClick()
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("한국 월드컵 체코한테 패배", state.inputText)
        assertEquals("한국 월드컵 체코한테 패배", state.lastInputText)
        assertTrue(state.canRetryWithMoreContext)
        assertEquals(listOf("어느 연도와 대회 기준인가요?"), state.result?.missingContext)
    }

    @Test
    fun askMoreContextKeepsInputAndRequestsFocusWithHelperText() = runTest {
        val result = factCheckResult(
            verdict = FactCheckVerdict.NEEDS_MORE_CONTEXT,
            missingContext = listOf("어느 연도와 대회 기준인가요?")
        )
        val viewModel = FactCheckViewModel(
            repository = RecordingFactCheckRepository(
                outcomes = listOf(Result.success(result))
            )
        )

        viewModel.onInputChange("한국 월드컵 체코한테 패배")
        viewModel.onAnalyzeClick()
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onAskMoreContextClick()

        val state = viewModel.uiState.value
        assertEquals("한국 월드컵 체코한테 패배", state.inputText)
        assertEquals("추가 정보: 어느 연도와 대회 기준인가요 입력해 주세요.", state.helperQuestionText)
        assertTrue(state.shouldFocusInput)
    }

    @Test
    fun inputFocusHandledResetsFocusEventOnly() = runTest {
        val result = factCheckResult(
            verdict = FactCheckVerdict.NEEDS_MORE_CONTEXT,
            missingContext = listOf("어느 연도인가요?")
        )
        val viewModel = FactCheckViewModel(
            repository = RecordingFactCheckRepository(
                outcomes = listOf(Result.success(result))
            )
        )

        viewModel.onInputChange("한국 월드컵 체코한테 패배")
        viewModel.onAnalyzeClick()
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onAskMoreContextClick()
        viewModel.onInputFocusHandled()

        val state = viewModel.uiState.value
        assertFalse(state.shouldFocusInput)
        assertEquals("추가 정보: 어느 연도인가요 입력해 주세요.", state.helperQuestionText)
    }

    @Test
    fun inputChangeKeepsHelperQuestionText() = runTest {
        val result = factCheckResult(
            verdict = FactCheckVerdict.NEEDS_MORE_CONTEXT,
            missingContext = listOf("어느 연도인가요?")
        )
        val viewModel = FactCheckViewModel(
            repository = RecordingFactCheckRepository(
                outcomes = listOf(Result.success(result))
            )
        )

        viewModel.onInputChange("한국 월드컵 체코한테 패배")
        viewModel.onAnalyzeClick()
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onAskMoreContextClick()
        viewModel.onInputChange("2022년 한국 월드컵 체코한테 패배")

        val state = viewModel.uiState.value
        assertEquals("2022년 한국 월드컵 체코한테 패배", state.inputText)
        assertEquals("추가 정보: 어느 연도인가요 입력해 주세요.", state.helperQuestionText)
    }

    @Test
    fun loadingStatePreventsDuplicateAnalyzeRequests() = runTest {
        val repository = SuspendedFactCheckRepository(factCheckResult())
        val viewModel = FactCheckViewModel(repository = repository)

        viewModel.onInputChange("중복 요청 방지 테스트")
        viewModel.onAnalyzeClick()
        repository.started.await()
        viewModel.onAnalyzeClick()

        assertEquals(listOf("중복 요청 방지 테스트"), repository.inputs)

        repository.release.complete(Unit)
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLoading)
    }

    private class ThrowingFactCheckRepository(
        private val error: Throwable
    ) : FactCheckRepository {
        override suspend fun analyze(text: String): FactCheckResult {
            throw error
        }
    }

    private class RecordingFactCheckRepository(
        private val outcomes: List<Result<FactCheckResult>>
    ) : FactCheckRepository {
        val inputs = mutableListOf<String>()
        private var index = 0

        override suspend fun analyze(text: String): FactCheckResult {
            inputs += text
            val outcome = outcomes[index.coerceAtMost(outcomes.lastIndex)]
            index += 1
            return outcome.getOrThrow()
        }
    }

    private class SuspendedFactCheckRepository(
        private val result: FactCheckResult
    ) : FactCheckRepository {
        val inputs = mutableListOf<String>()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        override suspend fun analyze(text: String): FactCheckResult {
            inputs += text
            started.complete(Unit)
            release.await()
            return result
        }
    }

    private fun factCheckResult(
        verdict: FactCheckVerdict = FactCheckVerdict.TRUE,
        missingContext: List<String> = emptyList()
    ): FactCheckResult =
        FactCheckResult(
            finalSummary = "요약",
            riskLevel = com.example.fakenews.data.model.FactCheckRiskLevel.LOW,
            fakeNewsPossibility = 10,
            logicAnalysis = "논리",
            emotionalBiasAnalysis = "감정",
            sourceReliabilityAnalysis = "출처",
            exaggerationAnalysis = "과장",
            factsVsOpinions = "구분",
            additionalChecks = listOf("확인"),
            recommendedSearchKeywords = listOf("경제"),
            relatedArticleCount = 0,
            relatedArticles = emptyList(),
            verdict = verdict,
            missingContext = missingContext
        )
}

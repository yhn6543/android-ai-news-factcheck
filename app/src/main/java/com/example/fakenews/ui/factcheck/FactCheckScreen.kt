package com.example.fakenews.ui.factcheck

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fakenews.data.model.FactCheckClaimCategory
import com.example.fakenews.data.model.FactCheckResult
import com.example.fakenews.data.model.FactCheckRiskLevel
import com.example.fakenews.data.model.FactCheckTimeSensitivity
import com.example.fakenews.data.model.FactCheckVerdict
import com.example.fakenews.data.model.GroundedSource
import com.example.fakenews.data.model.NewsArticle
import com.example.fakenews.ui.components.EmptyStateView
import com.example.fakenews.ui.components.ErrorMessage
import com.example.fakenews.ui.components.FactCheckResultCard
import com.example.fakenews.ui.components.LoadingView
import com.example.fakenews.ui.theme.NewsAppTheme

@Composable
fun FactCheckScreen(
    onBackClick: () -> Unit,
    onArticleClick: (NewsArticle) -> Unit,
    viewModel: FactCheckViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    FactCheckContent(
        uiState = uiState,
        onBackClick = onBackClick,
        onInputChange = viewModel::onInputChange,
        onAnalyzeClick = viewModel::onAnalyzeClick,
        onRetryAnalyze = viewModel::retryAnalyze,
        onAskMoreContextClick = viewModel::onAskMoreContextClick,
        onInputFocusHandled = viewModel::onInputFocusHandled
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FactCheckContent(
    uiState: FactCheckUiState,
    onBackClick: () -> Unit,
    onInputChange: (String) -> Unit,
    onAnalyzeClick: () -> Unit,
    onRetryAnalyze: () -> Unit,
    onAskMoreContextClick: () -> Unit,
    onInputFocusHandled: () -> Unit,
    modifier: Modifier = Modifier
) {
    val inputFocusRequester = remember { FocusRequester() }
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(uiState.shouldFocusInput) {
        if (uiState.shouldFocusInput) {
            runCatching { inputFocusRequester.requestFocus() }
            onInputFocusHandled()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = "가짜뉴스 판별") },
                actions = {
                    OutlinedButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .heightIn(min = 36.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "뒤로",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "뉴스 내용이나 의심되는 문장을 입력하세요.",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            item {
                Text(
                    text = "AI 분석 결과는 참고용이며 최종 사실 확인은 신뢰 가능한 출처로 추가 확인하세요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                OutlinedTextField(
                    value = uiState.inputText,
                    onValueChange = onInputChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(inputFocusRequester)
                        .heightIn(min = 180.dp),
                    placeholder = {
                        Text(text = "예: 기사 본문이나 의심되는 문장을 붙여넣어 주세요.")
                    },
                    minLines = 6,
                    maxLines = 12
                )
            }
            uiState.helperQuestionText?.let { helperText ->
                item {
                    Text(
                        text = helperText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            item {
                Button(
                    onClick = onAnalyzeClick,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoading
                ) {
                    Text(text = "판별하기")
                }
            }

            uiState.errorMessage?.let { errorMessage ->
                item {
                    ErrorMessage(message = errorMessage)
                }
                if (uiState.showApiKeySetupMessage) {
                    item {
                        EmptyStateView(
                            message = "local.properties 또는 Gradle property에 GEMINI_API_KEY를 설정해 주세요."
                        )
                    }
                } else if (uiState.canRetry) {
                    item {
                        OutlinedButton(
                            onClick = onRetryAnalyze,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !uiState.isLoading
                        ) {
                            Text(text = "다시 시도")
                        }
                    }
                }
            }

            if (uiState.isLoading) {
                item {
                    LoadingView(message = "AI 분석 중입니다.")
                }
            }

            if (!uiState.isLoading && uiState.errorMessage == null && uiState.result == null) {
                item {
                    EmptyStateView(message = "분석 결과가 아직 없습니다.")
                }
            }

            uiState.result?.let { result ->
                item {
                    FactCheckResultCard(
                        result = result,
                        onAskMoreContextClick = onAskMoreContextClick,
                        onOpenSource = { uri ->
                            runCatching { uriHandler.openUri(uri) }
                        }
                    )
                }
            }

        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FactCheckContentPreview() {
    NewsAppTheme {
        FactCheckContent(
            uiState = FactCheckUiState(
                inputText = "경제 관련 의심 문장",
                result = MockFactCheckRepositoryPreview.result,
                helperQuestionText = "추가 정보: 어느 날짜 기준인지 입력해 주세요."
            ),
            onBackClick = {},
            onInputChange = {},
            onAnalyzeClick = {},
            onRetryAnalyze = {},
            onAskMoreContextClick = {},
            onInputFocusHandled = {}
        )
    }
}

private object MockFactCheckRepositoryPreview {
    val result = FactCheckResult(
        finalSummary = "기준 시점이 없어 추가 정보가 필요합니다.",
        riskLevel = FactCheckRiskLevel.MEDIUM,
        fakeNewsPossibility = 50,
        logicAnalysis = "근거와 결론의 연결성을 추가 확인하세요.",
        emotionalBiasAnalysis = "감정적 표현은 낮은 편입니다.",
        sourceReliabilityAnalysis = "출처 교차 확인이 필요합니다.",
        exaggerationAnalysis = "과장 표현 여부를 원문과 비교하세요.",
        factsVsOpinions = "확인 가능한 사실과 해석을 분리하세요.",
        additionalChecks = listOf("공식 자료 확인", "다른 매체 비교"),
        recommendedSearchKeywords = emptyList(),
        relatedArticleCount = 2,
        relatedArticles = emptyList(),
        verdict = FactCheckVerdict.NEEDS_MORE_CONTEXT,
        confidenceScore = 30,
        reason = "주가 주장은 기준 시점에 따라 사실 여부가 달라질 수 있습니다.",
        evidenceSummary = "검색 근거가 부족합니다.",
        missingContext = listOf("어느 날짜 기준의 주가인가요?"),
        recommendedChecks = listOf("거래소 공시 또는 공식 시세를 확인하세요."),
        sourceCount = 2,
        sources = listOf(
            GroundedSource("참고 출처 1", "https://example.com/1"),
            GroundedSource("참고 출처 2", "https://example.com/2")
        ),
        searchQueriesUsed = listOf("삼성전자 주가 40만원"),
        claimCategory = FactCheckClaimCategory.FINANCE,
        timeSensitivity = FactCheckTimeSensitivity.HIGH,
        originalText = "삼성전자 주가 40만원 돌파"
    )
}

package com.example.fakenews.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.fakenews.data.model.FactCheckResult
import com.example.fakenews.data.model.FactCheckTimeSensitivity
import com.example.fakenews.data.model.FactCheckVerdict
import com.example.fakenews.data.model.GroundedSource
import com.example.fakenews.ui.theme.toComposeColor
import com.example.fakenews.util.FactCheckResultColorPolicy

@Composable
fun FactCheckResultCard(
    result: FactCheckResult,
    modifier: Modifier = Modifier,
    onAskMoreContextClick: () -> Unit = {},
    onOpenSource: (String) -> Unit = {}
) {
    var showAllSources by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = FactCheckResultColorPolicy
                .backgroundColorHex(result.verdict)
                .toComposeColor(),
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "분석 결과",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            if (result.originalText.isNotBlank()) {
                ResultLine(label = "사용자 입력 원문", value = result.originalText)
            }

            ResultLine(label = "최종 판정", value = result.verdict.userMessage())
            ResultLine(label = "AI 판정 신뢰도", value = "${result.confidenceScore}%")
            Text(
                text = "신뢰도는 주장이 사실일 확률이 아니라, 위 판정 결과에 대한 AI의 확신도입니다.",
                style = MaterialTheme.typography.bodySmall
            )

            if (result.finalSummary.isNotBlank()) {
                ResultLine(label = "판단 요약", value = result.finalSummary)
            }
            if (result.reason.isNotBlank()) {
                ResultLine(label = "판단 이유", value = result.reason)
            }
            if (result.evidenceSummary.isNotBlank()) {
                ResultLine(label = "근거 요약", value = result.evidenceSummary)
            }

            ContextSection(
                result = result,
                onAskMoreContextClick = onAskMoreContextClick
            )

            ResultList(
                label = "추가 확인 방법",
                values = result.recommendedChecks.ifEmpty { result.additionalChecks }
            )

            SearchQuerySection(searchQueries = result.searchQueriesUsed)

            SourceSection(
                result = result,
                showAllSources = showAllSources,
                onShowAllSources = { showAllSources = true },
                onOpenSource = onOpenSource
            )

            if (result.usedInternalKnowledge) {
                Text(
                    text = "검색 출처 없이 일반 지식 기반으로 판단했습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ContextSection(
    result: FactCheckResult,
    onAskMoreContextClick: () -> Unit
) {
    if (result.missingContext.isEmpty()) return

    if (result.verdict == FactCheckVerdict.NEEDS_MORE_CONTEXT) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "추가 정보가 필요합니다",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            ResultList(
                label = "추가로 필요한 정보 질문",
                values = result.missingContext
            )
            OutlinedButton(
                onClick = onAskMoreContextClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "이 내용을 보완하여 다시 물어보기")
            }
        }
    } else {
        ResultList(label = "부족한 맥락", values = result.missingContext)
    }
}

@Composable
private fun SearchQuerySection(searchQueries: List<String>) {
    if (searchQueries.isEmpty()) {
        ResultLine(label = "사용된 검색어", value = "사용된 검색어 없음")
        return
    }

    ResultList(label = "사용된 검색어", values = searchQueries)
}

@Composable
private fun SourceSection(
    result: FactCheckResult,
    showAllSources: Boolean,
    onShowAllSources: () -> Unit,
    onOpenSource: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ResultLine(label = "참고 출처 수", value = "${result.sourceCount}개")

        if (result.sourceCount == 0) {
            Text(
                text = "참고 출처 없음",
                style = MaterialTheme.typography.bodyMedium
            )
            if (result.timeSensitivity == FactCheckTimeSensitivity.HIGH) {
                Text(
                    text = "검색 근거가 부족하여 단정할 수 없습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            return
        }

        Text(
            text = "참고 출처 목록",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )

        if (result.sources.isEmpty()) {
            Text(
                text = "출처 제목을 확인할 수 없습니다.",
                style = MaterialTheme.typography.bodyMedium
            )
            return
        }

        val visibleSources = if (showAllSources) {
            result.sources
        } else {
            result.sources.take(MAX_VISIBLE_SOURCES)
        }
        visibleSources.forEachIndexed { index, source ->
            SourceRow(
                index = index + 1,
                source = source,
                onOpenSource = onOpenSource
            )
        }

        val hiddenCount = result.sources.size - visibleSources.size
        if (hiddenCount > 0) {
            TextButton(onClick = onShowAllSources) {
                Text(text = "출처 ${hiddenCount}개 더보기")
            }
        }
    }
}

@Composable
private fun SourceRow(
    index: Int,
    source: GroundedSource,
    onOpenSource: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$index. ${source.title.ifBlank { source.uri }}",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
        if (source.uri.isNotBlank()) {
            TextButton(onClick = { onOpenSource(source.uri) }) {
                Text(text = "원문 열기")
            }
        }
    }
}

private fun FactCheckVerdict.userMessage(): String =
    when (this) {
        FactCheckVerdict.TRUE -> "사실 가능성 높음"
        FactCheckVerdict.FALSE -> "거짓 가능성 높음"
        FactCheckVerdict.MISLEADING -> "오해의 소지가 있음"
        FactCheckVerdict.PARTLY_TRUE -> "일부 사실"
        FactCheckVerdict.UNVERIFIABLE -> "검증 불가"
        FactCheckVerdict.NEEDS_MORE_CONTEXT -> "추가 정보 필요"
    }

@Composable
private fun ResultLine(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ResultList(
    label: String,
    values: List<String>,
    modifier: Modifier = Modifier
) {
    if (values.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        values.forEach { value ->
            Text(
                text = "- $value",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private const val MAX_VISIBLE_SOURCES = 5

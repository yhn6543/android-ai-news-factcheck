package com.example.fakenews.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fakenews.data.model.NewsArticle
import com.example.fakenews.data.model.NewsPress
import com.example.fakenews.data.repository.MockNewsRepository
import com.example.fakenews.ui.components.EmptyStateView
import com.example.fakenews.ui.components.ErrorMessage
import com.example.fakenews.ui.components.KeywordChip
import com.example.fakenews.ui.components.LoadingView
import com.example.fakenews.ui.components.NewsArticleCard
import com.example.fakenews.ui.components.PressToggleButton
import com.example.fakenews.ui.theme.NewsAppTheme

@Composable
fun MainScreen(
    onFactCheckClick: () -> Unit,
    onArticleClick: (NewsArticle) -> Unit = {},
    viewModel: MainViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    MainContent(
        uiState = uiState,
        onFactCheckClick = onFactCheckClick,
        onPressToggle = viewModel::onPressToggle,
        onKeywordInputChange = viewModel::onKeywordInputChange,
        onAddKeyword = viewModel::onAddKeyword,
        onRemoveKeyword = viewModel::onRemoveKeyword,
        onSearchClick = viewModel::onSearchClick,
        onArticleClick = onArticleClick
    )
}

@Composable
private fun MainContent(
    uiState: MainUiState,
    onFactCheckClick: () -> Unit,
    onPressToggle: (NewsPress) -> Unit,
    onKeywordInputChange: (String) -> Unit,
    onAddKeyword: () -> Unit,
    onRemoveKeyword: (String) -> Unit,
    onSearchClick: () -> Unit,
    onArticleClick: (NewsArticle) -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
            contentPadding = PaddingValues(
                start = 20.dp,
                top = 20.dp,
                end = 20.dp,
                bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Fake News",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    FilledTonalButton(
                        onClick = onFactCheckClick,
                        modifier = Modifier.height(40.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "가짜뉴스 판별하기",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = NewsPress.selectablePresses(),
                        key = { press -> press.name }
                    ) { press ->
                        PressToggleButton(
                            press = press,
                            selected = if (press == NewsPress.ALL) {
                                uiState.isAllPressesSelected
                            } else {
                                press in uiState.selectedPresses
                            },
                            onClick = { onPressToggle(press) }
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.keywordInput,
                        onValueChange = onKeywordInputChange,
                        modifier = Modifier.weight(1f),
                        label = { Text(text = "키워드") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onAddKeyword() })
                    )
                    Button(
                        onClick = onAddKeyword,
                        modifier = Modifier.height(56.dp)
                    ) {
                        Text(text = "등록")
                    }
                }
            }

            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = uiState.registeredKeywords,
                        key = { keyword -> keyword }
                    ) { keyword ->
                        KeywordChip(
                            keyword = keyword,
                            onRemoveClick = { onRemoveKeyword(keyword) }
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = onSearchClick,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoading && !uiState.isCollectionInProgress
                ) {
                    Text(text = "탐색")
                }
            }

            if (uiState.isLoading) {
                item {
                    LoadingView(message = "뉴스를 불러오는 중입니다.")
                }
            }

            uiState.infoMessage?.let { infoMessage ->
                item {
                    EmptyStateView(message = infoMessage)
                }
            }

            uiState.errorMessage?.let { errorMessage ->
                item {
                    ErrorMessage(message = errorMessage)
                }
            }

            uiState.emptyMessage?.let { emptyMessage ->
                item {
                    EmptyStateView(message = emptyMessage)
                }
            }

            items(
                items = uiState.articles,
                key = { article -> article.id }
            ) { article ->
                NewsArticleCard(
                    article = article,
                    onClick = { onArticleClick(article) },
                    showMatchedKeywords = uiState.isSearchMode
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MainContentPreview() {
    NewsAppTheme {
        MainContent(
            uiState = MainUiState(
                articles = MockNewsRepository.mockArticles.take(3)
            ),
            onFactCheckClick = {},
            onPressToggle = {},
            onKeywordInputChange = {},
            onAddKeyword = {},
            onRemoveKeyword = {},
            onSearchClick = {},
            onArticleClick = {}
        )
    }
}

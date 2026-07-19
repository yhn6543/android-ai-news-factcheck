package com.example.fakenews.ui.main

import com.example.fakenews.MainDispatcherRule
import com.example.fakenews.data.model.NewsPress
import com.example.fakenews.data.repository.MockNewsRepository
import com.example.fakenews.domain.usecase.GetFilteredNewsUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NewsPressSelectionTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun viewModel(): MainViewModel =
        MainViewModel(
            newsRepositoryStatusProvider = null,
            getFilteredNewsUseCase = GetFilteredNewsUseCase(MockNewsRepository())
        )

    @Test
    fun defaultSelectionIsAll() = runTest {
        val viewModel = viewModel()

        assertEquals(NewsPress.articlePresses().toSet(), viewModel.uiState.value.selectedPresses)
        assertEquals(true, viewModel.uiState.value.isAllPressesSelected)
    }

    @Test
    fun clickingAllWhenEveryPressIsSelectedClearsSelection() = runTest {
        val viewModel = viewModel()

        viewModel.onPressToggle(NewsPress.ALL)

        assertEquals(emptySet<NewsPress>(), viewModel.uiState.value.selectedPresses)
        assertEquals(false, viewModel.uiState.value.isAllPressesSelected)
    }

    @Test
    fun clickingAllWhenNoPressIsSelectedSelectsEveryArticlePress() = runTest {
        val viewModel = viewModel()

        viewModel.onPressToggle(NewsPress.ALL)
        viewModel.onPressToggle(NewsPress.ALL)

        assertEquals(NewsPress.articlePresses().toSet(), viewModel.uiState.value.selectedPresses)
        assertEquals(true, viewModel.uiState.value.isAllPressesSelected)
    }

    @Test
    fun clickingAllWhenPartiallySelectedSelectsEveryArticlePress() = runTest {
        val viewModel = viewModel()

        viewModel.onPressToggle(NewsPress.MBC)
        viewModel.onPressToggle(NewsPress.ALL)

        assertEquals(NewsPress.articlePresses().toSet(), viewModel.uiState.value.selectedPresses)
        assertEquals(true, viewModel.uiState.value.isAllPressesSelected)
    }

    @Test
    fun togglingUnselectedSpecificPressAddsIt() = runTest {
        val viewModel = viewModel()

        viewModel.onPressToggle(NewsPress.ALL)
        viewModel.onPressToggle(NewsPress.MBC)

        assertEquals(setOf(NewsPress.MBC), viewModel.uiState.value.selectedPresses)
    }

    @Test
    fun togglingSelectedSpecificPressRemovesIt() = runTest {
        val viewModel = viewModel()

        viewModel.onPressToggle(NewsPress.ALL)
        viewModel.onPressToggle(NewsPress.MBC)
        viewModel.onPressToggle(NewsPress.MBC)

        assertEquals(emptySet<NewsPress>(), viewModel.uiState.value.selectedPresses)
    }

    @Test
    fun allUiSelectedIsFalseWhenAnySpecificPressIsMissing() = runTest {
        val viewModel = viewModel()

        viewModel.onPressToggle(NewsPress.YTN)

        assertEquals(false, viewModel.uiState.value.isAllPressesSelected)
    }

    @Test
    fun allSelectionTargetsFiveArticlePresses() = runTest {
        val viewModel = viewModel()

        assertEquals(5, NewsPress.articlePresses().size)
        assertEquals(NewsPress.articlePresses().toSet(), viewModel.uiState.value.selectedPresses)
    }
}

package com.example.fakenews.data.repository

import com.example.fakenews.AppContainer

object FactCheckRepositoryProvider {
    val useMockFactCheck: Boolean
        get() = AppContainer.USE_MOCK_FACTCHECK

    fun create(): FactCheckRepository =
        AppContainer.factCheckRepository
}

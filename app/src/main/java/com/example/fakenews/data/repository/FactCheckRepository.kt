package com.example.fakenews.data.repository

import com.example.fakenews.data.model.FactCheckResult

interface FactCheckRepository {
    suspend fun analyze(text: String): FactCheckResult
}

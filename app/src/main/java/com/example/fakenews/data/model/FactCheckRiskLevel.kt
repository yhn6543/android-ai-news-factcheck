package com.example.fakenews.data.model

enum class FactCheckRiskLevel(
    val displayName: String
) {
    LOW("낮음"),
    MEDIUM("주의"),
    HIGH("높음"),
    UNKNOWN("알 수 없음")
}

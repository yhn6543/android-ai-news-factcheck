package com.example.fakenews.data.model

enum class FactCheckVerdict(
    val displayName: String
) {
    TRUE("사실"),
    FALSE("거짓"),
    MISLEADING("오해 소지"),
    PARTLY_TRUE("일부 사실"),
    UNVERIFIABLE("확인 불가"),
    NEEDS_MORE_CONTEXT("맥락 부족")
}

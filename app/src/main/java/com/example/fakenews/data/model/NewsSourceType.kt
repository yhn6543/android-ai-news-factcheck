package com.example.fakenews.data.model

enum class NewsSourceType(
    val displayName: String
) {
    RSS("RSS"),
    PUBLIC_API("Public API"),
    SEARCH_RSS("Search RSS"),
    HTML_CRAWLING("HTML Crawling"),
    NOT_FOUND("Not Found"),
    MOCK("Mock")
}

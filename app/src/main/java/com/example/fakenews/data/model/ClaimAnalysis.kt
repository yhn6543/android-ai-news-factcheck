package com.example.fakenews.data.model

data class ClaimAnalysis(
    val originalText: String,
    val mainClaim: String,
    val entities: List<String> = emptyList(),
    val topicType: ClaimTopicType = ClaimTopicType.UNKNOWN,
    val numericValue: String? = null,
    val dateOrTimeHint: String? = null,
    val ambiguityQuestions: List<String> = emptyList(),
    val searchQueries: List<String> = emptyList()
)

enum class ClaimTopicType {
    SPORTS,
    FINANCE,
    POLITICS,
    SOCIAL,
    ECONOMY,
    TECHNOLOGY,
    UNKNOWN
}

package com.example.fakenews.util

import com.example.fakenews.data.model.ClaimAnalysis
import com.example.fakenews.data.model.ClaimTopicType
import java.util.Locale

object ClaimSearchQueryBuilder {
    fun analyze(inputText: String): ClaimAnalysis {
        val originalText = inputText.trim()
        val mainClaim = originalText.replace(Regex("\\s+"), " ")
        val topicType = detectTopicType(mainClaim)
        val numericValue = extractNumericValue(mainClaim)
        val dateOrTimeHint = extractDateOrTimeHint(mainClaim)
        val entities = extractEntities(mainClaim, topicType)
        val ambiguityQuestions = ambiguityQuestions(
            claim = mainClaim,
            topicType = topicType,
            dateOrTimeHint = dateOrTimeHint
        )
        val searchQueries = buildSearchQueries(
            mainClaim = mainClaim,
            topicType = topicType,
            entities = entities,
            numericValue = numericValue
        )

        return ClaimAnalysis(
            originalText = originalText,
            mainClaim = mainClaim,
            entities = entities,
            topicType = topicType,
            numericValue = numericValue,
            dateOrTimeHint = dateOrTimeHint,
            ambiguityQuestions = ambiguityQuestions,
            searchQueries = searchQueries
        )
    }

    fun evidenceKeywords(analysis: ClaimAnalysis): List<String> =
        (analysis.searchQueries.flatMap { query -> KeywordExtractor.extract(query, limit = 6) } +
            analysis.entities +
            listOfNotNull(analysis.numericValue))
            .map { keyword -> keyword.trim() }
            .filter { keyword -> keyword.length >= 2 }
            .distinctBy { keyword -> keyword.lowercase(Locale.ROOT) }
            .take(MAX_EVIDENCE_KEYWORDS)

    private fun detectTopicType(claim: String): ClaimTopicType {
        val lower = claim.lowercase(Locale.ROOT)
        return when {
            listOf("월드컵", "경기", "패배", "승리", "올림픽", "축구", "야구", "농구").any(lower::contains) ->
                ClaimTopicType.SPORTS
            listOf("주가", "주식", "코스피", "코스닥", "환율", "시총", "상장").any(lower::contains) ->
                ClaimTopicType.FINANCE
            listOf("대통령", "국회", "정당", "의원", "정치인", "선거", "정부").any(lower::contains) ->
                ClaimTopicType.POLITICS
            listOf("사건", "사고", "재난", "화재", "지진", "범죄").any(lower::contains) ->
                ClaimTopicType.SOCIAL
            listOf("경제", "물가", "금리", "수출", "수입", "고용").any(lower::contains) ->
                ClaimTopicType.ECONOMY
            listOf("삼성전자", "반도체", "ai", "인공지능", "기술", "스마트폰").any(lower::contains) ->
                ClaimTopicType.TECHNOLOGY
            else -> ClaimTopicType.UNKNOWN
        }
    }

    private fun extractNumericValue(claim: String): String? {
        val match = Regex("(\\d{1,3}(?:,\\d{3})+|\\d+)(\\s*만)?\\s*원?")
            .find(claim) ?: return null
        val number = match.groupValues[1].replace(",", "").toLongOrNull() ?: return null
        val multiplier = if (match.groupValues[2].isNotBlank()) 10_000L else 1L
        return (number * multiplier).toString()
    }

    private fun extractDateOrTimeHint(claim: String): String? =
        listOf(
            Regex("\\d{4}\\s*년\\s*\\d{1,2}\\s*월\\s*\\d{1,2}\\s*일?"),
            Regex("\\d{4}\\s*년"),
            Regex("\\d{1,2}\\s*월\\s*\\d{1,2}\\s*일?"),
            Regex("오늘|어제|그제|내일|지난\\s*주|이번\\s*주|최근|현재")
        )
            .firstNotNullOfOrNull { regex -> regex.find(claim)?.value?.replace(Regex("\\s+"), " ") }

    private fun extractEntities(
        claim: String,
        topicType: ClaimTopicType
    ): List<String> {
        val baseTokens = KeywordExtractor.extract(claim, limit = 10)
            .filterNot { token -> token in stopWords }
        val knownEntities = knownEntityKeywords.filter { entity -> claim.contains(entity) }
        val topicEntities = when (topicType) {
            ClaimTopicType.SPORTS -> listOf("한국", "대한민국", "체코", "월드컵").filter(claim::contains)
            ClaimTopicType.FINANCE,
            ClaimTopicType.TECHNOLOGY -> listOf("삼성전자", "주가", "코스피", "코스닥").filter(claim::contains)
            else -> emptyList()
        }

        return (knownEntities + topicEntities + baseTokens)
            .distinctBy { token -> token.lowercase(Locale.ROOT) }
            .take(8)
    }

    private fun ambiguityQuestions(
        claim: String,
        topicType: ClaimTopicType,
        dateOrTimeHint: String?
    ): List<String> =
        buildList {
            if (dateOrTimeHint == null && topicType in timeSensitiveTopics) {
                add("기준 날짜 또는 시점이 필요합니다.")
            }
            if (topicType == ClaimTopicType.SPORTS && claim.contains("월드컵") && dateOrTimeHint == null) {
                add("어느 월드컵 또는 경기인지 명확하지 않습니다.")
            }
            if (topicType == ClaimTopicType.FINANCE && dateOrTimeHint == null) {
                add("주가 또는 금융 수치의 기준 날짜와 시장이 필요합니다.")
            }
        }

    private fun buildSearchQueries(
        mainClaim: String,
        topicType: ClaimTopicType,
        entities: List<String>,
        numericValue: String?
    ): List<String> =
        buildList {
            add(mainClaim)
            when (topicType) {
                ClaimTopicType.FINANCE -> {
                    val company = entities.firstOrNull { entity -> entity.contains("삼성전자") }
                        ?: entities.firstOrNull()
                    if (company != null) {
                        add("$company 현재 주가")
                        numericValue?.let { add("$company 주가 ${it}원") }
                    }
                }
                ClaimTopicType.SPORTS -> {
                    val containsKorea = entities.any { it == "한국" || it == "대한민국" }
                    val containsCzech = entities.any { it == "체코" }
                    if (containsKorea && containsCzech) {
                        add("한국 체코 월드컵 패배")
                        add("대한민국 체코 월드컵 경기 결과")
                    }
                }
                else -> {
                    if (entities.isNotEmpty()) {
                        add(entities.joinToString(" "))
                    }
                }
            }
        }
            .map { query -> query.replace(Regex("\\s+"), " ").trim() }
            .filter { query -> query.isNotBlank() }
            .distinctBy { query -> query.lowercase(Locale.ROOT) }
            .take(MAX_SEARCH_QUERIES)

    private val timeSensitiveTopics = setOf(
        ClaimTopicType.SPORTS,
        ClaimTopicType.FINANCE,
        ClaimTopicType.POLITICS,
        ClaimTopicType.SOCIAL,
        ClaimTopicType.ECONOMY
    )

    private val stopWords = setOf(
        "돌파",
        "패배",
        "승리",
        "발생",
        "주장",
        "현재"
    )

    private val knownEntityKeywords = listOf(
        "삼성전자",
        "한국",
        "대한민국",
        "체코",
        "월드컵"
    )

    private const val MAX_SEARCH_QUERIES = 5
    private const val MAX_EVIDENCE_KEYWORDS = 8
}

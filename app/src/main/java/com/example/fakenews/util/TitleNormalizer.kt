package com.example.fakenews.util

import java.util.Locale

object TitleNormalizer {
    private val noiseWords = listOf(
        "속보",
        "단독",
        "종합",
        "연합뉴스",
        "mbc",
        "sbs",
        "kbs",
        "ytn"
    )
    private val bracketNoiseRegex = Regex("""[\[\(【].{0,8}?(속보|단독|종합).{0,8}?[\]\)】]""")
    private val punctuationRegex = Regex("""[\p{Punct}“”‘’·…]+""")
    private val whitespaceRegex = Regex("""\s+""")

    fun normalize(rawTitle: String?): String {
        var value = rawTitle
            ?.trim()
            ?.lowercase(Locale.ROOT)
            .orEmpty()

        if (value.isBlank()) return ""

        value = value.replace(bracketNoiseRegex, " ")
        noiseWords.forEach { word ->
            value = value
                .replace(Regex("""(^|\s)$word\s*[:：-]?"""), " ")
                .replace(Regex("""\s*[-:：]?\s*$word($|\s)"""), " ")
        }

        return value
            .replace(punctuationRegex, " ")
            .replace(whitespaceRegex, " ")
            .trim()
    }
}

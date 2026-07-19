package com.example.fakenews.util

import org.junit.Assert.assertEquals
import org.junit.Test

class JsonCleanerTest {
    @Test
    fun removesJsonCodeBlockAndKeepsJsonObject() {
        val raw = """
            ```json
            {
              "finalSummary": "요약"
            }
            ```
        """.trimIndent()

        assertEquals(
            """
            {
              "finalSummary": "요약"
            }
            """.trimIndent(),
            JsonCleaner.clean(raw)
        )
    }

    @Test
    fun extractsJsonObjectFromMixedText() {
        val raw = "분석 결과입니다. {\"finalSummary\":\"요약\"} 감사합니다."

        assertEquals("{\"finalSummary\":\"요약\"}", JsonCleaner.clean(raw))
    }
}

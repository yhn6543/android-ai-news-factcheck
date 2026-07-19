package com.example.fakenews.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class NewsSourceTypeTest {
    @Test
    fun includesRequiredSourceTypes() {
        assertEquals(
            setOf(
                NewsSourceType.RSS,
                NewsSourceType.PUBLIC_API,
                NewsSourceType.SEARCH_RSS,
                NewsSourceType.HTML_CRAWLING,
                NewsSourceType.NOT_FOUND,
                NewsSourceType.MOCK
            ),
            NewsSourceType.entries.toSet()
        )
    }
}

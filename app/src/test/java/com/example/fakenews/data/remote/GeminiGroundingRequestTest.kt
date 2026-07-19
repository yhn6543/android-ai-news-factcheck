package com.example.fakenews.data.remote

import com.example.fakenews.data.remote.dto.GeminiContentDto
import com.example.fakenews.data.remote.dto.GeminiPartDto
import com.example.fakenews.data.remote.dto.GeminiRequestDto
import com.example.fakenews.data.remote.dto.googleSearchTools
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiGroundingRequestTest {
    private val json = Json {
        encodeDefaults = false
    }

    @Test
    fun requestSerializesGoogleSearchToolWithSnakeCaseName() {
        val request = GeminiRequestDto(
            contents = listOf(
                GeminiContentDto(
                    parts = listOf(GeminiPartDto(text = "prompt"))
                )
            ),
            tools = googleSearchTools()
        )

        val serialized = json.encodeToString(request)

        assertTrue(serialized.contains("\"tools\""))
        assertTrue(serialized.contains("\"google_search\":{}"))
        assertFalse(serialized.contains("googleSearch"))
        assertFalse(serialized.contains("google_search_retrieval"))
    }
}

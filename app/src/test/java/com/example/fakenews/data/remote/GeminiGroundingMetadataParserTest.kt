package com.example.fakenews.data.remote

import com.example.fakenews.data.remote.dto.GeminiResponseDto
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class GeminiGroundingMetadataParserTest {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    @Test
    fun parsesGroundingMetadataFields() {
        val response = json.decodeFromString<GeminiResponseDto>(
            """
            {
              "candidates": [
                {
                  "content": {
                    "parts": [
                      { "text": "{\"verdict\":\"TRUE\"}" }
                    ]
                  },
                  "groundingMetadata": {
                    "webSearchQueries": ["삼성전자 주가", "Samsung Electronics stock"],
                    "groundingChunks": [
                      {
                        "web": {
                          "title": "기사 제목",
                          "uri": "https://example.com/article"
                        }
                      },
                      {
                        "web": {
                          "title": "공식 자료",
                          "uri": "https://example.com/source"
                        }
                      }
                    ],
                    "groundingSupports": [
                      {
                        "groundingChunkIndices": [0, 1],
                        "confidenceScores": [0.8, 0.7]
                      }
                    ]
                  }
                }
              ]
            }
            """.trimIndent()
        )

        val metadata = response.firstGroundingMetadataOrEmpty()

        assertEquals(listOf("삼성전자 주가", "Samsung Electronics stock"), metadata.webSearchQueries)
        assertEquals(2, metadata.actualSourceCount)
        assertEquals(2, metadata.rawSourceCount)
        assertEquals(2, metadata.uniqueSourceCount)
        assertEquals(2, metadata.uniqueUriSourceCount)
        assertEquals("기사 제목", metadata.groundingChunks[0].web?.title)
        assertEquals("https://example.com/article", metadata.groundingChunks[0].web?.uri)
        assertEquals(listOf(0, 1), metadata.groundingSupports.single().groundingChunkIndices)
        assertEquals(listOf(0.8, 0.7), metadata.groundingSupports.single().confidenceScores)
        assertEquals(2, metadata.sources.size)
    }

    @Test
    fun deDuplicatesSourcesByNormalizedUri() {
        val response = json.decodeFromString<GeminiResponseDto>(
            responseJson(
                chunks = """
                [
                  { "web": { "title": "A", "uri": "https://example.com/news/" } },
                  { "web": { "title": "A duplicate", "uri": "https://example.com/news" } }
                ]
                """.trimIndent()
            )
        )

        val metadata = response.firstGroundingMetadataOrEmpty()

        assertEquals(2, metadata.rawSourceCount)
        assertEquals(1, metadata.uniqueSourceCount)
        assertEquals(1, metadata.uniqueUriSourceCount)
        assertEquals(1, metadata.sources.size)
    }

    @Test
    fun titleOnlySourcesUseIndexFallbackKeyAndDoNotCrash() {
        val response = json.decodeFromString<GeminiResponseDto>(
            responseJson(
                chunks = """
                [
                  { "web": { "title": "같은 제목", "uri": "" } },
                  { "web": { "title": "같은 제목", "uri": "" } }
                ]
                """.trimIndent()
            )
        )

        val metadata = response.firstGroundingMetadataOrEmpty()

        assertEquals(2, metadata.rawSourceCount)
        assertEquals(2, metadata.uniqueSourceCount)
        assertEquals(0, metadata.uniqueUriSourceCount)
        assertEquals(2, metadata.sources.size)
        assertEquals("", metadata.sources.first().uri)
    }

    private fun responseJson(chunks: String): String =
        """
        {
          "candidates": [
            {
              "content": {
                "parts": [
                  { "text": "{\"verdict\":\"TRUE\"}" }
                ]
              },
              "groundingMetadata": {
                "webSearchQueries": ["검색어"],
                "groundingChunks": $chunks,
                "groundingSupports": [
                  {
                    "groundingChunkIndices": [0],
                    "confidenceScores": [0.8]
                  }
                ]
              }
            }
          ]
        }
        """.trimIndent()
}

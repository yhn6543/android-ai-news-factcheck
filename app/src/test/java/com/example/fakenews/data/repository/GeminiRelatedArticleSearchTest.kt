package com.example.fakenews.data.repository

import com.example.fakenews.data.remote.GeminiApi
import com.example.fakenews.data.remote.dto.GeminiCandidateDto
import com.example.fakenews.data.remote.dto.GeminiRequestDto
import com.example.fakenews.data.remote.dto.GeminiResponseContentDto
import com.example.fakenews.data.remote.dto.GeminiResponseDto
import com.example.fakenews.data.remote.dto.GeminiPartTextDto
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class GeminiRelatedArticleSearchTest {
    @Test
    fun factCheckCallDoesNotInjectInternalEvidenceArticlesIntoPrompt() = runTest {
        val geminiApi = RecordingGeminiApi()
        val repository = GeminiFactCheckRepository(
            apiKey = "test-key",
            api = geminiApi
        )

        val result = repository.analyze("삼성전자 주가 40만원 돌파")

        assertFalse(geminiApi.lastPrompt.contains("관련 근거 기사 목록"))
        assertFalse(geminiApi.lastPrompt.contains("추출된 핵심 주장"))
        assertTrue(result.relatedArticles.isEmpty())
    }

    private class RecordingGeminiApi : GeminiApi {
        var lastPrompt: String = ""

        override suspend fun generateContent(
            model: String,
            apiKey: String,
            request: GeminiRequestDto
        ): Response<GeminiResponseDto> {
            lastPrompt = request.contents.first().parts.first().text
            return Response.success(
                GeminiResponseDto(
                    candidates = listOf(
                        GeminiCandidateDto(
                            content = GeminiResponseContentDto(
                                parts = listOf(
                                    GeminiPartTextDto(
                                        text = """
                                        {
                                          "verdict": "NEEDS_MORE_CONTEXT",
                                          "confidenceScore": 35,
                                          "finalSummary": "기준 시점이 필요합니다.",
                                          "reason": "주가 주장은 시점이 필요합니다.",
                                          "evidenceSummary": "",
                                          "missingContext": ["어느 날짜 기준인가요?"],
                                          "recommendedChecks": ["거래소 공시 확인"],
                                          "searchQueriesUsed": [],
                                          "claimCategory": "FINANCE",
                                          "timeSensitivity": "HIGH",
                                          "needsTimeContext": true
                                        }
                                        """.trimIndent()
                                    )
                                )
                            )
                        )
                    )
                )
            )
        }
    }
}

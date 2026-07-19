package com.example.fakenews.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GeminiRequestDto(
    val contents: List<GeminiContentDto>,
    val tools: List<GeminiToolDto>,
    @SerialName("generationConfig")
    val generationConfig: GeminiGenerationConfigDto = GeminiGenerationConfigDto()
)

@Serializable
data class GeminiContentDto(
    val parts: List<GeminiPartDto>
)

@Serializable
data class GeminiPartDto(
    val text: String
)

@Serializable
data class GeminiGenerationConfigDto(
    val temperature: Double = 0.2
)

@Serializable
data class GeminiToolDto(
    @SerialName("google_search")
    val googleSearch: GeminiGoogleSearchDto
) {
    companion object {
        fun googleSearch(): GeminiToolDto =
            GeminiToolDto(googleSearch = GeminiGoogleSearchDto())
    }
}

@Serializable
class GeminiGoogleSearchDto

fun googleSearchTools(): List<GeminiToolDto> =
    listOf(GeminiToolDto.googleSearch())

package com.example.fakenews.data.remote.dto

import com.example.fakenews.data.model.GeminiGroundingMetadata
import com.example.fakenews.data.model.GroundingChunk
import com.example.fakenews.data.model.GroundingSupport
import com.example.fakenews.data.model.GroundingWebSource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GeminiResponseDto(
    val candidates: List<GeminiCandidateDto> = emptyList()
) {
    fun firstTextOrNull(): String? =
        candidates
            .firstOrNull()
            ?.content
            ?.parts
            ?.firstOrNull { part -> !part.text.isNullOrBlank() }
            ?.text

    fun firstGroundingMetadataOrEmpty(): GeminiGroundingMetadata =
        candidates
            .firstOrNull()
            ?.groundingMetadata
            ?.toDomain()
            ?: GeminiGroundingMetadata.Empty
}

@Serializable
data class GeminiCandidateDto(
    val content: GeminiResponseContentDto? = null,
    @SerialName("groundingMetadata")
    val groundingMetadata: GeminiGroundingMetadataDto? = null
)

@Serializable
data class GeminiResponseContentDto(
    val parts: List<GeminiPartTextDto> = emptyList()
)

@Serializable
data class GeminiPartTextDto(
    val text: String? = null
)

@Serializable
data class GeminiGroundingMetadataDto(
    @SerialName("webSearchQueries")
    val webSearchQueries: List<String> = emptyList(),
    @SerialName("groundingChunks")
    val groundingChunks: List<GeminiGroundingChunkDto> = emptyList(),
    @SerialName("groundingSupports")
    val groundingSupports: List<GeminiGroundingSupportDto> = emptyList()
) {
    fun toDomain(): GeminiGroundingMetadata =
        GeminiGroundingMetadata(
            webSearchQueries = webSearchQueries,
            groundingChunks = groundingChunks.map { chunk ->
                GroundingChunk(web = chunk.web?.toDomain())
            },
            groundingSupports = groundingSupports.map { support ->
                GroundingSupport(
                    groundingChunkIndices = support.groundingChunkIndices,
                    confidenceScores = support.confidenceScores
                )
            }
        )
}

@Serializable
data class GeminiGroundingChunkDto(
    val web: GeminiGroundingWebSourceDto? = null
)

@Serializable
data class GeminiGroundingWebSourceDto(
    val title: String = "",
    val uri: String = ""
) {
    fun toDomain(): GroundingWebSource =
        GroundingWebSource(
            title = title,
            uri = uri
        )
}

@Serializable
data class GeminiGroundingSupportDto(
    @SerialName("groundingChunkIndices")
    val groundingChunkIndices: List<Int> = emptyList(),
    @SerialName("confidenceScores")
    val confidenceScores: List<Double> = emptyList()
)

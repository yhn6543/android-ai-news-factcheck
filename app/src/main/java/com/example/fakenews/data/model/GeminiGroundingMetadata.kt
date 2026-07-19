package com.example.fakenews.data.model

import java.net.URI
import java.util.Locale

data class GeminiGroundingMetadata(
    val webSearchQueries: List<String> = emptyList(),
    val groundingChunks: List<GroundingChunk> = emptyList(),
    val groundingSupports: List<GroundingSupport> = emptyList()
) {
    val rawSourceCount: Int
        get() = groundingChunks.size

    val actualSourceCount: Int
        get() = rawSourceCount

    val uniqueSourceCount: Int
        get() = uniqueSources.size

    val uniqueUriSourceCount: Int
        get() = groundingChunks
            .mapNotNull { chunk -> chunk.web?.uri?.normalizedUriKey()?.takeIf(String::isNotBlank) }
            .distinct()
            .size

    val sources: List<GroundedSource>
        get() = uniqueSources.map { (_, source) -> source }

    private val uniqueSources: List<Pair<String, GroundedSource>>
        get() = groundingChunks.mapIndexedNotNull { index, chunk ->
            val web = chunk.web ?: return@mapIndexedNotNull null
            val title = web.title.trim()
            val uri = web.uri.trim()
            if (title.isBlank() && uri.isBlank()) return@mapIndexedNotNull null
            val key = if (uri.isNotBlank()) {
                uri.normalizedUriKey()
            } else {
                "title_only:$index:${title.normalizedTitleKey()}"
            }
            key to GroundedSource(title = title, uri = uri)
        }.distinctBy { (key, _) -> key }

    companion object {
        val Empty = GeminiGroundingMetadata()
    }
}

data class GroundingChunk(
    val web: GroundingWebSource? = null
)

data class GroundingWebSource(
    val title: String = "",
    val uri: String = ""
)

data class GroundingSupport(
    val groundingChunkIndices: List<Int> = emptyList(),
    val confidenceScores: List<Double> = emptyList()
)

data class GroundedSource(
    val title: String,
    val uri: String
)

private fun String.normalizedUriKey(): String =
    runCatching {
        val parsed = URI(trim())
        val scheme = parsed.scheme?.lowercase(Locale.ROOT).orEmpty()
        val host = parsed.host?.lowercase(Locale.ROOT).orEmpty()
        val path = parsed.path.orEmpty().trimEnd('/')
        val query = parsed.query?.let { "?$it" }.orEmpty()
        if (host.isBlank()) {
            trim().lowercase(Locale.ROOT).trimEnd('/')
        } else {
            "$scheme://$host$path$query"
        }
    }.getOrElse {
        trim().lowercase(Locale.ROOT).trimEnd('/')
    }

private fun String.normalizedTitleKey(): String =
    trim()
        .lowercase(Locale.ROOT)
        .replace(Regex("\\s+"), " ")

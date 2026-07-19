package com.example.fakenews.data.remote

import com.example.fakenews.data.remote.dto.GeminiRequestDto
import com.example.fakenews.data.remote.dto.GeminiResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface GeminiApi {
    @POST("v1beta/models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Header("x-goog-api-key") apiKey: String,
        @Body request: GeminiRequestDto
    ): Response<GeminiResponseDto>
}

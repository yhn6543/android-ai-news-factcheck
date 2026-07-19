package com.example.fakenews.data.repository

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Test

class GeminiErrorMapperTest {
    @Test
    fun http503MapsToServerOverloadedMessage() {
        val error = GeminiErrorMapper.fromHttpStatus(503)

        assertEquals("Gemini 서버가 일시적으로 혼잡합니다. 잠시 후 다시 시도해 주세요.", error.message)
    }

    @Test
    fun http429MapsToRateLimitMessage() {
        val error = GeminiErrorMapper.fromHttpStatus(429)

        assertEquals("요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.", error.message)
    }

    @Test
    fun http401And403MapToApiKeyOrPermissionMessage() {
        assertEquals(
            "Gemini API Key 또는 권한을 확인해 주세요.",
            GeminiErrorMapper.fromHttpStatus(401).message
        )
        assertEquals(
            "Gemini API Key 또는 권한을 확인해 주세요.",
            GeminiErrorMapper.fromHttpStatus(403).message
        )
    }

    @Test
    fun timeoutMapsToTimeoutMessage() {
        val error = GeminiErrorMapper.fromThrowable(SocketTimeoutException("timeout"))

        assertEquals("응답 시간이 초과되었습니다. 네트워크 상태를 확인해 주세요.", error.message)
    }

    @Test
    fun unknownHostAndConnectExceptionMapToInternetConnectionMessage() {
        assertEquals(
            "인터넷 연결을 확인해 주세요.",
            GeminiErrorMapper.fromThrowable(UnknownHostException("offline")).message
        )
        assertEquals(
            "인터넷 연결을 확인해 주세요.",
            GeminiErrorMapper.fromThrowable(ConnectException("refused")).message
        )
    }
}

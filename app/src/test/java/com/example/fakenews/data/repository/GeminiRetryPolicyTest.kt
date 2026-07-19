package com.example.fakenews.data.repository

import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiRetryPolicyTest {
    @Test
    fun http503IsRetryable() {
        assertTrue(GeminiRetryPolicy.shouldRetry(FactCheckException.ServerOverloaded))
    }

    @Test
    fun timeoutIsRetryable() {
        assertTrue(GeminiRetryPolicy.shouldRetry(SocketTimeoutException("timeout")))
    }

    @Test
    fun http401And403AreNotRetryable() {
        assertFalse(GeminiRetryPolicy.shouldRetry(GeminiErrorMapper.fromHttpStatus(401)))
        assertFalse(GeminiRetryPolicy.shouldRetry(GeminiErrorMapper.fromHttpStatus(403)))
    }

    @Test
    fun jsonParseFailureIsNotRetryable() {
        assertFalse(GeminiRetryPolicy.shouldRetry(FactCheckException.ParseFailure))
    }

    @Test
    fun networkFailuresOtherThanTimeoutAreNotRetried() {
        assertFalse(GeminiRetryPolicy.shouldRetry(UnknownHostException("offline")))
        assertFalse(GeminiRetryPolicy.shouldRetry(FactCheckException.NetworkFailure))
    }
}

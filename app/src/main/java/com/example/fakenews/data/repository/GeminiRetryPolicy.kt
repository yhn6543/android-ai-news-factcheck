package com.example.fakenews.data.repository

object GeminiRetryPolicy {
    const val MAX_RETRY_COUNT = 3

    fun shouldRetry(error: Throwable): Boolean =
        when (GeminiErrorMapper.fromThrowable(error)) {
            FactCheckException.ServerOverloaded,
            FactCheckException.Timeout -> true
            else -> false
        }

    fun shouldFallbackToNextModel(error: Throwable): Boolean =
        GeminiErrorMapper.fromThrowable(error) == FactCheckException.ServerOverloaded

    fun backoffDelayMillis(retryIndex: Int): Long =
        1_000L * (1L shl retryIndex.coerceAtLeast(0).coerceAtMost(MAX_RETRY_COUNT - 1))
}

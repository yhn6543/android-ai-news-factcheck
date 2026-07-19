package com.example.fakenews.data.repository

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.serialization.SerializationException

object GeminiErrorMapper {
    fun fromHttpStatus(
        statusCode: Int,
        rawErrorBody: String? = null
    ): FactCheckException =
        when (statusCode) {
            503 -> FactCheckException.ServerOverloaded
            429 -> FactCheckException.RateLimited
            401, 403 -> FactCheckException.ApiKeyOrPermission
            else -> FactCheckException.HttpError(statusCode, rawErrorBody)
        }

    fun fromThrowable(error: Throwable): FactCheckException =
        when (error) {
            is FactCheckException.HttpError -> fromHttpStatus(error.code, error.rawErrorBody)
            is FactCheckException -> error
            is SocketTimeoutException -> FactCheckException.Timeout
            is UnknownHostException,
            is ConnectException -> FactCheckException.NoInternet
            is IOException -> FactCheckException.NetworkFailure
            is SerializationException,
            is IllegalArgumentException -> FactCheckException.ParseFailure
            else -> FactCheckException.Unknown
        }

    fun errorType(error: Throwable): String =
        when (fromThrowable(error)) {
            FactCheckException.MissingApiKey -> "MISSING_API_KEY"
            FactCheckException.ServerOverloaded -> "SERVER_OVERLOADED"
            FactCheckException.RateLimited -> "RATE_LIMITED"
            FactCheckException.ApiKeyOrPermission -> "API_KEY_OR_PERMISSION"
            FactCheckException.Timeout -> "TIMEOUT"
            FactCheckException.NoInternet,
            FactCheckException.NetworkFailure -> "NO_INTERNET"
            is FactCheckException.HttpError -> "HTTP_ERROR"
            FactCheckException.EmptyBody,
            FactCheckException.MissingText,
            FactCheckException.ParseFailure -> "PARSE_FAILURE"
            FactCheckException.Unknown -> "UNKNOWN"
        }
}

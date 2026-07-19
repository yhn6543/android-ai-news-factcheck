package com.example.fakenews.data.repository

sealed class FactCheckException(message: String) : Exception(message) {
    data object MissingApiKey : FactCheckException("Gemini API Key가 설정되지 않았습니다.")
    data object ServerOverloaded : FactCheckException("Gemini 서버가 일시적으로 혼잡합니다. 잠시 후 다시 시도해 주세요.")
    data object RateLimited : FactCheckException("요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.")
    data object ApiKeyOrPermission : FactCheckException("Gemini API Key 또는 권한을 확인해 주세요.")
    data object Timeout : FactCheckException("응답 시간이 초과되었습니다. 네트워크 상태를 확인해 주세요.")
    data object NoInternet : FactCheckException("인터넷 연결을 확인해 주세요.")
    data object NetworkFailure : FactCheckException("인터넷 연결을 확인해 주세요.")
    data class HttpError(
        val code: Int,
        val rawErrorBody: String? = null
    ) : FactCheckException("분석 중 오류가 발생했습니다.")
    data object EmptyBody : FactCheckException("AI 응답을 해석하지 못했습니다. 다시 시도해 주세요.")
    data object MissingText : FactCheckException("AI 응답을 해석하지 못했습니다. 다시 시도해 주세요.")
    data object ParseFailure : FactCheckException("AI 응답을 해석하지 못했습니다. 다시 시도해 주세요.")
    data object Unknown : FactCheckException("분석 중 오류가 발생했습니다.")
}

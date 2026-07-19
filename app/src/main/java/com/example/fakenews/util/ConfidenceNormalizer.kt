package com.example.fakenews.util

import kotlin.math.roundToInt
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

data class ConfidenceParseResult(
    val rawValue: String,
    val rawType: String,
    val normalizedValue: Int,
    val parseSuccess: Boolean,
    val parseReason: String,
    val ambiguousLowInteger: Boolean
)

object ConfidenceNormalizer {
    fun normalize(
        rawElement: JsonElement?,
        defaultValue: Int
    ): ConfidenceParseResult {
        if (rawElement == null || rawElement == JsonNull) {
            return ConfidenceParseResult(
                rawValue = "",
                rawType = "null",
                normalizedValue = defaultValue.coerceConfidence(),
                parseSuccess = false,
                parseReason = "missing_confidence",
                ambiguousLowInteger = false
            )
        }

        val primitive = runCatching { rawElement.jsonPrimitive }.getOrNull()
            ?: return parseFailure(rawElement.toString(), "non_primitive", defaultValue)
        val rawValue = primitive.contentOrNull.orEmpty().trim()
        val rawType = if (primitive.isString) "string" else "number"

        if (rawType == "number") {
            return normalizeNumber(
                rawValue = rawElement.toString().trim(),
                numericValue = primitive.doubleOrNull,
                defaultValue = defaultValue
            )
        }

        return normalizeString(
            rawValue = rawValue,
            defaultValue = defaultValue
        )
    }

    private fun normalizeNumber(
        rawValue: String,
        numericValue: Double?,
        defaultValue: Int
    ): ConfidenceParseResult {
        val value = numericValue ?: return parseFailure(rawValue, "number", defaultValue)
        val integerLiteral = integerLiteralPattern.matches(rawValue)
        val integerValue = rawValue.toIntOrNull()
        if (integerLiteral && integerValue != null && integerValue in LOW_INTEGER_RANGE) {
            return ConfidenceParseResult(
                rawValue = rawValue,
                rawType = "number",
                normalizedValue = integerValue.coerceConfidence(),
                parseSuccess = true,
                parseReason = "ambiguous_low_integer",
                ambiguousLowInteger = true
            )
        }

        val normalized = if (value in 0.0..1.0) {
            (value * 100).roundToInt()
        } else {
            value.roundToInt()
        }

        return ConfidenceParseResult(
            rawValue = rawValue,
            rawType = "number",
            normalizedValue = normalized.coerceConfidence(),
            parseSuccess = true,
            parseReason = if (value in 0.0..1.0) "fraction_to_percent" else "number_0_to_100",
            ambiguousLowInteger = false
        )
    }

    private fun normalizeString(
        rawValue: String,
        defaultValue: Int
    ): ConfidenceParseResult {
        if (rawValue.isBlank()) return parseFailure(rawValue, "string", defaultValue)

        val percent = percentPattern.find(rawValue)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
        if (percent != null) {
            return success(rawValue, (percent).roundToInt(), "percent_string")
        }

        val slashTen = slashTenPattern.find(rawValue)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
        if (slashTen != null) {
            return success(rawValue, (slashTen * 10).roundToInt(), "slash_10")
        }

        val koreanTen = koreanTenPointPattern.find(rawValue)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
        if (koreanTen != null) {
            return success(rawValue, (koreanTen * 10).roundToInt(), "korean_10_point")
        }

        val englishTen = englishTenPointPattern.find(rawValue)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
        if (englishTen != null) {
            return success(rawValue, (englishTen * 10).roundToInt(), "english_10_point")
        }

        val numericValue = rawValue.toDoubleOrNull()
            ?: return parseFailure(rawValue, "string", defaultValue)
        val normalized = if (numericValue in 0.0..1.0) {
            (numericValue * 100).roundToInt()
        } else {
            numericValue.roundToInt()
        }

        return success(
            rawValue = rawValue,
            normalizedValue = normalized,
            parseReason = if (numericValue in 0.0..1.0) "numeric_string_fraction_to_percent" else "numeric_string"
        )
    }

    private fun success(
        rawValue: String,
        normalizedValue: Int,
        parseReason: String
    ): ConfidenceParseResult =
        ConfidenceParseResult(
            rawValue = rawValue,
            rawType = "string",
            normalizedValue = normalizedValue.coerceConfidence(),
            parseSuccess = true,
            parseReason = parseReason,
            ambiguousLowInteger = false
        )

    private fun parseFailure(
        rawValue: String,
        rawType: String,
        defaultValue: Int
    ): ConfidenceParseResult =
        ConfidenceParseResult(
            rawValue = rawValue,
            rawType = rawType,
            normalizedValue = defaultValue.coerceConfidence(),
            parseSuccess = false,
            parseReason = "parse_failed",
            ambiguousLowInteger = false
        )

    private fun Int.coerceConfidence(): Int = coerceIn(0, 100)

    private val LOW_INTEGER_RANGE = 1..10
    private val integerLiteralPattern = Regex("[-+]?\\d+")
    private val percentPattern = Regex("([-+]?\\d+(?:\\.\\d+)?)\\s*%")
    private val slashTenPattern = Regex("([-+]?\\d+(?:\\.\\d+)?)\\s*/\\s*10")
    private val koreanTenPointPattern = Regex("10\\s*점\\s*만점\\s*에\\s*([-+]?\\d+(?:\\.\\d+)?)\\s*점?")
    private val englishTenPointPattern = Regex("([-+]?\\d+(?:\\.\\d+)?)\\s*out\\s*of\\s*10", RegexOption.IGNORE_CASE)
}

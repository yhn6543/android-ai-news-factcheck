package com.example.fakenews.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfidenceNormalizerTest {
    @Test
    fun fractionNumberConvertsToPercent() {
        val result = ConfidenceNormalizer.normalize(Json.parseToJsonElement("0.87"), defaultValue = 50)

        assertEquals(87, result.normalizedValue)
        assertFalse(result.ambiguousLowInteger)
    }

    @Test
    fun fractionStringConvertsToPercent() {
        val result = ConfidenceNormalizer.normalize(JsonPrimitive("0.87"), defaultValue = 50)

        assertEquals(87, result.normalizedValue)
    }

    @Test
    fun percentStringConvertsToInteger() {
        val result = ConfidenceNormalizer.normalize(JsonPrimitive("87%"), defaultValue = 50)

        assertEquals(87, result.normalizedValue)
    }

    @Test
    fun slashTenStringConvertsToPercent() {
        val result = ConfidenceNormalizer.normalize(JsonPrimitive("8/10"), defaultValue = 50)

        assertEquals(80, result.normalizedValue)
    }

    @Test
    fun koreanTenPointStringConvertsToPercent() {
        val result = ConfidenceNormalizer.normalize(JsonPrimitive("10점 만점에 8점"), defaultValue = 50)

        assertEquals(80, result.normalizedValue)
    }

    @Test
    fun integerPercentScaleValueIsKept() {
        val result = ConfidenceNormalizer.normalize(Json.parseToJsonElement("87"), defaultValue = 50)

        assertEquals(87, result.normalizedValue)
        assertEquals("number_0_to_100", result.parseReason)
    }

    @Test
    fun valueAboveHundredIsClamped() {
        val result = ConfidenceNormalizer.normalize(Json.parseToJsonElement("870"), defaultValue = 50)

        assertEquals(100, result.normalizedValue)
    }

    @Test
    fun nullUsesDefaultAndMarksParseFailure() {
        val result = ConfidenceNormalizer.normalize(JsonNull, defaultValue = 45)

        assertEquals(45, result.normalizedValue)
        assertFalse(result.parseSuccess)
    }

    @Test
    fun jsonNumberEightIsNotConvertedWithoutContext() {
        val result = ConfidenceNormalizer.normalize(Json.parseToJsonElement("8"), defaultValue = 50)

        assertEquals(8, result.normalizedValue)
        assertTrue(result.ambiguousLowInteger)
        assertEquals("ambiguous_low_integer", result.parseReason)
    }

    @Test
    fun alreadyNormalizedValueIsNotNormalizedAgain() {
        val result = ConfidenceNormalizer.normalize(Json.parseToJsonElement("87"), defaultValue = 50)

        assertEquals(87, result.normalizedValue)
    }
}

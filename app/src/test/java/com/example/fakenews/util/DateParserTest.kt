package com.example.fakenews.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DateParserTest {
    @Test
    fun parsesRfc1123WithTimezone() {
        assertEquals(
            Instant.parse("2025-06-10T03:34:56Z").toEpochMilli(),
            DateParser.parseEpochMillis("Tue, 10 Jun 2025 12:34:56 +0900")
        )
    }

    @Test
    fun parsesIso8601OffsetDateTime() {
        assertEquals(
            Instant.parse("2025-06-10T03:34:56Z").toEpochMilli(),
            DateParser.parseEpochMillis("2025-06-10T12:34:56+09:00")
        )
    }

    @Test
    fun parsesKoreanAmPmWithSeoulTimezone() {
        val expected = LocalDateTime.of(2025, 6, 10, 15, 4)
            .atZone(ZoneId.of("Asia/Seoul"))
            .toInstant()
            .toEpochMilli()

        assertEquals(
            expected,
            DateParser.parseEpochMillis("2025-06-10 오후 3:04")
        )
    }

    @Test
    fun returnsNullWhenParsingFails() {
        assertNull(DateParser.parseEpochMillis("not a date"))
    }
}

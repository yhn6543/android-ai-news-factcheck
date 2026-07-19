package com.example.fakenews.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.TemporalAccessor
import java.util.Locale

object DateParser {
    private val seoulZone: ZoneId = ZoneId.of("Asia/Seoul")

    private val formatters: List<DateTimeFormatter> = listOf(
        DateTimeFormatter.RFC_1123_DATE_TIME,
        DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("EEE, dd MMM yyyy HH:mm:ss Z")
            .toFormatter(Locale.ENGLISH),
        DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("EEE, dd MMM yy HH:mm:ss Z")
            .toFormatter(Locale.ENGLISH),
        DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("EEE, dd MMM yyyy HH:mm Z")
            .toFormatter(Locale.ENGLISH),
        DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("EEE, dd MMM yy HH:mm Z")
            .toFormatter(Locale.ENGLISH),
        DateTimeFormatter.ISO_OFFSET_DATE_TIME,
        DateTimeFormatter.ISO_ZONED_DATE_TIME,
        DateTimeFormatter.ISO_LOCAL_DATE_TIME,
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
        DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm"),
        DateTimeFormatter.ofPattern("yyyy.MM.dd. HH:mm"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd a h:mm", Locale.KOREAN),
        DateTimeFormatter.ofPattern("yyyy.MM.dd a h:mm", Locale.KOREAN),
        DateTimeFormatter.ofPattern("yyyy.MM.dd. a h:mm", Locale.KOREAN),
        DateTimeFormatter.ofPattern("yyyy년 M월 d일 a h:mm", Locale.KOREAN),
        DateTimeFormatter.ofPattern("yyyy년 M월 d일 HH:mm", Locale.KOREAN)
    )

    fun parseEpochMillis(rawDate: String?): Long? {
        val value = rawDate
            ?.trim()
            ?.replace(Regex("\\s+"), " ")
            ?.removePrefix("입력 ")
            ?.removePrefix("등록 ")
            ?.removePrefix("기사입력 ")
            ?.removePrefix("Published ")
            ?.trim()
            .orEmpty()
        if (value.isEmpty()) return null

        formatters.forEach { formatter ->
            parseWithFormatter(value, formatter)?.let { return it }
        }

        return null
    }

    private fun parseWithFormatter(
        value: String,
        formatter: DateTimeFormatter
    ): Long? =
        runCatching {
            val parsed = formatter.parse(value)
            parsed.toEpochMillis()
        }.getOrNull()

    private fun TemporalAccessor.toEpochMillis(): Long =
        runCatching { Instant.from(this).toEpochMilli() }
            .getOrElse {
                runCatching { ZonedDateTime.from(this).toInstant().toEpochMilli() }
                    .getOrElse {
                        runCatching { OffsetDateTime.from(this).toInstant().toEpochMilli() }
                            .getOrElse {
                                LocalDateTime.from(this)
                                    .atZone(seoulZone)
                                    .toInstant()
                                    .toEpochMilli()
                            }
                    }
            }
}

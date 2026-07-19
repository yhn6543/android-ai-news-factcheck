package com.example.fakenews.util

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object TimeFormatter {
    private val publishedTimeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm")

    fun formatPublishedTime(epochMillis: Long?): String =
        epochMillis?.let {
            publishedTimeFormatter.format(
                Instant.ofEpochMilli(it).atZone(ZoneId.of("Asia/Seoul"))
            )
        } ?: "시간 정보 없음"
}

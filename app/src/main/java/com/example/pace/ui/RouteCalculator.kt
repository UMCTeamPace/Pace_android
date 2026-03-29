package com.example.pace.ui

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.math.ln

object RouteCalculator {
    private val seoulZone: ZoneId = ZoneId.of("Asia/Seoul")
    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun calculateWeight(duration: Int): Float {
        return if (duration > 0 && duration <= 180) {
            1.0f
        } else {
            1.0f + ln((duration - 180).toFloat() / 180f + 1f)
        }
    }

    fun convertUtcToMillis(serverDateStr: String?): Long {
        if (serverDateStr.isNullOrBlank()) return System.currentTimeMillis()

        val instant = parseToInstant(serverDateStr) ?: return System.currentTimeMillis()
        return instant.toEpochMilli()
    }

    fun convertUtcToKst(serverDateStr: String?): String {
        if (serverDateStr.isNullOrBlank()) return ""

        parseToInstant(serverDateStr)?.let { instant ->
            return instant.atZone(seoulZone).format(timeFormatter)
        }

        return parseLocalDateTime(serverDateStr)
            ?.atZone(ZoneOffset.UTC)
            ?.withZoneSameInstant(seoulZone)
            ?.format(timeFormatter)
            ?: serverDateStr.take(5)
    }

    private fun parseToInstant(raw: String): Instant? {
        return try {
            Instant.parse(raw)
        } catch (_: DateTimeParseException) {
            try {
                OffsetDateTime.parse(raw).toInstant()
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }

    private fun parseLocalDateTime(raw: String): LocalDateTime? {
        return try {
            LocalDateTime.parse(raw)
        } catch (_: DateTimeParseException) {
            null
        }
    }
}

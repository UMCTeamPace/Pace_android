package com.example.pace.data.repeat

import biweekly.util.DayOfWeek
import biweekly.util.Frequency
import biweekly.util.ICalDate
import biweekly.util.Recurrence
import com.example.pace.data.model.request.RepeatInfo
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object RepeatRuleHelper {
    fun buildRRule(info: RepeatInfo?): String? {
        if (info == null || info.repeatType.uppercase() == "NONE") return null

        return try {
            val rrule = StringBuilder("FREQ=${info.repeatType.uppercase()}")

            if (info.repeatInterval > 1) {
                rrule.append(";INTERVAL=${info.repeatInterval}")
            }

            if (!info.daysOfWeek.isNullOrEmpty()) {
                val days = info.daysOfWeek.split(",")
                    .mapNotNull { normalizeDayToken(it) }
                    .joinToString(",")
                if (days.isNotEmpty()) {
                    rrule.append(";BYDAY=$days")
                }
            }

            when (info.repeatType.uppercase()) {
                "MONTHLY" -> when (info.monthlyOption) {
                    "ORDINAL_DAY" -> {
                        val week = info.referenceWeekOfMonth
                        val day = info.referenceDayOfWeek
                        if (week != null && !day.isNullOrBlank()) {
                            rrule.append(";BYDAY=${week}${day.uppercase()}")
                        }
                    }
                    "SPECIFIC_DATE" -> {
                        val days = info.monthlyDays
                            ?.split(",")
                            ?.mapNotNull { it.trim().toIntOrNull() }
                            ?.sorted()
                            ?.joinToString(",")
                        if (!days.isNullOrBlank()) {
                            rrule.append(";BYMONTHDAY=$days")
                        }
                    }
                    "FIXED_DAY", null -> {
                        info.referenceDayOfMonth?.let { rrule.append(";BYMONTHDAY=$it") }
                    }
                }
                "YEARLY" -> when (info.yearlyOption) {
                    "ORDINAL_DAY" -> {
                        val month = info.referenceMonth
                        val week = info.referenceWeekOfMonth
                        val day = info.referenceDayOfWeek
                        if (month != null && week != null && !day.isNullOrBlank()) {
                            rrule.append(";BYMONTH=$month;BYDAY=${week}${day.uppercase()}")
                        }
                    }
                    "SPECIFIC_DATE" -> {
                        val months = info.yearlyMonths
                            ?.split(",")
                            ?.mapNotNull { it.trim().toIntOrNull() }
                            ?.sorted()
                            ?.joinToString(",")
                        val dayOfMonth = info.referenceDayOfMonth
                        if (!months.isNullOrBlank() && dayOfMonth != null) {
                            rrule.append(";BYMONTH=$months;BYMONTHDAY=$dayOfMonth")
                        }
                    }
                    "FIXED_DAY", null -> {
                        val month = info.referenceMonth
                        val dayOfMonth = info.referenceDayOfMonth
                        if (month != null && dayOfMonth != null) {
                            rrule.append(";BYMONTH=$month;BYMONTHDAY=$dayOfMonth")
                        }
                    }
                }
            }

            when (info.endType.uppercase()) {
                "COUNT" -> info.endCount?.let { rrule.append(";COUNT=$it") }
                "DATE" -> info.repeatEndDate?.takeIf { it.isNotBlank() }?.let {
                    rrule.append(";UNTIL=${it.replace("-", "")}T235959Z")
                }
            }

            rrule.toString()
        } catch (_: Exception) {
            null
        }
    }

    fun parseRecurrenceString(rruleStr: String): Recurrence? {
        return try {
            val params = parseParams(rruleStr)
            val freqStr = params["FREQ"] ?: return null
            val builder = Recurrence.Builder(Frequency.valueOf(freqStr))

            params["INTERVAL"]?.toIntOrNull()?.let { builder.interval(it) }
            params["COUNT"]?.toIntOrNull()?.let { builder.count(it) }

            params["UNTIL"]?.let { untilStr ->
                val format = if (untilStr.contains("T")) {
                    SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.getDefault()).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }
                } else {
                    SimpleDateFormat("yyyyMMdd", Locale.getDefault())
                }
                runCatching { format.parse(untilStr) }.getOrNull()?.let { date ->
                    builder.until(ICalDate(date, untilStr.contains("T")))
                }
            }

            params["BYDAY"]?.split(",")?.forEach { dayCode ->
                val normalized = dayCode.trim().uppercase()
                if (normalized.length < 2) return@forEach
                val dayOfWeek = parseDayOfWeek(normalized.takeLast(2)) ?: return@forEach
                val num = normalized.dropLast(2).toIntOrNull()
                builder.byDay(num, dayOfWeek)
            }

            params["BYMONTHDAY"]?.split(",")
                ?.mapNotNull { it.trim().toIntOrNull() }
                ?.forEach { builder.byMonthDay(it) }

            params["BYMONTH"]?.split(",")
                ?.mapNotNull { it.trim().toIntOrNull() }
                ?.forEach { builder.byMonth(it) }

            builder.build()
        } catch (_: Exception) {
            null
        }
    }

    fun parseRepeatInfo(rruleStr: String, endDate: String): RepeatInfo? {
        val recur = parseRecurrenceString(rruleStr) ?: return null
        val params = parseParams(rruleStr)
        val byDay = params["BYDAY"]
        val byMonthDay = params["BYMONTHDAY"]
        val byMonth = params["BYMONTH"]
        val ordinalPrefix = byDay?.takeLastIfHasOrdinal()?.dropLast(2)?.toIntOrNull()
        val ordinalDay = byDay?.takeLastIfHasOrdinal()?.takeLast(2)

        return runCatching {
            RepeatInfo(
                repeatType = recur.frequency.name,
                repeatInterval = recur.interval ?: 1,
                daysOfWeek = if (recur.frequency.name == "WEEKLY") byDay else null,
                monthlyOption = when {
                    recur.frequency.name != "MONTHLY" -> null
                    ordinalPrefix != null && !ordinalDay.isNullOrBlank() -> "ORDINAL_DAY"
                    !byMonthDay.isNullOrBlank() && byMonthDay.contains(",") -> "SPECIFIC_DATE"
                    else -> "FIXED_DAY"
                },
                monthlyDays = if (recur.frequency.name == "MONTHLY") byMonthDay else null,
                yearlyOption = when {
                    recur.frequency.name != "YEARLY" -> null
                    ordinalPrefix != null && !ordinalDay.isNullOrBlank() -> "ORDINAL_DAY"
                    !byMonth.isNullOrBlank() && byMonth.contains(",") -> "SPECIFIC_DATE"
                    else -> "FIXED_DAY"
                },
                yearlyMonths = if (recur.frequency.name == "YEARLY") byMonth else null,
                referenceDayOfMonth = byMonthDay?.split(",")?.firstOrNull()?.toIntOrNull(),
                referenceMonth = byMonth?.split(",")?.firstOrNull()?.toIntOrNull(),
                referenceDayOfWeek = ordinalDay,
                referenceWeekOfMonth = ordinalPrefix,
                endType = when {
                    recur.count != null -> "COUNT"
                    recur.until != null -> "DATE"
                    else -> "NEVER"
                },
                endCount = recur.count ?: 1,
                repeatEndDate = recur.until?.let {
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(it.date)
                } ?: endDate
            )
        }.getOrNull()
    }

    private fun parseParams(rruleStr: String): Map<String, String> {
        return rruleStr.removePrefix("RRULE:").trim().split(";")
            .map { it.split("=", limit = 2) }
            .filter { it.size == 2 && it[0].isNotBlank() }
            .associate { (key, value) -> key.uppercase() to value }
    }

    private fun normalizeDayToken(day: String): String? {
        return when (day.trim().uppercase()) {
            "SUNDAY", "SUN", "SU" -> "SU"
            "MONDAY", "MON", "MO" -> "MO"
            "TUESDAY", "TUE", "TU" -> "TU"
            "WEDNESDAY", "WED", "WE" -> "WE"
            "THURSDAY", "THU", "TH" -> "TH"
            "FRIDAY", "FRI", "FR" -> "FR"
            "SATURDAY", "SAT", "SA" -> "SA"
            else -> null
        }
    }

    private fun parseDayOfWeek(value: String): DayOfWeek? {
        return when (value) {
            "SU" -> DayOfWeek.SUNDAY
            "MO" -> DayOfWeek.MONDAY
            "TU" -> DayOfWeek.TUESDAY
            "WE" -> DayOfWeek.WEDNESDAY
            "TH" -> DayOfWeek.THURSDAY
            "FR" -> DayOfWeek.FRIDAY
            "SA" -> DayOfWeek.SATURDAY
            else -> null
        }
    }

    private fun String.takeLastIfHasOrdinal(): String? {
        return takeIf { it.length >= 3 }
    }
}

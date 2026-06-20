package com.example.pace.util

import android.view.View
import android.widget.TextView
import com.example.pace.data.model.Schedule
import com.example.pace.data.model.response.RouteInfo
import com.example.pace.data.model.response.RouteOnlyScheduleData
import com.google.gson.Gson
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

object ScheduleCountdownUtils {
    private const val WINDOW_MINUTES = 15L
    private val seoulZone: ZoneId = ZoneId.of("Asia/Seoul")
    private val gson = Gson()

    fun applyAlert(
        alertView: TextView,
        schedule: Schedule,
        routeInfo: RouteInfo?,
        enabled: Boolean = true,
        now: LocalDateTime = LocalDateTime.now()
    ) {
        val text = if (enabled) buildAlertText(schedule, routeInfo, now) else null
        applyAlertText(alertView, text)
    }

    fun applyAlert(
        alertView: TextView,
        isAllDay: Boolean,
        type: String,
        startDate: String?,
        startTime: String?,
        routeInfo: RouteInfo?,
        enabled: Boolean = true,
        now: LocalDateTime = LocalDateTime.now()
    ) {
        val text = if (enabled) {
            buildAlertText(isAllDay, type, startDate, startTime, routeInfo, now)
        } else {
            null
        }
        applyAlertText(alertView, text)
    }

    fun nextCountdownRefreshDelayMillis(
        schedules: List<Schedule>,
        routeInfoMap: Map<Long, RouteInfo> = emptyMap(),
        now: LocalDateTime = LocalDateTime.now()
    ): Long? {
        val candidates = schedules.flatMap { schedule ->
            val routeInfo = routeInfoMap[schedule.id] ?: parseRouteInfo(schedule.routeJson)
            buildCountdownTimes(
                isAllDay = schedule.isAllDay,
                type = schedule.type,
                startDate = schedule.startDate,
                startTime = schedule.startTime,
                routeInfo = routeInfo
            )
        }

        return candidates.mapNotNull { nextDelayFor(it, now) }.minOrNull()
    }

    fun nextRouteArrivalRefreshDelayMillis(
        schedules: List<Schedule>,
        routeInfoMap: Map<Long, RouteInfo> = emptyMap(),
        now: LocalDateTime = LocalDateTime.now()
    ): Long? {
        val candidates = schedules.flatMap { schedule ->
            val routeInfo = routeInfoMap[schedule.id] ?: parseRouteInfo(schedule.routeJson)
            buildRouteArrivalRefreshTime(schedule, routeInfo)
        }

        return candidates.mapNotNull { nextDelayFor(it, now) }.minOrNull()
    }

    fun nextRouteScheduleCountdownRefreshDelayMillis(
        schedules: List<RouteOnlyScheduleData>,
        now: LocalDateTime = LocalDateTime.now()
    ): Long? {
        val candidates = schedules.flatMap { item ->
            val info = item.scheduleInfo
            buildCountdownTimes(
                isAllDay = info.isAllDay,
                type = "ROUTE",
                startDate = info.startDate,
                startTime = info.startTime,
                routeInfo = item.route
            )
        }

        return candidates.mapNotNull { nextDelayFor(it, now) }.minOrNull()
    }

    private fun buildAlertText(
        schedule: Schedule,
        routeInfo: RouteInfo?,
        now: LocalDateTime
    ): String? {
        val resolvedRouteInfo = routeInfo ?: parseRouteInfo(schedule.routeJson)
        return buildAlertText(
            isAllDay = schedule.isAllDay,
            type = schedule.type,
            startDate = schedule.startDate,
            startTime = schedule.startTime,
            routeInfo = resolvedRouteInfo,
            now = now
        )
    }

    private fun buildAlertText(
        isAllDay: Boolean,
        type: String,
        startDate: String?,
        startTime: String?,
        routeInfo: RouteInfo?,
        now: LocalDateTime
    ): String? {
        if (isAllDay) return null

        val nowMinute = now.truncatedTo(ChronoUnit.MINUTES)
        val best = buildCountdownTimes(isAllDay, type, startDate, startTime, routeInfo)
            .mapNotNull { candidate ->
                val targetMinute = candidate.time.truncatedTo(ChronoUnit.MINUTES)
                val minutes = Duration.between(nowMinute, targetMinute).toMinutes()
                if (minutes in 0..WINDOW_MINUTES) {
                    candidate.copy(minutes = minutes)
                } else {
                    null
                }
            }
            .minWithOrNull(compareBy<CountdownCandidate> { it.minutes }.thenBy { it.priority })
            ?: return null

        return "${best.label} ${best.minutes}분 전"
    }

    private fun applyAlertText(alertView: TextView, text: String?) {
        if (text == null) {
            alertView.visibility = View.GONE
        } else {
            alertView.text = text
            alertView.visibility = View.VISIBLE
        }
    }

    private fun buildCountdownTimes(
        isAllDay: Boolean,
        type: String,
        startDate: String?,
        startTime: String?,
        routeInfo: RouteInfo?
    ): List<CountdownCandidate> {
        if (isAllDay) return emptyList()

        val candidates = mutableListOf<CountdownCandidate>()
        if (type == "ROUTE") {
            parseRouteDateTime(routeInfo?.departureTime, startDate)?.let {
                candidates.add(CountdownCandidate(label = "출발", time = it, priority = 0))
            }
        }
        parseScheduleStartDateTime(startDate, startTime)?.let {
            candidates.add(CountdownCandidate(label = "일정", time = it, priority = 1))
        }
        return candidates
    }

    private fun buildRouteArrivalRefreshTime(schedule: Schedule, routeInfo: RouteInfo?): List<CountdownCandidate> {
        if (schedule.type != "ROUTE" || schedule.isAllDay) return emptyList()
        val arrival = parseRouteDateTime(routeInfo?.arrivalTime, schedule.startDate) ?: return emptyList()
        return listOf(CountdownCandidate(label = "도착", time = arrival, priority = 2))
    }

    private fun nextDelayFor(candidate: CountdownCandidate, now: LocalDateTime): Long? {
        if (candidate.label == "도착") {
            return if (candidate.time.isAfter(now)) {
                Duration.between(now, candidate.time).toMillis().coerceAtLeast(0L) + 1000L
            } else {
                null
            }
        }

        val nowMinute = now.truncatedTo(ChronoUnit.MINUTES)
        val targetMinute = candidate.time.truncatedTo(ChronoUnit.MINUTES)
        val minutes = Duration.between(nowMinute, targetMinute).toMinutes()

        return when {
            minutes < 0 -> null
            minutes <= WINDOW_MINUTES -> millisUntilNextMinute(now)
            else -> Duration.between(now, targetMinute.minusMinutes(WINDOW_MINUTES))
                .toMillis()
                .coerceAtLeast(0L)
        }
    }

    private fun millisUntilNextMinute(now: LocalDateTime): Long {
        val nextMinute = now.truncatedTo(ChronoUnit.MINUTES).plusMinutes(1)
        return Duration.between(now, nextMinute).toMillis().coerceAtLeast(250L)
    }

    private fun parseScheduleStartDateTime(startDate: String?, startTime: String?): LocalDateTime? {
        val date = parseDate(startDate) ?: return null
        val time = parseTime(startTime) ?: return null
        return LocalDateTime.of(date, time)
    }

    private fun parseRouteDateTime(raw: String?, fallbackDate: String?): LocalDateTime? {
        if (raw.isNullOrBlank()) return null

        parseInstant(raw)?.let {
            return it.atZone(seoulZone).toLocalDateTime()
        }
        parseOffsetDateTime(raw)?.let {
            return it.atZoneSameInstant(seoulZone).toLocalDateTime()
        }
        parseLocalDateTime(raw)?.let {
            return it.atZone(ZoneOffset.UTC).withZoneSameInstant(seoulZone).toLocalDateTime()
        }

        val date = parseDate(fallbackDate) ?: return null
        val time = parseTime(raw) ?: return null
        return LocalDateTime.of(date, time)
    }

    private fun parseRouteInfo(routeJson: String?): RouteInfo? {
        if (routeJson.isNullOrBlank()) return null
        return runCatching { gson.fromJson(routeJson, RouteInfo::class.java) }.getOrNull()
    }

    private fun parseDate(raw: String?): LocalDate? {
        if (raw.isNullOrBlank()) return null
        return runCatching { LocalDate.parse(raw.take(10)) }.getOrNull()
    }

    private fun parseTime(raw: String?): LocalTime? {
        if (raw.isNullOrBlank()) return null
        return runCatching { LocalTime.parse(raw.take(5)) }.getOrNull()
    }

    private fun parseInstant(raw: String): Instant? {
        return try {
            Instant.parse(raw)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun parseOffsetDateTime(raw: String): OffsetDateTime? {
        return try {
            OffsetDateTime.parse(raw)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun parseLocalDateTime(raw: String): LocalDateTime? {
        return try {
            LocalDateTime.parse(raw)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private data class CountdownCandidate(
        val label: String,
        val time: LocalDateTime,
        val priority: Int,
        val minutes: Long = Long.MAX_VALUE
    )
}

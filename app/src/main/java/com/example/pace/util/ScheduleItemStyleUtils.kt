package com.example.pace.util

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.pace.R
import com.example.pace.data.model.Schedule
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

object ScheduleItemStyleUtils {
    fun isPastTimedSchedule(
        schedule: Schedule,
        now: LocalDateTime = LocalDateTime.now()
    ): Boolean {
        if (schedule.type == "ROUTE") return false
        if (schedule.isAllDay) return false

        val endDateTime = parseScheduleEndDateTime(schedule) ?: return false
        return endDateTime.isBefore(now)
    }

    fun applyScheduleColors(
        context: Context,
        schedule: Schedule,
        scheduleColor: Int,
        titleViews: List<TextView>,
        secondaryViews: List<TextView>,
        accentViews: List<ImageView>,
        categoryView: ImageView,
        pinnedView: ImageView
    ) {
        val primaryTextColor = ContextCompat.getColor(context, R.color.text_primary)
        val secondaryTextColor = ContextCompat.getColor(context, R.color.text_secondary)
        val disabledTextColor = ContextCompat.getColor(context, R.color.text_disabled)
        val isPastTimedSchedule = isPastTimedSchedule(schedule)

        val titleColor = if (isPastTimedSchedule) disabledTextColor else primaryTextColor
        val secondaryColor = if (isPastTimedSchedule) disabledTextColor else secondaryTextColor
        val categoryColor = if (isPastTimedSchedule) {
            (scheduleColor and 0x00FFFFFF) or (0x80 shl 24)
        } else {
            scheduleColor
        }

        titleViews.forEach { it.setTextColor(titleColor) }
        secondaryViews.forEach { it.setTextColor(secondaryColor) }
        accentViews.forEach { it.imageTintList = ColorStateList.valueOf(secondaryColor) }
        categoryView.imageTintList = ColorStateList.valueOf(categoryColor)
        pinnedView.imageTintList = if (isPastTimedSchedule) {
            ColorStateList.valueOf(disabledTextColor)
        } else {
            null
        }
    }

    fun resolveScheduleColor(schedule: Schedule): Int {
        return schedule.eventColor.takeIf { it != null && it != 0 }
            ?: schedule.calendarColor.takeIf { it != null && it != 0 }
            ?: Color.parseColor("#A2BD3B")
    }

    fun nextPastTimedRefreshDelayMillis(
        schedules: List<Schedule>,
        now: LocalDateTime = LocalDateTime.now()
    ): Long? {
        val nextEndDateTime = schedules
            .asSequence()
            .filterNot { it.type == "ROUTE" }
            .filterNot { it.isAllDay }
            .mapNotNull(::parseScheduleEndDateTime)
            .filter { !it.isBefore(now) }
            .minOrNull()

        return nextEndDateTime?.let {
            Duration.between(now, it).toMillis().coerceAtLeast(0L) + 1000L
        }
    }

    private fun parseScheduleEndDateTime(schedule: Schedule): LocalDateTime? {
        val endDate = runCatching { LocalDate.parse(schedule.endDate.take(10)) }.getOrNull()
            ?: return null
        val endTime = parseTime(schedule.endTime) ?: return null
        return LocalDateTime.of(endDate, endTime)
    }

    private fun parseTime(value: String): LocalTime? {
        return runCatching { LocalTime.parse(value.take(8)) }.getOrNull()
            ?: runCatching { LocalTime.parse(value.take(5)) }.getOrNull()
    }
}

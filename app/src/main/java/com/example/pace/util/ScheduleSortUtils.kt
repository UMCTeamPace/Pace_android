package com.example.pace.util

import com.example.pace.data.model.Schedule
import java.time.LocalDate
import java.time.LocalTime

object ScheduleSortUtils {
    fun displayComparator(
        today: LocalDate = LocalDate.now(),
        now: LocalTime = LocalTime.now()
    ): Comparator<Schedule> {
        return compareBy<Schedule>(
            { !it.isPinned },
            { if (it.type == "ROUTE") 0 else 1 },
            { if (!it.isPinned && isEndedTimedToday(it, today, now)) 1 else 0 },
            { if (it.isAllDay) 0 else 1 },
            { parseTimeOrEnd(it.startTime) }
        ).then { left, right ->
            compareCreationOrder(left.id, right.id)
        }
    }

    private fun isEndedTimedToday(schedule: Schedule, today: LocalDate, now: LocalTime): Boolean {
        if (schedule.isAllDay) return false
        val scheduleDate = runCatching { LocalDate.parse(schedule.startDate.take(10)) }.getOrNull()
        if (scheduleDate != today) return false

        val endTime = runCatching { LocalTime.parse(schedule.endTime) }.getOrNull() ?: return false
        return endTime.isBefore(now)
    }

    private fun parseTimeOrEnd(value: String?): LocalTime {
        return runCatching { LocalTime.parse(value) }.getOrDefault(LocalTime.MAX)
    }

    private fun compareCreationOrder(leftId: Long, rightId: Long): Int {
        return if (leftId < 0 && rightId < 0) {
            rightId.compareTo(leftId)
        } else {
            leftId.compareTo(rightId)
        }
    }
}

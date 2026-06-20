package com.example.pace.util

import com.example.pace.data.model.Schedule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ScheduleItemStyleUtilsTest {
    @Test
    fun pastTimedSchedule_onlyAppliesToToday() {
        val now = LocalDateTime.of(2026, 6, 19, 15, 0)

        assertFalse(
            ScheduleItemStyleUtils.isPastTimedSchedule(
                schedule(startDate = "2026-06-18", endDate = "2026-06-18", endTime = "10:00"),
                now
            )
        )

        assertTrue(
            ScheduleItemStyleUtils.isPastTimedSchedule(
                schedule(startDate = "2026-06-19", endDate = "2026-06-19", endTime = "10:00"),
                now
            )
        )

        assertFalse(
            ScheduleItemStyleUtils.isPastTimedSchedule(
                schedule(startDate = "2026-06-19", endDate = "2026-06-20", endTime = "01:00"),
                now
            )
        )
    }

    @Test
    fun routeAndAllDaySchedules_areNeverPastTimed() {
        val now = LocalDateTime.of(2026, 6, 19, 15, 0)

        assertFalse(
            ScheduleItemStyleUtils.isPastTimedSchedule(
                schedule(startDate = "2026-06-19", endDate = "2026-06-19", endTime = "10:00", type = "ROUTE"),
                now
            )
        )

        assertFalse(
            ScheduleItemStyleUtils.isPastTimedSchedule(
                schedule(startDate = "2026-06-19", endDate = "2026-06-19", endTime = "10:00", isAllDay = true),
                now
            )
        )
    }

    private fun schedule(
        startDate: String,
        endDate: String,
        endTime: String,
        type: String = "NORMAL",
        isAllDay: Boolean = false
    ): Schedule {
        return Schedule(
            id = 1L,
            title = "test",
            startDate = startDate,
            endDate = endDate,
            startTime = "09:00",
            endTime = endTime,
            isAllDay = isAllDay,
            memo = null,
            location = null,
            repeatRule = null,
            calendarId = 1L,
            calendarDisplayName = null,
            calendarAccountName = null,
            type = type,
            eventColor = null,
            calendarColor = null
        )
    }
}

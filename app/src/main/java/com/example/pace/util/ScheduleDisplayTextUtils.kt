package com.example.pace.util

object ScheduleDisplayTextUtils {
    private const val DEFAULT_SCHEDULE_TITLE = "새 일정"

    fun titleOrDefault(title: String?): String {
        return title?.takeIf { it.isNotBlank() } ?: DEFAULT_SCHEDULE_TITLE
    }
}

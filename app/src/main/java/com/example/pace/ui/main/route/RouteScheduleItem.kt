package com.example.pace.ui.main.route

import com.example.pace.data.model.response.RouteOnlyScheduleData

sealed class RouteScheduleItem {
    data class DateHeader(val dateString: String) : RouteScheduleItem()

    data class ScheduleContent(val data: RouteOnlyScheduleData) : RouteScheduleItem()
}
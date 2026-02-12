package com.example.pace.data.model.response


import com.google.gson.annotations.SerializedName
import java.io.Serial

data class ScheduleListResponse(
    @SerializedName("schedules") val schedules: List<ScheduleDetailResponse>
)

data class ScheduleDetailResponse(
    @SerializedName("scheduleId") val scheduleId: Long,
    @SerializedName("scheduleInfo") val scheduleInfo: ScheduleInfo,
    @SerializedName("place") val place: PlaceInfo?,
    @SerializedName("reminders") val reminders: List<ReminderInfo>,
    @SerializedName("route") val route: RouteInfo?
)

data class UpdateScheduleRouteResponse(
    @SerializedName("scheduleId") val scheduleId: Long,
    @SerializedName("routeId") val routeId: Long,
    @SerializedName("updatedAt") val updatedAt: String
)

data class DeleteScheduleRouteResponse(
    @SerializedName("scheduleId") val scheduleId: Long,
    @SerializedName("updatedAt") val updatedAt: String
)

data class SchedulePagingResponse(
    @SerializedName("content") val content: List<ScheduleItem>,
    @SerializedName("size") val size: Int,
    @SerializedName("number") val number: Int,
    @SerializedName("first") val first: Boolean,
    @SerializedName("last") val last: Boolean,
    @SerializedName("empty") val empty: Boolean
)

data class ScheduleItem(
    @SerializedName("scheduleId") val scheduleId: Long,
    @SerializedName("scheduleInfo") val scheduleInfo: ScheduleInfo,
    @SerializedName("place") val place: PlaceInfo?,
    @SerializedName("reminders") val reminders: List<ReminderInfo>,
    @SerializedName("route") val route: RouteInfo?
)

data class ScheduleInfo(
    @SerializedName("title") val title: String,
    @SerializedName("isAllDay") val isAllDay: Boolean,
    @SerializedName("startDate") val startDate: String,
    @SerializedName("endDate") val endDate: String,
    @SerializedName("startTime") val startTime: String?,
    @SerializedName("endTime") val endTime: String?,
    @SerializedName("memo") val memo: String?
)

data class PlaceInfo(
    @SerializedName("targetName") val targetName: String,
    @SerializedName("targetLat") val targetLat: Double,
    @SerializedName("targetLng") val targetLng: Double
)

data class ReminderInfo(
    @SerializedName("reminderType") val reminderType: String,
    @SerializedName("minutesBefore") val minutesBefore: Int
)

data class RouteInfo(
    @SerializedName("originName") val originName: String,
    @SerializedName("originLat") val originLat: Double,
    @SerializedName("originLng") val originLng: Double,
    @SerializedName("destName") val destName: String,
    @SerializedName("destLat") val destLat: Double,
    @SerializedName("destLng") val destLng: Double,
    @SerializedName("totalTime") val totalTime: Int,
    @SerializedName("totalDistance") val totalDistance: Int,
    @SerializedName("arrivalTime") val arrivalTime: String?,
    @SerializedName("departureTime") val departureTime: String?,
    @SerializedName("routeDetails") val routeDetails: List<RouteDetailResponse>
)

data class RouteDetailResponse(
    @SerializedName("sequence") val sequence: Int,
    @SerializedName("duration") val duration: Int,
    @SerializedName("distance") val distance: Int,
    @SerializedName("description") val description: String,
    @SerializedName("start_lat") val startLat: Double,
    @SerializedName("start_lng") val startLng: Double,
    @SerializedName("end_lat") val endLat: Double,
    @SerializedName("end_lng") val endLng: Double,
    @SerializedName("transit_type") val transitType: String?,
    @SerializedName("line_name") val lineName: String?,
    @SerializedName("line_color") val lineColor: String?,
    @SerializedName("stop_count") val stopCount: Int,
    @SerializedName("departure_stop") val departureStop: String?,
    @SerializedName("arrival_stop") val arrivalStop: String?,
    @SerializedName("shortName") val shortName: String?,
)

data class CreateScheduleResponse(
    @SerializedName("scheduleId") val scheduleId: Long,
    @SerializedName("scheduleInfo") val scheduleInfo: ScheduleInfo,
    @SerializedName("place") val place: PlaceInfo?,
    @SerializedName("reminders") val reminders: List<ReminderInfo>,
    @SerializedName("route") val route: RouteInfo?
)

data class ScheduleConversionResponse(
    @SerializedName("scheduleId") val scheduleId: Long,
    @SerializedName("isPathIncluded") val isPathIncluded: Boolean
)

data class WithdrawResponse(
    @SerializedName("result") val result: String
)
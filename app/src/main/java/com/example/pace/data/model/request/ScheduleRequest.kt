package com.example.pace.data.model.request

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class ScheduleRequest(
    @SerializedName("title") val title: String,
    @SerializedName("memo") val memo: String?,
    @SerializedName("is_route") val isRoute: Boolean,
    @SerializedName("start_time") val startTime: String,
    @SerializedName("end_time") val endTime: String
)

data class UpdateScheduleRouteRequest(
    @SerializedName("origin") val origin: Origin,
    @SerializedName("dest") val dest: Destination,
    @SerializedName("routeDetails") val routeDetails: List<RouteDetail>,
    @SerializedName("start_time") val startTime: String,
    @SerializedName("end_time") val endTime: String,
    @SerializedName("arrival_time") val arrivalTime:String?,
    @SerializedName("departure_time") val departureStop: String?,
    @SerializedName("total_time") val totalTime: Int,
    @SerializedName("total_distance") val totalDistance: Int
)

data class Origin(
    @SerializedName("origin_name") val originName: String,
    @SerializedName("origin_lat") val originLat: Double,
    @SerializedName("origin_lng") val originLng: Double
)

data class CreateScheduleRequest(
    @SerializedName("title") val title: String,
    @SerializedName("isAllDay") val isAllDay: Boolean,
    @SerializedName("startDate") val startDate: String,
    @SerializedName("endDate") val endDate: String,
    @SerializedName("startTime") val startTime: String?,
    @SerializedName("endTime") val endTime: String?,
    @SerializedName("memo") val memo: String?,
    @SerializedName("isPathIncluded") val isPathIncluded: Boolean,
    @SerializedName("isRepeat") val isRepeat: Boolean,
    @SerializedName("repeatInfo") val repeatInfo: RepeatInfo?,
    @SerializedName("place") val place: PlaceRequest?,
    @SerializedName("reminders") val reminders: List<ReminderRequest>,
    @SerializedName("route") val route: RouteRequest?,
    @SerializedName("color") val color: String? = "#DC354B"
)
data class RepeatInfo(
    @SerializedName("repeatType") val repeatType: String,
    @SerializedName("repeatInterval") val repeatInterval: Int,
    @SerializedName("daysOfWeek") val daysOfWeek: String?,
    @SerializedName("endType") val endType: String,
    @SerializedName("endCount") val endCount: Int,
    @SerializedName("repeatEndDate") val repeatEndDate: String?
): java.io.Serializable

data class PlaceRequest(
    @SerializedName("targetName") val targetName: String,
    @SerializedName("targetLat") val targetLat: Double,
    @SerializedName("targetLng") val targetLng: Double
)

data class ReminderRequest(
    @SerializedName("reminderType") val reminderType: String,
    @SerializedName("minutesBefore") val minutesBefore: Int
)

data class RouteRequest(
    @SerializedName("originName") val originName: String,
    @SerializedName("originLat") val originLat: Double,
    @SerializedName("originLng") val originLng: Double,
    @SerializedName("destName") val destName: String,
    @SerializedName("dest_lat") val destLat: Double,
    @SerializedName("dest_lng") val destLng: Double,
    @SerializedName("totalTime") val totalTime: Int,
    @SerializedName("totalDistance") val totalDistance: Int,
    @SerializedName("routeDetails") val routeDetails: List<RouteDetailRequest>
)

data class RouteDetailRequest(
    @SerializedName("sequence") val sequence: Int,
    @SerializedName("duration") val duration: Int,
    @SerializedName("distance") val distance: Int,
    @SerializedName("description") val description: String,
    @SerializedName("start_lat") val startLat: Double,
    @SerializedName("start_lng") val startLng: Double,
    @SerializedName("end_lat") val endLat: Double,
    @SerializedName("end_lng") val endLng: Double,
    @SerializedName("transit_type") val transitType: String,
    @SerializedName("line_name") val lineName: String?,
    @SerializedName("line_color") val lineColor: String?,
    @SerializedName("stop_count") val stopCount: Int,
    @SerializedName("departure_stop") val departureStop: String?,
    @SerializedName("arrival_stop") val arrivalStop: String?
)


data class UpdateRouteRequest(
    @SerializedName("dest") val dest: Destination,
    @SerializedName("routeDetails") val routeDetails: List<RouteDetail>,
    @SerializedName("start_time") val startTime: String,
    @SerializedName("end_time") val endTime: String,
    @SerializedName("total_time") val totalTime: Int,
    @SerializedName("total_distance") val totalDistance: Int
)

data class Destination(
    @SerializedName("dest_name") val destName: String,
    @SerializedName("dest_lat") val destLat: Double,
    @SerializedName("dest_lng") val destLng: Double
)

data class RouteDetail(
    @SerializedName("sequence") val sequence: Int,
    @SerializedName("duration") val duration: Int,
    @SerializedName("distance") val distance: Int,
    @SerializedName("description") val description: String,
    @SerializedName("start_lat") val startLat: Double,
    @SerializedName("start_lng") val startLng: Double,
    @SerializedName("end_lat") val endLat: Double,
    @SerializedName("end_lng") val endLng: Double,
    @SerializedName("transit_type") val transitType: String,
    @SerializedName("line_name") val lineName: String?,
    @SerializedName("line_color") val lineColor: String?,
    @SerializedName("stop_count") val stopCount: Int,
    @SerializedName("departure_stop") val departureStop: String?,
    @SerializedName("arrival_stop") val arrivalStop: String?
)

data class DeleteScheduleRequest(
    @SerializedName("scheduleIds") val scheduleIds: List<Long>
)

data class UpdateScheduleRequest(
    @SerializedName("title") val title: String,
    @SerializedName("memo") val memo: String?,
    @SerializedName("isAllDay") val isAllDay: Boolean,
    @SerializedName("startDate") val startDate: String,
    @SerializedName("endDate") val endDate: String,
    @SerializedName("startTime") val startTime: String?,
    @SerializedName("endTime") val endTime: String?,
    @SerializedName("isPathIncluded") val isPathIncluded: Boolean,
    @SerializedName("repeatInfo") val repeatInfo: RepeatInfo?,
    @SerializedName("place") val place: PlaceRequest?,
    @SerializedName("reminders") val reminders: List<ReminderRequest>
)
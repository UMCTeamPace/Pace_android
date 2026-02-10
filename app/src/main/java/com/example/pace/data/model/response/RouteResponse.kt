package com.example.pace.data.model.response

import com.google.gson.annotations.SerializedName

data class RouteApiResponse(
    @SerializedName("routeApiResDtoList") val routeList: List<RouteItem>
)

data class RouteItem(
    @SerializedName("totalDistance") val totalDistance: Int,
    @SerializedName("totalTime") val totalTime: Int,
    @SerializedName("arrivalTime") val arrivalTime: String,
    @SerializedName("departureTime") val departureTime: String,
    @SerializedName("routeDetailInfoResDTOList") val routeDetails: List<RouteDetail>
)

data class RouteDetail(
    @SerializedName("sequence") val sequence: Int,
    @SerializedName("startLat") val startLat: Double,
    @SerializedName("startLng") val startLng: Double,
    @SerializedName("endLat") val endLat: Double,
    @SerializedName("endLng") val endLng: Double,
    @SerializedName("duration") val duration: Int,
    @SerializedName("distance") val distance: Int,
    @SerializedName("description") val description: String?,
    @SerializedName("points") val points: String,
    @SerializedName("transitDetail") val transitDetail: TransitDetail?
)

data class TransitDetail(
    @SerializedName("transitType") val transitType: String,
    @SerializedName("lineName") val lineName: String,
    @SerializedName("lineColor") val lineColor: String?,
    @SerializedName("stopCount") val stopCount: Int,
    @SerializedName("departureStop") val departureStop: String,
    @SerializedName("arrivalStop") val arrivalStop: String,
    @SerializedName("departureTime") val departureTime: String,
    @SerializedName("shortName") val shortName: String,
    @SerializedName("locationLat") val locationLat: Double,
    @SerializedName("locationLng") val locationLng: Double,
    @SerializedName("headsign") val headsign: String,
    @SerializedName("stationPath") val stationPath: List<String>
)

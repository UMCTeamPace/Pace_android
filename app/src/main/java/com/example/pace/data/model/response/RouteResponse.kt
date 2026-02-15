package com.example.pace.data.model.response

import com.google.gson.annotations.SerializedName

data class RouteApiResponse(
    @SerializedName("routeApiResDtoList") val routeApiResDtoList: List<RouteResponse>?
)

data class RouteResponse(
    @SerializedName("totalDistance") val totalDistance: Int,
    @SerializedName("totalTime") val totalTime: Int,
    @SerializedName("arrivalTime") val arrivalTime: String,
    @SerializedName("departureTime") val departureTime: String,

    // 1. value: 서버에 저장하고 불러올 때 쓸 이름 (routeDetails)
    // 2. alternate: 경로 검색 API에서 데이터가 올 때의 이름 (ResDTO...)
    @SerializedName(
        value = "routeDetails",
        alternate = ["routeDetailInfoResDTOList"]
    )
    val routeDetails: List<RouteDetail>
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
    val transitType: String? = null,
    val lineColor: String? = null,
    val lineName: String? = null,
    val shortName: String? = null,
    val departureStop: String? = null,
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
    @SerializedName("arrivalTime") val arrivalTime: String?, // Swagger에 있는 필드 추가
    @SerializedName("shortName") val shortName: String,
    @SerializedName("locationLat") val locationLat: Double,
    @SerializedName("locationLng") val locationLng: Double,
    @SerializedName("headsign") val headsign: String?,
    @SerializedName("stationPath") val stationPath: List<String>?
)

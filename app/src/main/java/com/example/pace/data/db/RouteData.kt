package com.example.pace.data.db

import java.io.Serializable

data class RouteResponse(
    val totalDistance: Int,
    val totalTime: Int,
    val arrivalTime: String,
    val departureTime: String,
    val routeDetailInfoResDTOList: List<RouteDetail>
) : Serializable

data class RouteDetail(
    val sequence: Int,
    val startLat: Double,
    val startLng: Double,
    val endLat: Double,
    val endLng: Double,
    val duration: Int,
    val distance: Int,
    val description: String?,
    val transitDetail: TransitDetail?
) : Serializable

data class TransitDetail(
    val transitType: String,
    val lineName: String,
    val lineColor: String?,
    val stopCount: Int,
    val departureStop: String,
    val arrivalStop: String,
    val departureTime: String,
    val arrivalTime: String,
    val locationLat: Double,
    val locationLng: Double,
    val points: String?,
    val headsign: String?
) : Serializable
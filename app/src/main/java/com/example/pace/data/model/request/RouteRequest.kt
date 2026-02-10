package com.example.pace.data.model.request

import com.google.gson.annotations.SerializedName

data class RouteRequest(
    @SerializedName("originLat") val originLat: Double,
    @SerializedName("originLng") val originLng: Double,
    @SerializedName("destLat") val destLat: Double,
    @SerializedName("destLng") val destLng: Double,
    @SerializedName("arrivalTime") val arrivalTime: String?,
    @SerializedName("departureTime") val departureTime: String?,
    @SerializedName("transitType") val transitType: String?,
    @SerializedName("searchWay") val searchWay: String
)

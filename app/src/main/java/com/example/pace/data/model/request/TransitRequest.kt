package com.example.pace.data.model.request

import com.google.gson.annotations.SerializedName

data class SubwayTransitRequest(
    @SerializedName("startStationName") val startStationName: String,
    @SerializedName("endStationName") val endStationName: String,
    @SerializedName("lineName") val lineName: String
)
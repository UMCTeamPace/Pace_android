package com.example.pace.data.model.request

import com.google.gson.annotations.SerializedName

data class SavePlaceRequest(
    @SerializedName("placeName") val placeName: String,
    @SerializedName("latitude") val latitude: Double,
    @SerializedName("longitude") val longitude: Double,
    @SerializedName("address") val address: String,
    @SerializedName("groupId") val groupId: Long
)


data class MovePlaceGroupRequest(
    @SerializedName("placeIds") val placeIds: List<Long>,
    @SerializedName("targetGroupId") val targetGroupId: Long
)
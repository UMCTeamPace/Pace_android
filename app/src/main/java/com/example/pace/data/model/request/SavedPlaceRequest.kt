package com.example.pace.data.model.request

import com.google.gson.annotations.SerializedName

data class SavePlaceRequest(
    @SerializedName("placeName") val placeName: String,
    @SerializedName("placeId") val placeId: String,
    @SerializedName("groupId") val groupId: Long
)


data class MovePlaceGroupRequest(
    @SerializedName("placeIdList") val placeIdList: List<Long>,
    @SerializedName("targetGroupId") val targetGroupId: Long
)

data class DeletePlacesRequest(
    val placeIdList: List<Long>
)
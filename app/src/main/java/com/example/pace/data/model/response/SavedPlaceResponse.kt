package com.example.pace.data.model.response

import com.google.gson.annotations.SerializedName

data class SavePlaceResponse(
    @SerializedName("savedPlaceId") val savedPlaceId: Long,
    @SerializedName("groupId") val groupId: Long,
    @SerializedName("placeName") val placeName: String,
    @SerializedName("placeId") val placeId: String,
    @SerializedName("createdAt") val createdAt: String
)


data class SavedPlaceItem(
    @SerializedName("savedPlaceId") val savedPlaceId: Long,
    @SerializedName("placeId") val placeId: String,
    @SerializedName("placeName") val placeName: String,
    @SerializedName("address") val address: String,
    @SerializedName("groupId") val groupId: Long,
    @SerializedName("createdAt") val createdAt: String
)

data class SavedPlaceListResponse(
    @SerializedName("savedPlaceList") val savedPlaceList: List<SavedPlaceItem>,
    @SerializedName("listSize") val listSize: Int
)
package com.example.pace.data.model.response

import com.google.gson.annotations.SerializedName

data class GroupListResponse(
    @SerializedName("placeGroupList") val placeGroupList: List<GroupItem>,
    @SerializedName("listSize") val listSize: Int
)

data class GroupItem(
    @SerializedName("groupId") val groupId: Long,
    @SerializedName("groupName") val groupName: String,
    @SerializedName("groupColor") val groupColor: String,
    @SerializedName("createdAt") val createdAt: String
)

data class CreateGroupResponse(
    @SerializedName("groupId") val groupId: Long,
    @SerializedName("createdAt") val createdAt: String
)

data class UpdateGroupResponse(
    @SerializedName("groupId") val groupId: Long,
    @SerializedName("updatedAt") val updatedAt: String
)

data class BaseResponse(
    @SerializedName("status") val status: String? = null
)
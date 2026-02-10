package com.example.pace.data.model.request

import com.google.gson.annotations.SerializedName

data class CreateGroupRequest(
    @SerializedName("groupName") val groupName: String,
    @SerializedName("groupColor") val groupColor: String
)

data class UpdateGroupRequest(
    @SerializedName("groupName") val groupName: String,
    @SerializedName("groupColor") val groupColor: String
)

data class DeleteGroupRequest(
    val groupIdList: List<Long>
)
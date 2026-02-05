package com.example.pace.data.model.response

import com.google.gson.annotations.SerializedName

data class MemberSettingsResponse(
    @SerializedName("settingId")
    val settingId: Long,
    @SerializedName("earlyArrivalTime")
    val earlyArrivalTime: Int,
    @SerializedName("isNotiEnabled")
    val isNotiEnabled: Boolean,
    @SerializedName("isLocEnabled")
    val isLocEnabled: Boolean,
    @SerializedName("isReminderActive")
    val isReminderActive: Boolean,
    @SerializedName("deptReminderFreq")
    val deptReminderFreq: Int,
    @SerializedName("deptReminderInterval")
    val deptReminderInterval: Int,
    @SerializedName("calendarType")
    val calendarType: String,
    @SerializedName("reminderTimes")
    val reminderTimes: List<Int>,
    @SerializedName("createdAt")
    val createdAt: String,
    @SerializedName("updatedAt")
    val updatedAt: String
)

package com.example.pace.data.model.response

import com.google.gson.annotations.SerializedName

data class UpdateSettingsResponse(
    @SerializedName("alarmEnabled")
    val alarmEnabled: Boolean,
    @SerializedName("reminderTimes")
    val reminderTimes: List<Int>
)
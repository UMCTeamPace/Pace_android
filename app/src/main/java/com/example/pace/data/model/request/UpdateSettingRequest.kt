package com.example.pace.data.model.request

import com.google.gson.annotations.SerializedName

data class UpdateSettingsRequest(
    @SerializedName("isReminderActive")
    val isReminderActive: Boolean,

    @SerializedName("earlyArrivalTime")
    val earlyArrivalTime: Int,

    @SerializedName("calendarId")
    val calendarId: String,

    @SerializedName("alarms")
    val alarms: List<AlarmSettingRequest>
)

data class AlarmSettingRequest(
    @SerializedName("type")
    val type: String, // "SCHEDULE" 또는 "DEPARTURE"
    @SerializedName("minutes")
    val minutes: List<Int>
)

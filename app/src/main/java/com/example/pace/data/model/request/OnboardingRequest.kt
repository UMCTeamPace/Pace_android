package com.example.pace.data.model.request

import com.google.gson.annotations.SerializedName

data class OnboardingRequest(
    @SerializedName("isReminderActive") val isReminderActive: Boolean,
    @SerializedName("earlyArrivalTime") val earlyArrivalTime: Int,
    @SerializedName("calendarId") val calendarId: String,
    @SerializedName("alarms") val alarms: List<AlarmConfig>
)

data class AlarmConfig(
    @SerializedName("type") val type: String,
    @SerializedName("minutes") val minutes: List<Int>
)

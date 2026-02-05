package com.example.pace.data.model.request

import com.google.gson.annotations.SerializedName

data class UpdateSettingsRequest(
    @SerializedName("earlyArrivalTime")
    val earlyArrivalTime: Int,
    @SerializedName("isNotiEnabled")
    val isNotiEnabled: Boolean,
    @SerializedName("isLocEnabled")
    val isLocEnabled: Boolean,
    @SerializedName("isReminderActive")
    val isReminderActive: Boolean,
    @SerializedName("CalendarType")
    val calendarType: String,
    @SerializedName("reminderTimes")
    val reminderTimes: List<Int>,
    @SerializedName("scheduleReminderTimes")
    val scheduleReminderTimes: List<Int>,
    @SerializedName("departureReminderTimes")
    val departureReminderTimes: List<Int>,
    @SerializedName("alarms")
    val alarms: List<AlarmSetting>
)

data class AlarmSetting(
    @SerializedName("type")
    val type: String,
    @SerializedName("minutes")
    val minutes: List<Int>
)
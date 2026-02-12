package com.example.pace.data.model.response

import com.google.gson.annotations.SerializedName

data class UpdateSettingsResponse(
    @SerializedName("memberId") // 스웨거에 memberId가 있다면 반드시 추가!
    val memberId: Long,
    @SerializedName("earlyArrivalTime")
    val earlyArrivalTime: Int,
    @SerializedName("isNotiEnabled")
    val isNotiEnabled: Boolean,
    @SerializedName("isLocEnabled")
    val isLocEnabled: Boolean,
    @SerializedName("isReminderActive")
    val isReminderActive: Boolean,
    @SerializedName("calendarType")
    val calendarType: String,
    @SerializedName("reminderTimes")
    val reminderTimes: List<Int>,
    @SerializedName("scheduleReminderTimes")
    val scheduleReminderTimes: List<Int>,
    @SerializedName("departureReminderTimes")
    val departureReminderTimes: List<Int>,
    @SerializedName("alarms")
    val alarms: List<AlarmSettingResponse>
)

data class AlarmSettingResponse(
    @SerializedName("type")
    val type: String,
    @SerializedName("minutes")
    val minutes: List<Int>
)
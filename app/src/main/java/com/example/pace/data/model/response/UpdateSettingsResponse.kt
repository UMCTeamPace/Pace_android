package com.example.pace.data.model.response

import com.google.gson.annotations.SerializedName

// 1. 최상위 응답 객체
data class UpdateSettingsResponse(
    @SerializedName("isSuccess")
    val isSuccess: Boolean,

    @SerializedName("code")
    val code: String,

    @SerializedName("message")
    val message: String,

    @SerializedName("result")
    val result: SettingsResult // 실제 데이터는 이 안에 있음
)

// 2. "result" 내부에 담긴 데이터 객체
data class SettingsResult(
    @SerializedName("isReminderActive")
    val isReminderActive: Boolean,

    @SerializedName("earlyArrivalTime")
    val earlyArrivalTime: Int,

    @SerializedName("calendarType")
    val calendarType: String,

    @SerializedName("alarms")
    val alarms: List<AlarmSettingResponse>
)

data class AlarmSettingResponse(
    @SerializedName("type")
    val type: String,
    @SerializedName("minutes")
    val minutes: List<Int>
)
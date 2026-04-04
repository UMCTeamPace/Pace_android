package com.example.pace.data.model.response

import com.google.gson.annotations.SerializedName
import com.example.pace.data.model.UserSettingsEntity

data class MemberSettingsResponse(
    @SerializedName("earlyArrivalTime")
    val earlyArrivalTime: Int,
    @SerializedName("isReminderActive")
    val isReminderActive: Boolean,
    @SerializedName(value = "calendarId", alternate = ["calendarType"])
    val calendarId: String?,
    @SerializedName("syncedCalendarIds")
    val syncedCalendarIds: List<Long>? = null,
    @SerializedName("alarms")
    val alarms: List<MemberSettingAlarmResponse> = emptyList()
)

data class MemberSettingAlarmResponse(
    @SerializedName("type")
    val type: String,
    @SerializedName("minutes")
    val minutes: List<Int>
)

fun MemberSettingsResponse.toEntity(existing: UserSettingsEntity? = null): UserSettingsEntity {
    val resolvedCalendarId = calendarId?.toLongOrNull() ?: existing?.calendarId ?: -1L
    val resolvedSyncedCalendarIds = syncedCalendarIds ?: existing?.syncedCalendarIds ?: if (resolvedCalendarId != -1L) listOf(resolvedCalendarId) else emptyList()
    val departureAlarms = alarms.firstOrNull { it.type == "DEPARTURE" }?.minutes ?: emptyList()
    val scheduleAlarms = alarms.firstOrNull { it.type == "SCHEDULE" }?.minutes ?: emptyList()

    return UserSettingsEntity(
        id = existing?.id ?: 1L,
        isReminderActive = isReminderActive,
        earlyArrivalTime = earlyArrivalTime,
        calendarId = resolvedCalendarId,
        syncedCalendarIds = resolvedSyncedCalendarIds,
        departureAlarms = departureAlarms,
        scheduleAlarms = scheduleAlarms,
        isSynced = true,
        lastUpdated = System.currentTimeMillis()
    )
}

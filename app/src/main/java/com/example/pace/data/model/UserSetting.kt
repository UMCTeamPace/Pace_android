package com.example.pace.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.pace.data.model.request.AlarmSettingRequest
import com.example.pace.data.model.request.UpdateSettingsRequest

@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey val id: Long = 1L,
    val isReminderActive: Boolean,
    val earlyArrivalTime: Int,
    val calendarId: Long,
    val syncedCalendarIds: List<Long> = emptyList(),
    val departureAlarms: List<Int>,
    val scheduleAlarms: List<Int>,
    val isSynced: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
)

// 💡 클래스 괄호 밖 하단에 추가하세요.
fun UserSettingsEntity.toUpdateRequest(): UpdateSettingsRequest {
    // 알람 리스트 생성 로직
    val alarmList = mutableListOf<AlarmSettingRequest>()

    // 비어있지 않을 때만 추가하거나, 빈 리스트라도 타입을 명시해서 추가
    alarmList.add(AlarmSettingRequest(type = "DEPARTURE", minutes = this.departureAlarms))
    alarmList.add(AlarmSettingRequest(type = "SCHEDULE", minutes = this.scheduleAlarms))

    return UpdateSettingsRequest(
        isReminderActive = this.isReminderActive,
        earlyArrivalTime = this.earlyArrivalTime,
        calendarId = this.calendarId.toString(), // 👈 Long을 String으로 변환
        alarms = alarmList,

//        // 서버 DTO의 나머지 필드들 (필요에 따라 매핑)
//        isNotiEnabled = true,
//        isLocEnabled = true,
//        reminderTimes = emptyList(), // 필요시 추가
//        scheduleReminderTimes = this.scheduleAlarms,
//        departureReminderTimes = this.departureAlarms
    )
}

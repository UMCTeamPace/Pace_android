package com.example.pace.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey val id: Int = 1, // 설정값은 하나만 저장하므로 ID를 1로 고정
    val isReminderActive: Boolean,
    val earlyArrivalTime: Int,
    val calendarType: String,

    // Converters 덕분에 List<Int>를 그대로 사용할 수 있습니다.
    val departureAlarms: List<Int>,
    val scheduleAlarms: List<Int>,

    // 동기화 상태 추적용
    val isSynced: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
)
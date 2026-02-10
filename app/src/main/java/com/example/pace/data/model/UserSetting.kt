package com.example.pace.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_settings")
data class UserSettingsEntity(
    @PrimaryKey val id: Long = 1L,
    val isReminderActive: Boolean,
    val earlyArrivalTime: Int,

    // [기본 캘린더] 하나만 선택 (RadioGroup/RadioButton용)
    val calendarId: Long,

    // [동기화할 캘린더들] 다중 선택 (RecyclerView/Toggle용) 👈 추가됨
    val syncedCalendarIds: List<Long> = emptyList(),

    val departureAlarms: List<Int>,
    val scheduleAlarms: List<Int>,

    val isSynced: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
)
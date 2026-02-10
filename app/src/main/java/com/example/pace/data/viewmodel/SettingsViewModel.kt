package com.example.pace.data.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pace.data.datasource.AuthDataStore
import com.example.pace.data.db.UserSettingsDao
import com.example.pace.data.model.UserSettingsEntity
import com.example.pace.data.model.request.AlarmSetting
import com.example.pace.data.model.request.UpdateSettingsRequest
import com.example.pace.data.repository.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dao: UserSettingsDao,
    private val repository: SettingsRepository // 👈 1. 여기에 추가!
) : ViewModel() {
    // 로컬 고정 ID
    private val LOCAL_USER_ID = 1L

    // 1. UI용: Room에서 데이터를 실시간 관찰 (StateFlow)
    // DB의 값이 바뀌면 UI가 자동으로 업데이트됩니다.
    val userSettings: StateFlow<UserSettingsEntity?> = dao.getSettingsFlow(LOCAL_USER_ID)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    // 2. 여유 시간 업데이트 (Room DB에만 저장)
    fun updateEarlyArrival(minutes: Int) {
        viewModelScope.launch {
            dao.updateEarlyArrivalTime(LOCAL_USER_ID, minutes)
            Log.d("SETTINGS_DEBUG", "로컬 DB 여유시간 변경: $minutes 분")
        }
    }

    // 3. 리마인더 알림 스위치 업데이트
    fun updateReminderStatus(isActive: Boolean) {
        viewModelScope.launch {
            // Dao에 updateReminderStatus 함수가 있다면 사용하세요.
            // 없다면 전체 Entity를 가져와서 copy 후 update 해야 합니다.
            val current = dao.getSettings(LOCAL_USER_ID)
            current?.let {
                dao.insertSettings(it.copy(isReminderActive = isActive))
            }
        }
    }

    fun updateDefaultCalendar(calendarId: Long) {
        viewModelScope.launch {
            val currentSettings = userSettings.first()
            currentSettings?.let {
                val updated = it.copy(calendarId = calendarId, isSynced = false)
                // 👈 2. 이제 repository 참조가 가능해집니다.
                repository.updateSettings(updated)
            }
        }
    }

    fun toggleCalendarSync(calendarId: Long, isChecked: Boolean) {
        viewModelScope.launch {
            val currentSettings = userSettings.value ?: return@launch
            val currentList = currentSettings.syncedCalendarIds.toMutableList()

            if (isChecked) {
                if (!currentList.contains(calendarId)) currentList.add(calendarId)
            } else {
                currentList.remove(calendarId)
            }

            val updated = currentSettings.copy(syncedCalendarIds = currentList, isSynced = false)
            repository.updateSettings(updated)
        }
    }

}
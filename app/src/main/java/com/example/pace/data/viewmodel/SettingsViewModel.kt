package com.example.pace.data.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pace.data.datasource.AuthDataStore
import com.example.pace.data.model.UserSettingsEntity
import com.example.pace.data.repository.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val authDataStore: AuthDataStore // 👈 1. 토큰을 가져오기 위해 주입 추가
) : ViewModel() {
    val userSettings: StateFlow<UserSettingsEntity?> = repository.getUserSettings()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    /**
     * [공통 로직] 모든 설정 변경은 이 함수를 거치도록 통합하면 코드가 깔끔해집니다.
     */
    private fun saveAndSync(updatedEntity: UserSettingsEntity) {
        viewModelScope.launch {
            val token = authDataStore.getAccessToken() ?: ""
            // Repository 내부에서 로컬 DB 저장 + 서버 PATCH를 동시에 수행함
            repository.updateSettings(token, updatedEntity)
        }
    }

    // 2. 여유 시간 업데이트 (서버와 동기화가 필요하다면 saveAndSync 사용)
    fun updateEarlyArrival(minutes: Int) {
        userSettings.value?.let {
            saveAndSync(it.copy(earlyArrivalTime = minutes))
        }
    }

    // 3. 리마인더 알림 스위치 업데이트
    fun updateReminderStatus(isActive: Boolean) {
        userSettings.value?.let {
            saveAndSync(it.copy(isReminderActive = isActive))
        }
    }

    // 4. 기본 캘린더 변경
    fun updateDefaultCalendar(calendarId: Long) {
        userSettings.value?.let {
            saveAndSync(
                it.copy(
                    calendarId = calendarId,
                    lastUpdated = System.currentTimeMillis()
                )
            )
        }
    }

    // 5. 동기화할 캘린더 목록 변경
    // SettingsViewModel.kt

    fun toggleCalendarSync(calendarId: Long, isChecked: Boolean) {
        viewModelScope.launch {
            // 1. 현재 로컬 설정 가져오기
            val currentSettings = userSettings.value ?: return@launch
            val currentList = currentSettings.syncedCalendarIds.toMutableList()

            // 2. 체크 상태에 따라 리스트 수정
            if (isChecked) {
                if (!currentList.contains(calendarId)) currentList.add(calendarId)
            } else {
                currentList.remove(calendarId)
            }

            // 3. 💡 saveAndSync 대신 repository.updateSettingsLocally 사용!
            // 이렇게 하면 서버 API를 호출하지 않고 로컬 DB만 업데이트합니다.
            val updatedSettings = currentSettings.copy(
                syncedCalendarIds = currentList,
                lastUpdated = System.currentTimeMillis()
            )

            saveAndSync(updatedSettings)

            Log.d("LOCAL_DB", "캘린더 선택 상태 변경 (로컬): $calendarId -> $isChecked")
        }
    }

    // 6. 알람 시간 업데이트
    fun updateScheduleAlarms(alarms: List<Int>) {
        userSettings.value?.let {
            saveAndSync(it.copy(scheduleAlarms = alarms.take(5)))
        }
    }

    fun updateDepartureAlarms(alarms: List<Int>) {
        userSettings.value?.let {
            saveAndSync(it.copy(departureAlarms = alarms.take(5)))
        }
    }
    fun updateSelectedCalendars(selectedIds: List<Long>) {
        viewModelScope.launch {
            // 💡 _userSettings가 아니라 userSettings입니다 (오타 수정)
            val currentSettings = userSettings.value ?: return@launch

            val updatedSettings = currentSettings.copy(
                syncedCalendarIds = selectedIds,
                lastUpdated = System.currentTimeMillis()
            )

            // 서버 통신 없이 DB만 업데이트
            saveAndSync(updatedSettings)

            Log.d("LOCAL_DB", "선택된 캘린더 ID들 로컬 저장 완료: $selectedIds")
        }
    }
}

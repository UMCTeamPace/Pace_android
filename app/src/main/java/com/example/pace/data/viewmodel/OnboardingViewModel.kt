package com.example.pace.data.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.pace.data.datasource.AuthDataStore
import com.example.pace.data.model.UserSettingsEntity
import com.example.pace.data.model.request.AlarmConfig
import com.example.pace.data.model.request.OnboardingRequest
import com.example.pace.data.repository.repository.OnboardingRepository
import com.example.pace.data.worker.SyncSettingsWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val repository: OnboardingRepository,
    private val authDataStore: AuthDataStore,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {

    // --- 수집 정보 변수 ---
    var earlyArrivalTime: Int = 0
    var departureAlarms = mutableListOf<Int>()
    var scheduleAlarms = mutableListOf<Int>()
    var selectedCalendarId: Long = -1L
    var isReminderActive: Boolean = true

    // --- 화면 전환 이벤트 ---
    private val _onboardingSuccess = MutableSharedFlow<Boolean>()
    val onboardingSuccess = _onboardingSuccess.asSharedFlow()

    // --- 온보딩 완료 시 실행 ---
    fun completeOnboarding() {
        viewModelScope.launch {
            val tempEntity = UserSettingsEntity(
                id = 1L,
                isReminderActive = isReminderActive,
                earlyArrivalTime = earlyArrivalTime,
                calendarId = selectedCalendarId,
                departureAlarms = departureAlarms,
                scheduleAlarms = scheduleAlarms,
                isSynced = false
            )

            val request = mapToRequest(tempEntity)
            val accessToken = authDataStore.getAccessToken() ?: ""

            try {
                val response = repository.saveOnboardingSettings(
                    accessToken = "$accessToken",
                    request = request
                )

                if (response.isSuccess && response.result != null) {
                    Log.d("PACE_DEBUG", "응답 데이터: ${response.result}")

                    if (response.isSuccess && response.result != null) {
                        // 💡 response.result 자체가 이제 OnboardingResponse 객체입니다.
                        val actualData = response.result

                        authDataStore.saveAuthData(
                            accessToken = actualData.accessToken, // 👈 바로 접근 가능!
                            refreshToken = actualData.refreshToken
                        )

                        repository.saveSettingsToLocal(tempEntity.copy(isSynced = true))
                        _onboardingSuccess.emit(true)

                        Log.d("PACE_DEBUG", "✅ 온보딩 완료! 토큰: ${actualData.accessToken}")
                    }
                } else {
                    handleFail(tempEntity)
                }
            } catch (e: Exception) {
                Log.e("PACE_DEBUG", "❌ 온보딩 실패: ${e.message}")
                handleFail(tempEntity)
            }
        }
    }

    // 실패 시 로컬에만 저장하고 나중에 동기화하도록 WorkManager 예약
    private suspend fun handleFail(entity: UserSettingsEntity) {
        repository.saveSettingsToLocal(entity)
        scheduleSync()
        // 실패하더라도 일단 메인으로 보낼지 여부는 정책에 따라 선택 (현재는 일단 보냄)
        _onboardingSuccess.emit(true)
    }

    private fun mapToRequest(entity: UserSettingsEntity): OnboardingRequest {
        val alarms = mutableListOf<AlarmConfig>()
        if (entity.departureAlarms.isNotEmpty()) {
            alarms.add(AlarmConfig("DEPARTURE", entity.departureAlarms))
        }
        if (entity.scheduleAlarms.isNotEmpty()) {
            alarms.add(AlarmConfig("SCHEDULE", entity.scheduleAlarms))
        }

        return OnboardingRequest(
            isReminderActive = entity.isReminderActive,
            earlyArrivalTime = entity.earlyArrivalTime,
            calendarType = "GOOGLE", // 혹은 실제 사용하는 타입
            alarms = alarms
        )
    }

    private fun scheduleSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncSettingsWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, java.util.concurrent.TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "settings_sync_work",
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
    }
}
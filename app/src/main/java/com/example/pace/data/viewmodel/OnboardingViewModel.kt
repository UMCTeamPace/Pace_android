package com.example.pace.data.viewmodel

import android.content.Context
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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val repository: OnboardingRepository,
    private val authDataStore: AuthDataStore,
    @ApplicationContext private val context: Context
) : ViewModel() {

    var earlyArrivalTime: Int = 0
    var departureAlarms = mutableListOf<Int>()
    var scheduleAlarms = mutableListOf<Int>()
    var selectedCalendarId: Long = -1L
    var isReminderActive: Boolean = true

    private val _onboardingSuccess = MutableSharedFlow<Boolean>()
    val onboardingSuccess = _onboardingSuccess.asSharedFlow()

    fun completeOnboarding(allCalendarIds: List<Long>) {
        viewModelScope.launch {
            val tempEntity = UserSettingsEntity(
                id = 1L,
                isReminderActive = isReminderActive,
                earlyArrivalTime = earlyArrivalTime,
                calendarId = selectedCalendarId,
                syncedCalendarIds = allCalendarIds,
                departureAlarms = departureAlarms,
                scheduleAlarms = scheduleAlarms,
                isSynced = false
            )

            val onboardingToken = authDataStore.getTempToken()
                ?: authDataStore.getAccessToken()
                ?: ""

            if (onboardingToken.isBlank()) {
                Log.e("PACE_DEBUG", "온보딩 요청에 사용할 토큰이 없습니다.")
                handleFail(tempEntity)
                return@launch
            }

            val request = mapToRequest(tempEntity)

            try {
                val response = repository.saveOnboardingSettings(
                    accessToken = if (onboardingToken.startsWith("Bearer ")) {
                        onboardingToken
                    } else {
                        "Bearer $onboardingToken"
                    },
                    request = request
                )

                if (response.isSuccess && response.result != null) {
                    val actualData = response.result
                    authDataStore.saveAuthData(
                        accessToken = actualData.accessToken,
                        refreshToken = actualData.refreshToken
                    )
                    authDataStore.clearTempToken()
                    authDataStore.setOnboardingComplete(true)

                    repository.saveSettingsToLocal(tempEntity.copy(isSynced = true))
                    _onboardingSuccess.emit(true)

                    Log.d("PACE_DEBUG", "온보딩 완료, 정식 토큰 저장 완료")
                } else {
                    Log.e("PACE_DEBUG", "온보딩 서버 저장 실패: ${response.code}, ${response.message}")
                    handleFail(tempEntity)
                }
            } catch (e: Exception) {
                Log.e("PACE_DEBUG", "온보딩 저장 예외: ${e.message}", e)
                handleFail(tempEntity)
            }
        }
    }

    private suspend fun handleFail(entity: UserSettingsEntity) {
        repository.saveSettingsToLocal(entity)
        scheduleSync()
        _onboardingSuccess.emit(false)
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
            calendarId = entity.calendarId.toString(),
            alarms = alarms
        )
    }

    private fun scheduleSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncSettingsWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "settings_sync_work",
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
    }
}

package com.example.pace.data.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.pace.data.model.UserSettingsEntity
import com.example.pace.data.model.request.AlarmConfig
import com.example.pace.data.model.request.OnboardingRequest
import com.example.pace.data.model.response.RawDefaultResponse
import com.example.pace.data.repository.repository.OnboardingRepository
import com.example.pace.data.worker.SyncSettingsWorker
import com.kakao.sdk.auth.TokenManager
import dagger.hilt.android.internal.Contexts.getApplication
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val repository: OnboardingRepository,
    // Hilt에서 주입받은 Application을 통해 WorkManager를 호출합니다.
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {

    // --- 4가지 수집 정보 변수 ---
    var earlyArrivalTime: Int = 0
    var departureAlarms = mutableListOf<Int>()
    var scheduleAlarms = mutableListOf<Int>()
    var calendarType: String = "GOOGLE"
    var isReminderActive: Boolean = true

    // --- 온보딩 완료 시 실행 ---
    fun completeOnboarding() {
        viewModelScope.launch {
            val settings = UserSettingsEntity(
                earlyArrivalTime = earlyArrivalTime,
                departureAlarms = departureAlarms,
                scheduleAlarms = scheduleAlarms,
                calendarType = calendarType,
                isReminderActive = isReminderActive,
                isSynced = false
            )

            // 1. 로컬 저장 (Room DB)
            repository.saveSettingsToLocal(settings)

            // 2. 서버 동기화 예약 (아래 정의한 scheduleSync 함수를 재사용하는 것이 좋습니다)
            scheduleSync()
        }
    }

    // --- Helper: Entity -> Request 변환 ---
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
            calendarType = entity.calendarType,
            alarms = alarms
        )
    }

    // --- WorkManager 예약 ---
    private fun scheduleSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncSettingsWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                1, java.util.concurrent.TimeUnit.MINUTES
            )
            .build()

        // getApplication() 대신 주입받은 context를 사용합니다.
        WorkManager.getInstance(context).enqueueUniqueWork(
            "settings_sync_work",
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
    }
}
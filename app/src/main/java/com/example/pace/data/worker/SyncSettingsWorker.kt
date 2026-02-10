package com.example.pace.data.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.pace.data.datasource.AuthDataStore
import com.example.pace.data.db.UserSettingsDao
import com.example.pace.data.model.request.AlarmSetting
import com.example.pace.data.model.request.UpdateSettingsRequest
import com.example.pace.data.repository.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncSettingsWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val dao: UserSettingsDao,
    private val repository: SettingsRepository,
    private val authDataStore: AuthDataStore
) : CoroutineWorker(context, workerParams) {

    // 로컬 DB에서 사용하는 고정 ID
    private val LOCAL_USER_ID = 1L

    override suspend fun doWork(): Result {
        // 1. 고정 ID(1L)에 해당하는 설정 데이터 가져오기 (동기화 여부와 상관없이 최신본)
        val settings = dao.getSettings(LOCAL_USER_ID) ?: return Result.success()

        // 2. Request 조립
        val alarmSettings = mutableListOf<AlarmSetting>()
        if (settings.departureAlarms.isNotEmpty()) {
            alarmSettings.add(AlarmSetting(type = "DEPARTURE", minutes = settings.departureAlarms))
        }
        if (settings.scheduleAlarms.isNotEmpty()) {
            alarmSettings.add(AlarmSetting(type = "SCHEDULE", minutes = settings.scheduleAlarms))
        }

        val request = UpdateSettingsRequest(
            earlyArrivalTime = settings.earlyArrivalTime,
            isNotiEnabled = true,
            isLocEnabled = true,
            isReminderActive = settings.isReminderActive,
            calendarType = "GOOGLE",
            reminderTimes = emptyList(),
            scheduleReminderTimes = settings.scheduleAlarms,
            departureReminderTimes = settings.departureAlarms,
            alarms = alarmSettings
        )

        // 3. 토큰 준비
        val token = authDataStore.getAccessToken() ?: ""
        val fullToken = if (token.startsWith("Bearer ")) token else "Bearer $token"

        return try {
            // 4. PATCH 요청 전송 (💡 memberId 파라미터 삭제)
            val response = repository.updateMemberSettings(
                accessToken = fullToken,
                request = request
            )

            if (response.isSuccess) {
                // 5. 로컬 DB 동기화 상태만 업데이트 (ID 교체 로직 삭제)
                dao.updateSyncStatus(LOCAL_USER_ID, true)

                Log.d("PACE_SYNC", "✅ 백그라운드 설정 동기화 완료")
                Result.success()
            } else {
                Log.e("PACE_SYNC", "❌ 동기화 실패: ${response.message}")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e("PACE_SYNC", "❌ 에러 발생: ${e.message}")
            Result.retry()
        }
    }
}
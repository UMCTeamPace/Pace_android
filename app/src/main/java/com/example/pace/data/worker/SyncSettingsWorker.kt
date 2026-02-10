package com.example.pace.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.pace.data.db.UserSettingsDao
import com.example.pace.data.model.request.AlarmConfig
import com.example.pace.data.model.request.UpdateSettingsRequest
import com.example.pace.data.model.response.RawDefaultResponse
import com.example.pace.data.repository.repository.SettingsRepository
import com.example.pace.data.datasource.AuthDataStore // 토큰 관리는 보통 여기서 하므로 변경 권장
import com.example.pace.data.model.request.AlarmSetting
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncSettingsWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val dao: UserSettingsDao,
    private val repository: SettingsRepository,
    private val authDataStore: AuthDataStore // 토큰 추출용
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        // 1. 로컬 DB 데이터 확인
        val settings = dao.getUnsyncedSettings() ?: return Result.success()

        // 1. alarms 리스트 생성 (AlarmSetting 클래스 사용)
        val alarmSettings = mutableListOf<AlarmSetting>()
        if (settings.departureAlarms.isNotEmpty()) {
            alarmSettings.add(AlarmSetting(type = "DEPARTURE", minutes = settings.departureAlarms))
        }
        if (settings.scheduleAlarms.isNotEmpty()) {
            alarmSettings.add(AlarmSetting(type = "SCHEDULE", minutes = settings.scheduleAlarms))
        }

// 2. 최종 Request 객체 생성
        val request = UpdateSettingsRequest(
            earlyArrivalTime = settings.earlyArrivalTime,
            isNotiEnabled = true, // 기본값 혹은 권한 상태에 따라 설정
            isLocEnabled = true,  // 기본값 혹은 권한 상태에 따라 설정
            isReminderActive = settings.isReminderActive,
            calendarType = settings.calendarType,

            // 개별 리스트 필드들도 채워줍니다 (서버 요구사항에 따라 빈 리스트 혹은 settings 값 주입)
            reminderTimes = emptyList(), // 필요시 settings에서 추출
            scheduleReminderTimes = settings.scheduleAlarms,
            departureReminderTimes = settings.departureAlarms,

            // 위에서 만든 AlarmSetting 리스트
            alarms = alarmSettings
        )

        // 3. 실제 저장된 토큰 가져오기 (AuthDataStore 내부 함수 확인 필요)
        val accessToken = authDataStore.getAccessToken() ?: "" // 예시: 변수명에 맞게 수정
        if (accessToken.isEmpty()) return Result.failure()

        // 4. 서버 업데이트 실행 (memberId도 유동적으로 처리하는 것이 좋습니다)
        return try {
            val response = repository.updateMemberSettings(
                accessToken = "$accessToken",
                memberId = 1L, // TODO: 실제 로그인된 유저 ID가 필요하다면 authDataStore에서 가져오세요.
                request = request
            )

            // 5. RawDefaultResponse의 isSuccess 필드로 체크
            if (response.isSuccess) {
                dao.updateSyncStatus(true)
                Result.success()
            } else {
                Result.retry() // 서버 응답은 왔으나 실패 시 재시도
            }
        } catch (e: Exception) {
            Result.retry() // 네트워크 끊김 등 예외 발생 시 재시도
        }
    }
}
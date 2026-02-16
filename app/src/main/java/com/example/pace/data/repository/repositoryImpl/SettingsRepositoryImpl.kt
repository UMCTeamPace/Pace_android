package com.example.pace.data.repository.repositoryImpl

import android.content.Context
import android.util.Log
import androidx.work.*
import com.example.pace.data.api.SettingsService
import com.example.pace.data.db.UserSettingsDao
import com.example.pace.data.model.UserSettingsEntity
import com.example.pace.data.model.request.UpdateSettingsRequest
import com.example.pace.data.model.response.MemberSettingsResponse
import com.example.pace.data.model.response.RawDefaultResponse
import com.example.pace.data.model.response.UpdateSettingsResponse
import com.example.pace.data.model.toUpdateRequest
import com.example.pace.data.repository.repository.SettingsRepository
import com.example.pace.data.util.safeApiCall
import com.example.pace.data.worker.SyncSettingsWorker
import dagger.hilt.android.qualifiers.ApplicationContext // 💡 Import 추가 확인
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class SettingsRepositoryImpl @Inject constructor(
    private val dao: UserSettingsDao,
    private val api: SettingsService,
    @ApplicationContext private val context: Context // 💡 여기에 context 주입을 추가하세요!
) : SettingsRepository {

    override suspend fun getMemberSettings(
        accessToken: String,
    ): RawDefaultResponse<MemberSettingsResponse> {
        return safeApiCall { api.getMemberSettings(accessToken) }
    }

    override suspend fun updateMemberSettings(
        accessToken: String,
        request: UpdateSettingsRequest
    ): RawDefaultResponse<UpdateSettingsResponse> {
        return safeApiCall { api.updateMemberSettings(accessToken, request) }
    }

    override suspend fun updateSettings(accessToken: String, settings: UserSettingsEntity) {
        // 1. 로컬 DB에 저장 (UI 즉시 반영) - 동기화 상태 false로 시작
        dao.insertSettings(settings.copy(isSynced = false))

        try {
            val request = settings.toUpdateRequest()

            // 2. 서버 API 호출 (Bearer 접두어는 필요시 추가)
            val token = if (accessToken.startsWith("Bearer ")) accessToken else "Bearer $accessToken"
            val response = api.updateMemberSettings(token, request)

            if (response.isSuccess) {
                // 서버 업데이트 성공 시 동기화 상태 true로 변경
                dao.updateSyncStatus(settings.id, true)
                Log.d("PACE_SYNC", "서버 동기화 성공")
            } else {
                Log.e("PACE_SYNC", "서버 응답 실패: ${response.message}")
                scheduleSync() // 💡 응답 실패 시 워커 예약
            }
        } catch (e: Exception) {
            Log.e("PACE_SYNC", "네트워크 오류 발생", e)
            scheduleSync() // 💡 네트워크 오류 시 워커 예약
        }
    }

    override suspend fun updateSettingsLocally(settings: UserSettingsEntity) {
        try {
            dao.insertSettings(settings)
            Log.d("PACE_LOCAL", "로컬 DB 업데이트 성공 (서버 동기화 제외)")
        } catch (e: Exception) {
            Log.e("PACE_LOCAL", "로컬 DB 업데이트 실패", e)
        }
    }

    override fun getUserSettings(): Flow<UserSettingsEntity?> {
        return dao.getUserSettings()
    }

    private fun scheduleSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncSettingsWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        // 💡 이제 생성자에서 주입받은 context를 사용할 수 있습니다.
        WorkManager.getInstance(context).enqueueUniqueWork(
            "SettingsSyncWork",
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
    }
}
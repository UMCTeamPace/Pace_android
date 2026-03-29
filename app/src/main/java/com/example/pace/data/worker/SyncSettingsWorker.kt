package com.example.pace.data.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.pace.data.datasource.AuthDataStore
import com.example.pace.data.db.UserSettingsDao
import com.example.pace.data.model.toUpdateRequest
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

    override suspend fun doWork(): Result {
        // 1. 로컬 DB에서 최신 설정 데이터 가져오기
        val settings = dao.getUnsyncedSettings() ?: return Result.success()

        // 2. 이미 만들어둔 확장 함수를 사용하여 Request 조립 (필요 없는 필드 자동 정리)
        val request = settings.toUpdateRequest()

        // 3. 토큰 준비
        val token = authDataStore.getAccessToken() ?: ""
        // 주의: repository.updateMemberSettings 내부에서 "Bearer "를 붙여주는지 확인하세요.
        // 만약 중복으로 붙지 않게 하려면 아래와 같이 처리합니다.
        val fullToken = if (token.startsWith("Bearer ")) token else "Bearer $token"

        return try {
            // 4. 서버 전송
            val response = repository.updateMemberSettings(
                accessToken = fullToken,
                request = request
            )

            if (response.isSuccess) {
                // 5. 성공 시 로컬 DB 동기화 상태 업데이트
                dao.updateSyncStatus(settings.id, true)
                Log.d("PACE_SYNC", "✅ 백그라운드 설정 동기화 완료")
                Result.success()
            } else {
                Log.e("PACE_SYNC", "❌ 동기화 실패: ${response.message}")
                Result.retry() // 네트워크 문제 등일 경우 다시 시도
            }
        } catch (e: Exception) {
            Log.e("PACE_SYNC", "❌ 에러 발생: ${e.message}")
            Result.retry()
        }
    }
}
package com.example.pace.data.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.pace.data.repository.repository.ScheduleRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class ScheduleFinalizeWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val repository: ScheduleRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // 1. 전달받은 일정 ID 확인
        val scheduleId = inputData.getLong("schedule_id", -1L)
        if (scheduleId == -1L) {
            Log.e("ScheduleWorker", "❌ 유효하지 않은 일정 ID")
            return Result.failure()
        }

        return try {
            Log.d("ScheduleWorker", "🔄 일정 전환 작업 시작 (ID: $scheduleId)")

            // 2. 리포지토리에 통합된 전환 함수 호출
            // 이 함수 안에서 [기존조회 -> 서버삭제 -> 일반일정생성]이 모두 일어남
            val isSuccess = repository.convertRouteToNormalLocal(scheduleId)

            if (isSuccess) {
                Log.d("ScheduleWorker", "✅ 일정 전환 및 로컬 저장 성공")
                Result.success()
            } else {
                Log.e("ScheduleWorker", "⚠️ 전환 실패 (서버 응답 오류 등), 재시도 예약")
                Result.retry() // 네트워크 문제 등 일시적 오류일 경우 재시도
            }
        } catch (e: Exception) {
            Log.e("ScheduleWorker", "❌ 워커 실행 중 예외 발생: ${e.message}")
            Result.failure()
        }
    }
}
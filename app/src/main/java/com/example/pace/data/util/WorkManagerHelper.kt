package com.example.pace.data.util

import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.pace.data.model.Schedule
import com.example.pace.data.worker.ScheduleFinalizeWorker
import java.util.concurrent.TimeUnit

// com.example.pace.utils.WorkManagerHelper.kt
object WorkManagerHelper {
    fun scheduleRouteFinalize(context: Context, schedule: Schedule) {
        // 기존 예약된 작업이 있다면 취소 (수정 시 중복 방지)
        WorkManager.getInstance(context).cancelAllWorkByTag("finalize_${schedule.id}")

        // 도착 시간 계산 (yyyyMMddTHHMM 포맷 기준)
        val arrivalTimeMillis = parseToMillis(schedule.endDate, schedule.endTime)
        val delay = arrivalTimeMillis - System.currentTimeMillis()

        if (delay > 0) {
            val data = Data.Builder()
                .putLong("schedule_id", schedule.id)
                .build()

            val finalizeRequest = OneTimeWorkRequestBuilder<ScheduleFinalizeWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .addTag("finalize_${schedule.id}") // 태그를 붙여야 나중에 취소/수정 가능
                .setInputData(data)
                .build()

            WorkManager.getInstance(context).enqueue(finalizeRequest)
            Log.d("WorkManager", "일정 ${schedule.id} 전환 예약 완료: ${delay/1000}초 후 실행")
        }
    }

    fun cancelFinalize(context: Context, scheduleId: Long) {
        WorkManager.getInstance(context).cancelAllWorkByTag("finalize_$scheduleId")
    }

    // 시간 파싱 유틸 (데이터 모델에 맞춰 수정 필요)
    private fun parseToMillis(date: String, time: String): Long {
        // yyyy-MM-dd HH:mm 형태를 Long으로 변환하는 로직
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
        return sdf.parse("$date $time")?.time ?: 0L
    }
}
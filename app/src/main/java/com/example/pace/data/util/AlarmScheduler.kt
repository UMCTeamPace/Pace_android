package com.example.pace.data.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.pace.AlarmReceiver
import java.util.*

object AlarmScheduler {
    fun schedulePaceAlarm(context: Context, scheduleTimeMillis: Long, leadMinutes: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // 1. 트리거 시간 계산 (일정 시간 - 리드 타임)
        val triggerTime = scheduleTimeMillis - (leadMinutes * 60 * 1000)

        // 디버깅 로그: 예약하려는 시간이 현재보다 미래인지 확인
        val diff = (triggerTime - System.currentTimeMillis()) / 1000
        android.util.Log.d("PaceAlarm", "알람 예약 시도: $diff 초 후 울림 예정 (데이터: $leadMinutes 분)")

        if (triggerTime <= System.currentTimeMillis()) {
            android.util.Log.e("PaceAlarm", "실패: 예약 시간이 이미 지났습니다.")
            return
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            // ⭐ 핵심: 이 데이터를 리시버가 받아서 액티비티로 넘겨야 함
            putExtra("MINUTES_LEFT", leadMinutes)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            leadMinutes, // 각 리드타임(60, 30, 10)별로 고유한 알람을 가짐
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE // Android 14+ 대응
        )

        // 2. 정확한 알람 예약
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } else {
                android.util.Log.e("PaceAlarm", "권한 없음: 정확한 알람 권한이 필요합니다.")
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }
}
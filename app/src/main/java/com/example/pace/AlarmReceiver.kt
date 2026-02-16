package com.example.pace

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 아이디 하나로 통일!
        val channelId = "pace_alert_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val minutesLeft = intent.getIntExtra("MINUTES_LEFT", 0)
        Log.d("PaceAlarm", "리시버에서 받은 시간: $minutesLeft")

        // 1. 채널 생성 (아이디 일치 확인: pace_alert_channel)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Pace 알람", NotificationManager.IMPORTANCE_HIGH).apply {
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(false)
                description = "외출 준비 알람 전용 채널입니다."
            }
            notificationManager.createNotificationChannel(channel)
        }

        val fullScreenIntent = Intent(context, AlertActivity::class.java).apply {
            action = Intent.ACTION_MAIN // 표준 액션으로 변경
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION
            putExtra("MINUTES_LEFT", minutesLeft)
        }


        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            (System.nanoTime() % Int.MAX_VALUE).toInt(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("외출 준비 시간!")
            .setContentText("설정한 알람 시간이 되었습니다.")
            .setPriority(NotificationCompat.PRIORITY_MAX) // 1. 최상위 우선순위
            .setCategory(NotificationCompat.CATEGORY_ALARM) // 2. '알람' 카테고리 명시
            .setFullScreenIntent(fullScreenPendingIntent, true) // 3. true가 강제 실행 의미
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // 4. 잠금화면에서도 공공연하게 띄움
            .setOngoing(true) // 5. 사용자가 끄기 전까지 유지
            .setSilent(true) // 6. 소리 대신 화면에 집중하게 함

        // 3. 알림 발사
        notificationManager.notify(100, builder.build())
    }
}
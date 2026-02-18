package com.example.pace

import android.annotation.SuppressLint
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
    @SuppressLint("FullScreenIntentPolicy")
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
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION
            putExtra("MINUTES_LEFT", minutesLeft)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            1000, // 고정된 요청 코드를 사용하여 인텐트 식별성 확보
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("외출 준비 시간!")
            .setContentText(if (minutesLeft > 0) "출발까지 ${minutesLeft}분 남았습니다." else "지금 출발해야 합니다!")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)

        // 알림 전송
        notificationManager.notify(100, builder.build())
    }
}
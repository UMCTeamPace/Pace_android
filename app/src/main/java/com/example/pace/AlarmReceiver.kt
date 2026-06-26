package com.example.pace

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {
    companion object {
        const val NOTIFICATION_ID = 100
        const val CHANNEL_ID = "pace_alert_channel_vibrate"
    }

    @SuppressLint("FullScreenIntentPolicy")
    override fun onReceive(context: Context, intent: Intent) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val minutesLeft = intent.getIntExtra("MINUTES_LEFT", 0)
        Log.d("PaceAlarm", "리시버에서 받은 시간: $minutesLeft")

        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val canUseFullScreenIntent = if (Build.VERSION.SDK_INT >= 34) {
            notificationManager.canUseFullScreenIntent()
        } else {
            true
        }

        Log.d(
            "PaceAlarm",
            "AlarmReceiver state: action=${intent.action}, isInteractive=${powerManager.isInteractive}, " +
                "isKeyguardLocked=${keyguardManager.isKeyguardLocked}, " +
                "isDeviceSecure=${keyguardManager.isDeviceSecure}, " +
                "canUseFullScreenIntent=$canUseFullScreenIntent"
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Pace 알람",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 800, 250, 800)
                description = "외출 준비 알람 전용 채널입니다."
            }
            notificationManager.createNotificationChannel(channel)
        }

        val fullScreenIntent = Intent(context, AlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("MINUTES_LEFT", minutesLeft)
            putExtra("TEST_WEATHER_STATUS", intent.getStringExtra("TEST_WEATHER_STATUS"))
            putExtra("TEST_WEATHER_DESC", intent.getStringExtra("TEST_WEATHER_DESC"))
            putExtra("TEST_LOCATION", intent.getStringExtra("TEST_LOCATION"))
            putExtra("TEST_TEMP", intent.getDoubleExtra("TEST_TEMP", Double.NaN))
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            1000,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = if (minutesLeft > 0) {
            "출발까지 ${minutesLeft}분 남았습니다."
        } else {
            "지금 출발해야 합니다."
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(loadAppIconBitmap(context))
            .setContentTitle("외출 준비 시간!")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setVibrate(longArrayOf(0, 800, 250, 800))
            .setAutoCancel(true)

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    private fun loadAppIconBitmap(context: Context): Bitmap? {
        val drawable = ContextCompat.getDrawable(context, R.mipmap.ic_main) ?: return null
        return drawable.toBitmap()
    }

    private fun Drawable.toBitmap(): Bitmap {
        if (this is BitmapDrawable && bitmap != null) {
            return bitmap
        }

        val width = if (intrinsicWidth > 0) intrinsicWidth else 96
        val height = if (intrinsicHeight > 0) intrinsicHeight else 96
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        return bitmap
    }
}

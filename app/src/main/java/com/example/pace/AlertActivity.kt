package com.example.pace

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.pace.data.viewmodel.AlertViewModel
import com.example.pace.databinding.ActivityAlertBinding

class AlertActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlertBinding
    private lateinit var viewModel: AlertViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        setupLockScreenFlags()
        logLockState("before-super")

        super.onCreate(savedInstanceState)
        binding = ActivityAlertBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this).get(AlertViewModel::class.java)

        val minutesLeft = intent.getIntExtra("MINUTES_LEFT", 0)
        Log.d("PaceAlarm", "AlertActivity onCreate: minutes=$minutesLeft, sdk=${Build.VERSION.SDK_INT}")
        logLockState("after-content-view")
        binding.root.postDelayed({ logLockState("after-content-view-delayed") }, 500L)

        setupObservers()

        val testWeatherStatus = intent.getStringExtra("TEST_WEATHER_STATUS")
        if (testWeatherStatus != null) {
            val testTemp = intent.getDoubleExtra("TEST_TEMP", 23.0)
            val testWeatherDesc = intent.getStringExtra("TEST_WEATHER_DESC") ?: "테스트 날씨"
            val testLocation = intent.getStringExtra("TEST_LOCATION") ?: "테스트"
            Log.d(
                "PaceAlarm",
                "AlertActivity test weather: status=$testWeatherStatus, temp=$testTemp, minutes=$minutesLeft"
            )
            viewModel.initTestAlarmData(
                weatherStatusName = testWeatherStatus,
                temp = testTemp,
                weatherDesc = testWeatherDesc,
                location = testLocation,
                minutesLeft = minutesLeft
            )
        } else {
            viewModel.initAlarmData(minutesLeft)
            viewModel.loadAlertData("Seoul,KR", BuildConfig.YOUR_OPENWEATHER_API_KEY, minutesLeft)
        }
        Log.d("PaceAlarm", "AlertActivity minutes confirmed: $minutesLeft")

        binding.btnClose.setOnClickListener { dismissAlarm() }
        binding.btnOfflineClose.setOnClickListener { dismissAlarm() }
    }

    private fun setupObservers() {
        viewModel.alertTheme.observe(this) { theme ->
            Log.d("AlertCheck", "Current Image Resource ID: ${theme.imageRes}")
            val isOffline = viewModel.isOffline.value ?: false

            if (isOffline) {
                binding.layoutNormal.visibility = View.GONE
                binding.layoutOffline.visibility = View.VISIBLE

                binding.ivOfflineCharacter.setImageResource(theme.imageRes)
                binding.tvOfflineWeatherInfo.text = theme.topWeatherInfo
                binding.tvOfflineSubMessage.text = theme.subMessage
                binding.tvOfflineMainMessage.text = theme.mainMessage
            } else {
                binding.layoutNormal.visibility = View.VISIBLE
                binding.layoutOffline.visibility = View.GONE

                binding.ivWeatherIcon.visibility = View.VISIBLE
                binding.ivWeatherIcon.setImageResource(R.drawable.ic_location_pin)
                binding.tvWeatherInfo.text = theme.topWeatherInfo
                binding.ivCharacter.setImageResource(theme.imageRes)
                binding.tvSubMessage.text = theme.subMessage
                binding.tvMainMessage.text = theme.mainMessage
            }
        }

        viewModel.isLoading.observe(this) {
            // Loading UI can be added here if the alarm screen needs a pending state.
        }
    }

    override fun onResume() {
        super.onResume()
        logLockState("onResume")
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        logLockState("onWindowFocusChanged=$hasFocus")
    }

    private fun setupLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        Log.d("PaceAlarm", "AlertActivity lock-screen flags applied")
    }

    private fun logLockState(stage: String) {
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        Log.d(
            "PaceAlarm",
            "AlertActivity $stage: isKeyguardLocked=${keyguardManager.isKeyguardLocked}, " +
                "isDeviceSecure=${keyguardManager.isDeviceSecure}, hasWindowFocus=${hasWindowFocus()}"
        )
    }

    private fun dismissAlarm() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(AlarmReceiver.NOTIFICATION_ID)
        finish()
    }
}

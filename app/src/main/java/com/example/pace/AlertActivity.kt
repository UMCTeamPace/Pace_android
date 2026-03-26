package com.example.pace

import android.app.KeyguardManager
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

        // [추가] 풀스크린 알람을 위한 화면 켜짐 및 잠금해제 설정
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        setupLockScreenFlags()

        super.onCreate(savedInstanceState)
        binding = ActivityAlertBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. ViewModel 초기화 (AndroidViewModel 형식이므로 context 자동 처리)
        viewModel = ViewModelProvider(this).get(AlertViewModel::class.java)

        val minutes = intent.getIntExtra("MINUTES_LEFT", 0)

        // 2. 관찰자(Observers) 설정
        setupObservers()

        viewModel.initAlarmData(minutes)

        // AlertActivity.onCreate 내부
        val minutesLeft = intent.getIntExtra("MINUTES_LEFT", 0)

        viewModel.loadAlertData("Seoul,KR", BuildConfig.YOUR_OPENWEATHER_API_KEY, minutesLeft)
        Log.d("PaceAlarm", "액티비티에서 최종 확인한 시간: $minutesLeft")

        viewModel.initAlarmData(minutesLeft)

        // 4. 버튼 이벤트 설정 (온라인/오프라인 공통)
        binding.btnClose.setOnClickListener { finish() }
        binding.btnOfflineClose.setOnClickListener { finish() }
    }

    private fun setupObservers() {
        // [테마 데이터 관찰] 날씨 정보나 오프라인 상태에 따라 UI를 업데이트합니다.
        viewModel.alertTheme.observe(this) { theme ->
            android.util.Log.d("AlertCheck", "Current Image Resource ID: ${theme.imageRes}")
            val isOffline = viewModel.isOffline.value ?: false

            if (isOffline) {
                // [A] 오프라인 화면 처리 (layoutOffline 노출)
                binding.layoutNormal.visibility = View.GONE
                binding.layoutOffline.visibility = View.VISIBLE

                // 오프라인 전용 리소스 및 메시지 바인딩
                binding.ivOfflineCharacter.setImageResource(theme.imageRes)
                binding.tvOfflineWeatherInfo.text = theme.topWeatherInfo
                binding.tvOfflineSubMessage.text = theme.subMessage
                binding.tvOfflineMainMessage.text = theme.mainMessage
            } else {
                // [B] 정상(온라인) 화면 처리 (layoutNormal 노출)
                binding.layoutNormal.visibility = View.VISIBLE
                binding.layoutOffline.visibility = View.GONE

                // 1. 상단 날씨 아이콘 처리 (null 체크 포함)
                if (theme.weatherIconRes != null) {
                    binding.ivWeatherIcon.visibility = View.VISIBLE
                    binding.ivWeatherIcon.setImageResource(theme.weatherIconRes)
                } else {
                    binding.ivWeatherIcon.visibility = View.GONE
                }

                // 2. 캐릭터 및 텍스트 정보 업데이트
                binding.tvWeatherInfo.text = theme.topWeatherInfo
                binding.ivCharacter.setImageResource(theme.imageRes)
                binding.tvSubMessage.text = theme.subMessage
                binding.tvMainMessage.text = theme.mainMessage
            }
        }

        // [로딩 상태 관찰] 필요한 경우 ProgressBar 등의 UI 제어를 할 수 있습니다.
        viewModel.isLoading.observe(this) { isLoading ->
            // binding.loadingBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
    }

    private fun setupLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }
    }
}
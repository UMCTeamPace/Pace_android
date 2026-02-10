package com.example.pace

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
//import com.example.pace.data.model.PrepStep
//import com.example.pace.data.model.WeatherStatus
import com.example.pace.databinding.ActivityAlertBinding

class AlertActivity : AppCompatActivity() {

//    private lateinit var binding: ActivityAlertBinding
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//
//        // 3. 레이아웃과 연결합니다.
//        binding = ActivityAlertBinding.inflate(layoutInflater)
//        setContentView(binding.root)
//
//        // 4. (중요) 실제로는 Intent나 ViewModel에서 현재 데이터(단계, 날씨)를 받아와야 합니다.
//        // 지금은 테스트를 위해 고정값을 넣은 예시입니다.
//        val currentStep = PrepStep.SHOWER
//        val currentWeather = WeatherStatus.SUNNY
//
//        // 5. 앞서 만든 테마 결정 함수를 호출합니다.
//        val theme = getAlertTheme(currentStep, currentWeather)
//
//        // 6. 화면의 각 요소(Lottie, 텍스트)에 데이터를 뿌려줍니다.
//        // XML 레이아웃 파일에 선언한 ID값(lottieView, tvMainMessage 등)과 일치해야 합니다.
//        binding.lottieView.setAnimation(theme.lottieJson)
//        binding.lottieView.playAnimation() // 애니메이션 시작
//        binding.tvMainMessage.text = theme.message
//        binding.tvWeatherInfo.text = theme.topWeatherInfo
//
//        // 7. 알림 끄기 버튼 클릭 이벤트
//        binding.btnClose.setOnClickListener {
//            finish() // 액티비티 종료
//        }
//    }
}
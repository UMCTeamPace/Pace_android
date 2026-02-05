package com.example.pace.ui.onboarding

import android.os.Bundle
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.example.pace.R
import com.example.pace.data.model.OnboardingItem
import com.example.pace.databinding.ActivityOnboardingBinding

class OnboardingActivity : AppCompatActivity() {
    private lateinit var binding: ActivityOnboardingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. 어댑터 생성 (데이터 리스트 없이 생성)
        val adapter = OnboardingAdapter(this)

        // 2. ViewPager2에 연결
        binding.vpOnboarding.adapter = adapter

        // 3. 인디케이터 연결
        binding.dotsIndicator.setViewPager2(binding.vpOnboarding)

        // 카카오 로그인 버튼 로직
        binding.btnKakaoLogin.setOnClickListener {
            // 로그인 처리
        }
    }
}
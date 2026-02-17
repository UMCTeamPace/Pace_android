package com.example.pace.ui.splash

import android.animation.Animator
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.airbnb.lottie.LottieAnimationView
import com.example.pace.R
import com.example.pace.ui.main.MainActivity
import com.example.pace.ui.onboarding.OnboardingActivity
import com.example.pace.ui.onboarding.PermissionActivity
import com.kakao.sdk.auth.AuthApiClient
import com.kakao.sdk.user.UserApiClient

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        setContentView(R.layout.activity_splash)

        val lottieView = findViewById<LottieAnimationView>(R.id.lottieAnimationView)

        lottieView.speed = -0.6f

        lottieView.addAnimatorListener(object : Animator.AnimatorListener {

            override fun onAnimationEnd(animation: Animator) {
                // 1. 카카오 토큰이 있는지 확인
                if (AuthApiClient.instance.hasToken()) {
                    // 2. 토큰이 있다면 유효한지 서버에 한 번 더 확인 (선택 사항이지만 권장)
                    UserApiClient.instance.me { user, error ->
                        if (error != null) {
                            // 토큰은 있지만 유효하지 않은 경우 (로그인 만료 등)
                            navigateToOnboarding()
                        } else {
                            // 로그인 성공 상태 -> 메인으로 직행
                            navigateToMain()
                        }
                    }
                } else {
                    // 토큰이 아예 없는 경우 -> 온보딩으로 이동
                    navigateToOnboarding()
                }
            }

            override fun onAnimationStart(p0: Animator) {}
            override fun onAnimationCancel(p0: Animator) {}
            override fun onAnimationRepeat(p0: Animator) {}
        })
    }

    // 메인 화면으로 이동하는 함수
    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        applyTransition()
        finish()
    }

    // 온보딩 화면으로 이동하는 함수
    private fun navigateToOnboarding() {
        val intent = Intent(this, OnboardingActivity::class.java)
        startActivity(intent)
        applyTransition()
        finish()
    }

    // 기존에 작성하신 애니메이션 트랜지션 로직
    private fun applyTransition() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }
}
package com.example.pace.ui.splash

import android.animation.Animator
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.airbnb.lottie.LottieAnimationView
import com.example.pace.PaceApplication
import com.example.pace.R
import com.example.pace.data.datasource.AuthDataStore
import com.example.pace.ui.main.MainActivity
import com.example.pace.ui.onboarding.OnboardingActivity
import com.example.pace.ui.onboarding.PermissionActivity
import com.kakao.sdk.auth.AuthApiClient
import com.kakao.sdk.user.UserApiClient
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SplashActivity : AppCompatActivity() {

    @Inject
    lateinit var authDataStore: AuthDataStore

    //private val authDataStore by lazy { (application as PaceApplication).authDataStore }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val lottieView = findViewById<LottieAnimationView>(R.id.lottieAnimationView)

        lottieView.speed = -0.6f



        lottieView.addAnimatorListener(object : Animator.AnimatorListener {

            override fun onAnimationEnd(animation: Animator) {
                // 핵심 수정 부분: 저장된 액세스 토큰이 있는지 확인
                val accessToken = authDataStore.getAccessToken()

                if (accessToken != null) {
                    // 1. 토큰이 있으면 로그인된 상태 -> 메인으로
                    navigateToMain()
                } else {
                    // 2. 토큰이 없으면 로그인 필요 -> 온보딩으로
                    navigateToOnboarding()
                }
            }

            override fun onAnimationStart(p0: Animator) {}
            override fun onAnimationCancel(p0: Animator) {}
            override fun onAnimationRepeat(p0: Animator) {}
        })
    }

    private fun arePermissionsGranted(): Boolean {
        val permissions = arrayOf(
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )
        return permissions.all {
            androidx.core.content.ContextCompat.checkSelfPermission(this@SplashActivity, it) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }
    private fun handleRedirection() {
        if (arePermissionsGranted()) {
            // [재로그인 상황] 권한은 이미 다 있음!
            // 권한 설정 페이지(PermissionActivity)를 건너뛰고 바로 메인(또는 로그인 버튼 화면)으로
            navigateToMain()
        } else {
            // [신규 가입/초기화 상황] 권한이 없음
            navigateToOnboarding()
        }
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
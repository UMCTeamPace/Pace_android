package com.example.pace.ui.splash

import android.animation.Animator
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.airbnb.lottie.LottieAnimationView
import com.example.pace.PaceApplication
import com.example.pace.R
import com.example.pace.ui.main.MainActivity
import com.example.pace.ui.onboarding.OnboardingActivity
import com.example.pace.ui.onboarding.PermissionActivity
import com.kakao.sdk.auth.AuthApiClient
import com.kakao.sdk.user.UserApiClient

class SplashActivity : AppCompatActivity() {

    private val authDataStore by lazy { (application as PaceApplication).authDataStore }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


        setContentView(R.layout.activity_splash)

        val lottieView = findViewById<LottieAnimationView>(R.id.lottieAnimationView)

        lottieView.speed = -0.6f



        lottieView.addAnimatorListener(object : Animator.AnimatorListener {

            override fun onAnimationEnd(animation: Animator) {
                if (AuthApiClient.instance.hasToken()) {
                    UserApiClient.instance.me { user, error ->
                        if (error == null && user != null) {
                            // [로그인 성공]
                            // 💡 여기서 설정 완료 여부를 체크합니다!
                            if (authDataStore.isOnboardingComplete()) { // isOnboardingComplete는 DataStore에 구현해야 할 함수 이름입니다.
                                navigateToMain() // 설정 완료 유저 -> 메인행
                            } else {
                                navigateToOnboarding() // 가입은 됐는데 설정 안 한 유저 -> 온보딩행
                            }
                        } else {
                            // 로그인 실패(토큰 만료 등) -> 온보딩(로그인부터 다시)
                            navigateToOnboarding()
                        }
                    }
                } else {
                    // [토큰 없음] 로그아웃/탈퇴 유저 -> 온보딩으로 가서 가입부터 새로
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
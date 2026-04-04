package com.example.pace.ui.splash

import android.animation.Animator
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.airbnb.lottie.LottieAnimationView
import com.example.pace.R
import com.example.pace.data.datasource.AuthDataStore
import com.example.pace.data.model.request.ReissueRequest
import com.example.pace.data.model.response.DefaultResponse
import com.example.pace.data.repository.repository.AuthControllerRepository
import com.example.pace.ui.main.MainActivity
import com.example.pace.ui.onboarding.OnboardingActivity
import com.example.pace.ui.onboarding.PermissionActivity
import com.example.pace.ui.onboarding.UserSetupActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SplashActivity : AppCompatActivity() {

    @Inject
    lateinit var authDataStore: AuthDataStore

    @Inject
    lateinit var authControllerRepository: AuthControllerRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val lottieView = findViewById<LottieAnimationView>(R.id.lottieAnimationView)
        lottieView.speed = -0.6f

        lottieView.addAnimatorListener(object : Animator.AnimatorListener {
            override fun onAnimationEnd(animation: Animator) {
                lifecycleScope.launch {
                    routeFromStoredAuth()
                }
            }

            override fun onAnimationStart(animation: Animator) = Unit

            override fun onAnimationCancel(animation: Animator) = Unit

            override fun onAnimationRepeat(animation: Animator) = Unit
        })
    }

    private suspend fun routeFromStoredAuth() {
        val accessToken = authDataStore.getAccessToken()
        val tempToken = authDataStore.getTempToken()

        if (accessToken.isNullOrBlank()) {
            if (!tempToken.isNullOrBlank()) {
                if (hasRequiredPermissions()) {
                    navigateToUserSetup()
                } else {
                    navigateToPermission()
                }
            } else {
                navigateToOnboarding()
            }
            return
        }

        val refreshToken = authDataStore.getRefreshToken()
        if (refreshToken.isNullOrBlank()) {
            authDataStore.clearAllData()
            navigateToOnboarding()
            return
        }

        when (val response = authControllerRepository.reissueToken(refreshToken, ReissueRequest(refreshToken))) {
            is DefaultResponse.Success -> {
                val tokens = response.data
                if (tokens != null) {
                    authDataStore.saveTokens(tokens.accessToken, tokens.refreshToken)
                    if (hasRequiredPermissions()) {
                        navigateToMain()
                    } else {
                        navigateToPermission()
                    }
                } else {
                    authDataStore.clearAllData()
                    navigateToOnboarding()
                }
            }

            is DefaultResponse.Failure -> {
                authDataStore.clearAllData()
                navigateToOnboarding()
            }
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        val locationGranted = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val readCalendarGranted = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED

        val writeCalendarGranted = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED

        return locationGranted && readCalendarGranted && writeCalendarGranted
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        applyTransition()
        finish()
    }

    private fun navigateToPermission() {
        startActivity(Intent(this, PermissionActivity::class.java))
        applyTransition()
        finish()
    }

    private fun navigateToUserSetup() {
        startActivity(Intent(this, UserSetupActivity::class.java))
        applyTransition()
        finish()
    }

    // 온보딩 화면으로 이동하는 함수
    private fun navigateToOnboarding() {
        startActivity(Intent(this, OnboardingActivity::class.java))
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
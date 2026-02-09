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

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 묻지도 따지지도 않고 바로 메인으로 이동
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()

        setContentView(R.layout.activity_splash)
        val intent = Intent(this@SplashActivity, MainActivity::class.java)
        startActivity(intent)
        finish()

        val lottieView = findViewById<LottieAnimationView>(R.id.lottieAnimationView)
        lottieView.addAnimatorListener(object : Animator.AnimatorListener {

            override fun onAnimationEnd(animation: Animator) {
                val intent = Intent(this@SplashActivity, OnboardingActivity::class.java)
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                startActivity(intent)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
                } else {
                    @Suppress("DEPRECATION")
                    overridePendingTransition(0, 0)
                }
                finish()
            }

            override fun onAnimationStart(p0: Animator) {}
            override fun onAnimationCancel(p0: Animator) {}
            override fun onAnimationRepeat(p0: Animator) {}
        })
    }
}
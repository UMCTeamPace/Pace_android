package com.example.pace.ui.onboarding

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.pace.R
import com.example.pace.databinding.ActivityOnboardingBinding

class OnboardingActivity : AppCompatActivity() {
    private lateinit var binding: ActivityOnboardingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, OnboardingFragment())
                .commit()
        }
    }

    // [추가] 로그인 성공 시 호출하여 권한 설정 화면으로 이동
    fun moveToPermissionStep() {
        val intent = Intent(this, PermissionActivity::class.java)
        startActivity(intent)
        finish() // 온보딩 액티비티 종료
    }
}
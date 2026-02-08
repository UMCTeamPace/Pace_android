package com.example.pace.ui.onboarding

import android.content.Intent
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

        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 액티비티가 처음 생성될 때만 OnboardingFragment를 붙여줍니다.
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, OnboardingFragment())
                .commit()
        }
    }
    fun moveToPermissionStep() {
        val intent = Intent(this, PermissionActivity::class.java)
        startActivity(intent)
        finish() // 온보딩 액티비티 종료
    }
}
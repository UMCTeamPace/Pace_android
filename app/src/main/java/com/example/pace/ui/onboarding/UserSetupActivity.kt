package com.example.pace.ui.onboarding

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.pace.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class UserSetupActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_setup)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.user_setup_container, AppSettingPagerFragment())
                .commit()
        }
    }
}
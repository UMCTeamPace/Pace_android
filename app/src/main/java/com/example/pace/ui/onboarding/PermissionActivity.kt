package com.example.pace.ui.onboarding

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.pace.R
import dagger.hilt.android.AndroidEntryPoint // 추가

@AndroidEntryPoint
class PermissionActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_permission)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.permission_container, PermissionFragment())
                .commit()
        }
    }
}
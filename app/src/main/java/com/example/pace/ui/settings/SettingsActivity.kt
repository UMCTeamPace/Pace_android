package com.example.pace.ui.settings

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.pace.R
import com.example.pace.databinding.ActivitySettingsBinding

class SettingsActivity: AppCompatActivity() {
    lateinit var binding: ActivitySettingsBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.settingsBackIv.setOnClickListener {
            onBackPressed()
        }

        supportFragmentManager.beginTransaction()
            .replace(R.id.settings_fcv, SettingFragment())
            .commit()
    }
}
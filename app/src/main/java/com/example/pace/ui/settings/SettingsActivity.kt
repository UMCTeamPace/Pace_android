package com.example.pace.ui.settings

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
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
        binding.settingsCalendarIv.setOnClickListener {
            startActivity(Intent(this, SyncWithCalendarActivity::class.java))
        }
        binding.settingsReviewLl.setOnClickListener {
            val reviewDialog = ReviewDialog(this)
            reviewDialog.show()
        }
        binding.settingsSignoutLl.setOnClickListener {
            val signoutDialog = SignoutDialog(this)
            signoutDialog.show()
        }
    }
}
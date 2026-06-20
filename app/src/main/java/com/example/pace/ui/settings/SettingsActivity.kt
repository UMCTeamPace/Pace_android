package com.example.pace.ui.settings

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.pace.R
import com.example.pace.data.datasource.AuthDataStore
import com.example.pace.data.repository.repository.SettingsRepository
import com.example.pace.data.util.syncMemberSettingsIfNeeded
import com.example.pace.databinding.ActivitySettingsBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SettingsActivity: AppCompatActivity() {
    @javax.inject.Inject
    lateinit var authDataStore: AuthDataStore

    @javax.inject.Inject
    lateinit var settingsRepository: SettingsRepository

    lateinit var binding: ActivitySettingsBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lifecycleScope.launch {
            syncMemberSettingsIfNeeded(
                authDataStore = authDataStore,
                settingsRepository = settingsRepository,
                source = "SettingsActivity",
                force = true
            )
        }

        binding.settingsBackIv.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        supportFragmentManager.beginTransaction()
            .replace(R.id.settings_fcv, SettingFragment())
            .commit()
    }
}

package com.example.pace.ui.main.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.pace.data.datasource.AuthDataStore
import com.example.pace.data.repository.repository.ScheduleRepository
import com.example.pace.data.repository.repository.SettingsRepository

class ScheduleViewModelFactory(
    private val repository: ScheduleRepository,
    private val authDataStore: AuthDataStore,
    private val settingsRepository: SettingsRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ScheduleViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ScheduleViewModel(repository, authDataStore, settingsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
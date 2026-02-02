package com.example.pace.ui.main.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pace.data.model.Schedule
import com.example.pace.data.repository.ScheduleRepository
import kotlinx.coroutines.launch

class ScheduleViewModel(private val repository: ScheduleRepository) : ViewModel() {

    val allSchedules = repository.allSchedules
    val calendarEvents = repository.calendarEvents

    init {
        // Trigger initial data refresh when the ViewModel is created
        refreshSchedules()
    }

    fun refreshSchedules() {
        viewModelScope.launch {
            repository.refreshSchedules()
        }
    }

    fun updateSchedule(schedule: Schedule) {
        viewModelScope.launch {
            repository.updateSchedule(schedule)
        }
    }
}

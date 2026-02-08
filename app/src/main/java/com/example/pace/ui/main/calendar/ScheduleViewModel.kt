package com.example.pace.ui.main.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pace.data.model.Schedule
import com.example.pace.data.repository.ScheduleRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ScheduleViewModel(private val repository: ScheduleRepository) : ViewModel() {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    // [핵심 수정] 리스트를 Map<LocalDate, List<Schedule>> 형태로 가공
    // 레포지토리에서 전개(Expand)된 데이터를 받아와서 날짜별로 그룹화합니다.
    val scheduleMap: StateFlow<Map<LocalDate, List<Schedule>>> = repository.allSchedules
        .map { schedules ->
            schedules.groupBy { schedule ->
                LocalDate.parse(schedule.startDate, dateFormatter)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    // 기존의 raw 리스트가 필요한 경우를 위해 유지 (선택 사항)
    val allSchedules = repository.allSchedules
    val calendarEvents = repository.calendarEvents

    init {
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
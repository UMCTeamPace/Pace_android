package com.example.pace.ui.main.calendar

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pace.data.datasource.AuthDataStore
import com.example.pace.data.model.Schedule
import com.example.pace.data.repository.repository.ScheduleRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class ScheduleViewModel @Inject constructor(
    private val repository: ScheduleRepository,
    private val authDataStore: AuthDataStore
) : ViewModel() {

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
        android.util.Log.d("API_TEST", "ViewModel: refreshSchedules() 진입")

        viewModelScope.launch {
            try {
                // 2. AuthDataStore에서 저장된 토큰을 가져옵니다.
                val token = authDataStore.getAccessToken()
                Log.d("API_TEST", "ViewModel: 불러온 토큰 -> $token")

                if (token != null) {
                    // 3. 불러온 토큰을 사용하여 API 호출
                    val response = repository.getScheduleList(token,"2026-02-01","2026-02-28",null,null)
                    Log.d("API_TEST", "ViewModel: 리포지토리 호출 완료 -> $response")
                } else {
                    Log.e("API_TEST", "ViewModel: 저장된 토큰이 없습니다. 로그인이 필요합니다.")
                }

            } catch (e: Exception) {
                Log.e("API_TEST", "ViewModel: 에러 발생 -> ${e.message}")
            }
        }
    }

    fun updateSchedule(schedule: Schedule) {
        viewModelScope.launch {
            repository.updateSchedule(schedule)
        }
    }
}
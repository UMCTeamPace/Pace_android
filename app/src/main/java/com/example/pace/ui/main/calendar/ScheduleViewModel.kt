package com.example.pace.ui.main.calendar

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pace.data.datasource.AuthDataStore
import com.example.pace.data.model.Schedule
import com.example.pace.data.model.UserSettingsEntity
import com.example.pace.data.model.request.CreateScheduleRequest
import com.example.pace.data.model.request.PlaceRequest
import com.example.pace.data.model.request.ReminderRequest
import com.example.pace.data.model.response.ScheduleDetailResponse
import com.example.pace.data.repository.repository.ScheduleRepository
import com.example.pace.data.repository.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val repository: ScheduleRepository,
    private val authDataStore: AuthDataStore,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val rangeFormatter = DateTimeFormatter.ofPattern("yyyy. MM. dd")

    private var lastQuery: String = ""
    private var searchStartDate = LocalDate.now().minusYears(1)
    private var searchEndDate = LocalDate.now().plusYears(1)

    private val _searchRangeText = MutableStateFlow("")
    val searchRangeText: StateFlow<String> = _searchRangeText

    private val _filterColors = MutableStateFlow<Set<String>>(emptySet())
    val filterColors: StateFlow<Set<String>> = _filterColors

    private val _filterIncludeRoute = MutableStateFlow(true)
    val filterIncludeRoute: StateFlow<Boolean> = _filterIncludeRoute

    private val _searchResults = MutableStateFlow<List<Schedule>>(emptyList())
    val searchResults: StateFlow<List<Schedule>> = _searchResults

    private val _selectedDate = MutableStateFlow<LocalDate>(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate

    private val _isEditMode = MutableStateFlow(false)
    val isEditMode: StateFlow<Boolean> = _isEditMode

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds

    private val _createScheduleEvent = MutableStateFlow<Boolean?>(null)
    val createScheduleEvent: StateFlow<Boolean?> = _createScheduleEvent

    // 상세 정보 상태
    private val _scheduleDetailInfo = MutableStateFlow<ScheduleDetailResponse?>(null)
    val scheduleDetailInfo: StateFlow<ScheduleDetailResponse?> = _scheduleDetailInfo

    val userSettings: StateFlow<UserSettingsEntity?> = settingsRepository.getUserSettings()
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val usedColors: StateFlow<List<String>> = repository.getUsedColors()
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        updateRangeText()
        refreshSchedules()
    }

    // --- 동기화 로직 ---
    fun refreshSchedules() {
        viewModelScope.launch {
            try {
                val token = authDataStore.getAccessToken() ?: return@launch
                val fullToken = if (token.startsWith("Bearer ")) token else "Bearer $token"

                // 1. 서버 일정 동기화
                repository.getScheduleList(fullToken, "2026-02-01", "2026-02-28", null, null)

                // 2. 시스템 삭제분 정리
                withContext(Dispatchers.IO) {
                    repository.cleanUpSystemDeletedSchedules()
                }

                // 3. 기기 캘린더 최신화
                withContext(Dispatchers.IO) {
                    repository.refreshSchedules()
                }
            } catch (e: Exception) {
                Log.e("API_SYNC", "동기화 실패: ${e.message}")
            }
        }
    }

    // --- 상세 조회 ---
    fun getScheduleDetail(id: Long) {
        viewModelScope.launch {
            val token = authDataStore.getAccessToken()
            if (token != null) {
                val response = repository.getScheduleDetail(token, id)
                _scheduleDetailInfo.value = response.result
            }
        }
    }

    // --- 검색 및 필터 ---
    fun searchSchedules(query: String) {
        lastQuery = query
        val dbQuery = if (query.isBlank()) "%" else "%$query%"
        viewModelScope.launch(Dispatchers.IO) {
            val results = repository.searchSchedules(
                query = dbQuery,
                colors = _filterColors.value,
                includeRoute = _filterIncludeRoute.value,
                startDate = searchStartDate.format(dateFormatter),
                endDate = searchEndDate.format(dateFormatter)
            )
            _searchResults.value = results
        }
    }

    fun searchWithCurrentQuery() { searchSchedules(lastQuery) }

    fun setIncludeRouteFilter(include: Boolean) {
        _filterIncludeRoute.value = include
        searchSchedules(lastQuery)
    }

    fun toggleFilterColor(color: String) {
        val current = _filterColors.value.toMutableSet()
        if (current.contains(color)) current.remove(color) else current.add(color)
        _filterColors.value = current
        searchSchedules(lastQuery)
    }

    fun clearSearch() {
        lastQuery = ""
        _searchResults.value = emptyList()
    }

    fun expandSearchRange() {
        searchStartDate = searchStartDate.minusMonths(6)
        searchEndDate = searchEndDate.plusMonths(6)
        updateRangeText()
        searchSchedules(lastQuery)
    }

    private fun updateRangeText() {
        _searchRangeText.value = "${searchStartDate.format(rangeFormatter)} ~ ${searchEndDate.format(rangeFormatter)}"
    }

    // --- 생성 및 수정 (복구 완료) ---
    fun createSchedule(request: CreateScheduleRequest, placeId: String? = null, calendarId: Long? = null) {
        viewModelScope.launch {
            try {
                val token = authDataStore.getAccessToken() ?: return@launch
                val fullToken = if (token.startsWith("Bearer ")) token else "Bearer $token"
                val response = repository.createSchedule(fullToken, request, placeId, calendarId)
                if (response.isSuccess) {
                    withContext(Dispatchers.IO) { repository.refreshSchedules() }
                    _createScheduleEvent.value = true
                } else {
                    _createScheduleEvent.value = false
                }
            } catch (e: Exception) {
                _createScheduleEvent.value = false
            }
        }
    }

    fun createScheduleWithDefaultSettings(
        title: String, memo: String?, isAllDay: Boolean,
        startDate: String, startTime: String?, endDate: String, endTime: String?,
        place: PlaceRequest?, repeatRule: String? = null, placeId: String? = null,
        customAlarms: List<Int>? = null, calendarId: Long? = null
    ) {
        val settings = userSettings.value
        val reminders = mutableListOf<ReminderRequest>()
        val alarmList = customAlarms ?: settings?.scheduleAlarms ?: emptyList()

        alarmList.forEach { minutes ->
            reminders.add(ReminderRequest(reminderType = "SCHEDULE", minutesBefore = minutes))
        }
        settings?.departureAlarms?.forEach { minutes ->
            reminders.add(ReminderRequest(reminderType = "DEPARTURE", minutesBefore = minutes))
        }

        val request = CreateScheduleRequest(
            title = title, isAllDay = isAllDay, startDate = startDate, endDate = endDate,
            startTime = startTime, endTime = endTime, memo = memo,
            isPathIncluded = (place != null), isRepeat = repeatRule != null,
            repeatInfo = null, place = place, reminders = reminders, route = null
        )
        createSchedule(request, placeId, calendarId)
    }

    fun updateSchedule(schedule: Schedule) {
        viewModelScope.launch(Dispatchers.IO) { repository.updateSchedule(schedule) }
    }

    // --- 기타 유틸리티 ---
    val scheduleMap: StateFlow<Map<LocalDate, List<Schedule>>> = repository.allSchedules
        .map { schedules -> schedules.groupBy { LocalDate.parse(it.startDate, dateFormatter) } }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun setSelectedDate(date: LocalDate) { _selectedDate.value = date }

    fun getCalendarNameById(calendarId: Long): String {
        return try { repository.getCalendarName(calendarId) ?: "내 일정" } catch (e: Exception) { "내 일정" }
    }

    fun setEditMode(enabled: Boolean) {
        _isEditMode.value = enabled
        if (!enabled) _selectedIds.value = emptySet()
    }

    fun toggleSelection(id: Long) {
        val current = _selectedIds.value.toMutableSet()
        if (current.contains(id)) current.remove(id) else current.add(id)
        _selectedIds.value = current
    }

    fun resetCreateEvent() { _createScheduleEvent.value = null }
}

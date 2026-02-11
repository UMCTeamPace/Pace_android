package com.example.pace.ui.main.calendar

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pace.data.api.AuthControllerService
import com.example.pace.data.datasource.AuthDataStore
import com.example.pace.data.model.Schedule
import com.example.pace.data.model.request.CreateScheduleRequest
import com.example.pace.data.model.response.RouteInfo
import com.example.pace.data.repository.repository.ScheduleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers // 추가
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn // 추가
import kotlinx.coroutines.withContext
import com.example.pace.data.repository.repository.SettingsRepository // 세팅 레포지토리 임포트
import com.example.pace.data.model.UserSettingsEntity // 유저 설정 모델
import com.example.pace.data.model.request.PlaceRequest
import com.example.pace.data.model.request.ReminderRequest
import com.example.pace.data.model.request.RepeatInfo
import com.example.pace.data.model.request.RouteRequest

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

    // 사용자 설정 (온보딩 시 설정한 알람 등)
    val userSettings: StateFlow<UserSettingsEntity?> = settingsRepository.getUserSettings()
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // 사용 중인 색상 리스트
    val usedColors: StateFlow<List<String>> = repository.getUsedColors()
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        updateRangeText()
        refreshSchedules()
    }

    // --- 동기화 로직 (Clean-up 포함) ---
    fun refreshSchedules() {
        android.util.Log.d("API_SYNC", "통합 동기화 프로세스 시작")
        viewModelScope.launch {
            try {
                val token = authDataStore.getAccessToken() ?: return@launch
                val fullToken = if (token.startsWith("Bearer ")) token else "Bearer $token"

                // 1. 서버 일정 동기화
                repository.getScheduleList(fullToken, "2026-02-01", "2026-02-28", null, null)

                // 2. [중요] 시스템 삭제분 정리 (유령 일정 제거)
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

    // --- 검색 및 필터 로직 ---
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

    fun toggleFilterColor(color: String) {
        val current = _filterColors.value.toMutableSet()
        if (current.contains(color)) current.remove(color) else current.add(color)
        _filterColors.value = current
        searchSchedules(lastQuery)
    }

    // --- 생성 로직 ---
    fun createSchedule(request: CreateScheduleRequest, placeId: String? = null, calendarId: Long? = null) {
        viewModelScope.launch {
            try {
                val token = authDataStore.getAccessToken()
                val fullToken = token?.let { if (it.startsWith("Bearer ")) it else "Bearer $it" }

                // 경로 일정인데 토큰이 없는 경우 차단
                if (request.route != null && fullToken == null) {
                    _createScheduleEvent.value = false
                    return@launch
                }

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

    // --- 편집 모드 및 삭제 ---
    fun setEditMode(enabled: Boolean) {
        _isEditMode.value = enabled
        if (!enabled) _selectedIds.value = emptySet()
    }

    fun toggleSelection(id: Long) {
        val current = _selectedIds.value.toMutableSet()
        if (current.contains(id)) current.remove(id) else current.add(id)
        _selectedIds.value = current
    }

    fun deleteSelected() {
        viewModelScope.launch(Dispatchers.IO) {
            val idsToDelete = _selectedIds.value.toList()
            // 서버/로컬 삭제 로직 구현 필요 (Repository에 deleteSchedules(ids) 추가 권장)
            // repository.deleteLocalSchedules(idsToDelete)

            withContext(Dispatchers.Main) {
                setEditMode(false)
                refreshSchedules() // 삭제 후 목록 갱신
            }
        }
    }

    private fun updateRangeText() {
        _searchRangeText.value = "${searchStartDate.format(rangeFormatter)} ~ ${searchEndDate.format(rangeFormatter)}"
    }

    fun resetCreateEvent() { _createScheduleEvent.value = null }

    fun getCalendarNameById(calendarId: Long): String {
        return try {
            repository.getCalendarName(calendarId) ?: "내 일정"
        } catch (e: Exception) {
            "내 일정"
        }
    }
    fun createScheduleWithDefaultSettings(
        title: String,
        memo: String?,
        isAllDay: Boolean,
        startDate: String,
        startTime: String?,
        endDate: String,
        endTime: String?,
        place: PlaceRequest?,
        repeatRule: String? = null,
        placeId: String? = null,
        customAlarms: List<Int>? = null,
        calendarId: Long? = null
    ) {
        val settings = userSettings.value
        val reminders = mutableListOf<ReminderRequest>()

        // 1. 알람 리스트 결정 (사용자 선택 우선 -> 없으면 설정값)
        val alarmList = customAlarms ?: settings?.scheduleAlarms ?: emptyList()

        alarmList.forEach { minutes ->
            reminders.add(ReminderRequest(reminderType = "SCHEDULE", minutesBefore = minutes))
        }

        // 2. 출발 알람 설정이 있다면 추가
        settings?.departureAlarms?.forEach { minutes ->
            reminders.add(ReminderRequest(reminderType = "DEPARTURE", minutesBefore = minutes))
        }

        val request = CreateScheduleRequest(
            title = title,
            isAllDay = isAllDay,
            startDate = startDate,
            endDate = endDate,
            startTime = startTime,
            endTime = endTime,
            memo = memo,
            isPathIncluded = (place != null),
            isRepeat = repeatRule != null,
            repeatInfo = null,
            place = place,
            reminders = reminders,
            route = null, // 일반 일정이므로 route는 null
        )

        // 기존에 만들어둔 createSchedule 호출
        createSchedule(request, placeId, calendarId)
    }

    val scheduleMap: StateFlow<Map<LocalDate, List<Schedule>>> = repository.allSchedules
        .map { schedules ->
            schedules.groupBy { schedule ->
                LocalDate.parse(schedule.startDate, dateFormatter)
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    // 2. CalendarPageFragment.kt:100 라인 에러 해결 (날짜 선택 함수)
    fun setSelectedDate(date: LocalDate) {
        _selectedDate.value = date
    }
    fun updateSchedule(schedule: Schedule) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateSchedule(schedule)
        }
    }
    fun searchWithCurrentQuery() {
        searchSchedules(lastQuery)
    }

    // 2. SearchFilterBottomSheet.kt:98 라인 에러 해결
    // 필터에서 '경로 일정 포함' 여부를 설정하고 바로 검색을 갱신합니다.
    fun setIncludeRouteFilter(include: Boolean) {
        _filterIncludeRoute.value = include
        searchSchedules(lastQuery)
    }

    // SearchFragment.kt:202 라인 에러 해결
    fun expandSearchRange() {
        // 검색 시작 날짜는 6개월 전으로, 종료 날짜는 6개월 후로 확장
        searchStartDate = searchStartDate.minusMonths(6)
        searchEndDate = searchEndDate.plusMonths(6)

        // UI에 표시되는 날짜 범위 텍스트 업데이트
        updateRangeText()

        // 확장된 범위로 현재 검색어 다시 검색
        searchSchedules(lastQuery)

        android.util.Log.d("SearchFlow", "검색 범위 확장됨: ${searchStartDate} ~ ${searchEndDate}")
    }

    // SearchFragment.kt:127 라인 에러 해결
    fun clearSearch() {
        // 1. 마지막 검색어 변수 초기화
        lastQuery = ""

        // 2. 검색 결과 리스트 비우기
        _searchResults.value = emptyList()

        android.util.Log.d("SearchFlow", "검색 데이터 초기화 완료")
    }

}
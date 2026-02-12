package com.example.pace.ui.main.calendar

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pace.data.api.AuthControllerService
import com.example.pace.data.datasource.AuthDataStore
import com.example.pace.data.model.Schedule
import com.example.pace.data.model.UserSettingsEntity
import com.example.pace.data.model.request.CreateScheduleRequest
import com.example.pace.data.model.request.PlaceRequest
import com.example.pace.data.model.request.ReminderRequest
import com.example.pace.data.model.request.RepeatInfo
import com.example.pace.data.model.response.RouteInfo
import com.example.pace.data.model.response.ScheduleDetailResponse
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
import com.example.pace.data.repository.repository.SettingsRepository

import javax.inject.Inject


@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val repository: ScheduleRepository,
    private val authDataStore: AuthDataStore,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val rangeFormatter = DateTimeFormatter.ofPattern("yyyy. MM. dd")

    // 마지막 검색어 저장 (필터 변경 시 재사용)
    private var lastQuery: String = ""

    // 1. 검색 날짜 범위 상태 (초기값: 오늘 기준 앞뒤 1년)
    private var searchStartDate = LocalDate.now().minusYears(1)
    private var searchEndDate = LocalDate.now().plusYears(1)

    // UI에 표시할 날짜 범위 텍스트
    private val _searchRangeText = MutableStateFlow("")
    val searchRangeText: StateFlow<String> = _searchRangeText

    // 2. 필터 상태 (StateFlow로 노출해야 바텀시트에서 체크 표시를 유지할 수 있음)
    private val _filterColor = MutableStateFlow<String?>(null)
    val filterColor: StateFlow<String?> = _filterColor

    private val _filterIncludeRoute = MutableStateFlow(true)
    val filterIncludeRoute: StateFlow<Boolean> = _filterIncludeRoute

    // 검색 결과 상태
    private val _searchResults = MutableStateFlow<List<Schedule>>(emptyList())
    val searchResults: StateFlow<List<Schedule>> = _searchResults

    // 선택된 날짜 (캘린더용)
    private val _selectedDate = MutableStateFlow<LocalDate>(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate

    private val _filterColors = MutableStateFlow<Set<String>>(emptySet())
    val filterColors: StateFlow<Set<String>> = _filterColors

    private val _isEditMode = MutableStateFlow(false)
    val isEditMode: StateFlow<Boolean> = _isEditMode


    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds

    private val _createScheduleEvent = MutableStateFlow<Boolean?>(null)
    val createScheduleEvent: StateFlow<Boolean?> = _createScheduleEvent

    // 일정 상세 조회
    private val _scheduleDetailInfo = MutableStateFlow<ScheduleDetailResponse?>(null)
    val scheduleDetailInfo: StateFlow<ScheduleDetailResponse?> = _scheduleDetailInfo

    fun setEditMode(enabled: Boolean) {
        _isEditMode.value = enabled
        if (!enabled) _selectedIds.value = emptySet() // 편집 모드 종료 시 선택 초기화
    }

    fun toggleSelection(id: Long) {
        val current = _selectedIds.value.toMutableSet()
        if (current.contains(id)) current.remove(id) else current.add(id)
        _selectedIds.value = current
    }

    fun deleteSelected() {
        viewModelScope.launch(Dispatchers.IO) {
            // 실제 삭제 로직 (repository 호출 등)
            val idsToDelete = _selectedIds.value.toList()
            // repository.deleteSchedules(idsToDelete) // 이 함수는 repository에 있어야 합니다.

            withContext(Dispatchers.Main) {
                setEditMode(false) // 삭제 후 편집 모드 종료
            }
        }
    }
    val usedColors: StateFlow<List<String>> = repository.getUsedColors()
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val userSettings: StateFlow<UserSettingsEntity?> = settingsRepository.getUserSettings()
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    init {
        updateRangeText()
        refreshSchedules()
    }


    fun searchWithCurrentQuery() {
        searchSchedules(lastQuery)
    }

    // 날짜 텍스트 업데이트 함수
    private fun updateRangeText() {
        _searchRangeText.value = "${searchStartDate.format(rangeFormatter)} ~ ${searchEndDate.format(rangeFormatter)}"
    }

    // 3. 범위 넓혀 재검색 (6개월씩 확장)
    fun expandSearchRange() {
        searchStartDate = searchStartDate.minusMonths(6)
        searchEndDate = searchEndDate.plusMonths(6)
        updateRangeText()
        searchSchedules(lastQuery) // 확장된 범위로 다시 검색
    }

    fun setSelectedDate(date: LocalDate) {
        _selectedDate.value = date
    }

    // [수정] 색상 토글 함수
    fun toggleFilterColor(color: String) {
        val current = _filterColors.value.toMutableSet()
        if (current.contains(color)) {
            current.remove(color)
        } else {
            current.add(color)
        }
        _filterColors.value = current

        // 로그 4: 색상 변경 확인
        android.util.Log.d("SearchFlow", "색상 필터 변경됨: ${_filterColors.value}")

        searchSchedules(lastQuery)
    }

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

    fun setFilterColor(color: String?) {
        _filterColor.value = color
        searchSchedules(lastQuery)
    }

    fun setIncludeRouteFilter(include: Boolean) {
        _filterIncludeRoute.value = include
        searchSchedules(lastQuery)
    }

    // --- 기존 리스트 가공 및 기타 함수들 ---
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

    // 기존의 raw 리스트가 필요한 경우를 위해 유지 (선택 사항)
    val allSchedules = repository.allSchedules
    val calendarEvents = repository.calendarEvents


    fun refreshSchedules() {
        viewModelScope.launch {
            try {
                val token = authDataStore.getAccessToken() ?: return@launch
                val fullToken = if (token.startsWith("Bearer ")) token else "Bearer $token"

                // 1. 서버 데이터를 최신화 (서버->로컬)
                repository.getScheduleList(fullToken, "2026-02-01", "2026-02-28", null, null)

                // 2. [수정] 찌꺼기 청소와 기기 데이터 로드를 순차적으로 실행
                withContext(Dispatchers.IO) {
                    // 먼저 시스템 캘린더에서 실제 삭제된 녀석들을 DB에서 제거
                    repository.cleanUpSystemDeletedSchedules()

                    // 그 다음 현재 시스템 캘린더에 남아있는 최신본을 DB에 덮어쓰기
                    repository.refreshSchedules()
                }

                // 3. 만약 검색 중이었다면 검색 결과도 리프레시
                if (lastQuery.isNotEmpty()) {
                    searchSchedules(lastQuery)
                }

            } catch (e: Exception) {
                Log.e("API_SYNC", "동기화 실패: ${e.message}")
            }
        }
    }

    fun updateSchedule(schedule: Schedule) {
        viewModelScope.launch(Dispatchers.IO) { repository.updateSchedule(schedule) }
    }
    fun clearSearch() {
        // 1. 마지막 검색어 변수도 반드시 비워야 합니다. (가장 중요!)
        lastQuery = ""

        // 2. 검색 결과 리스트 비우기
        _searchResults.value = emptyList()

        android.util.Log.d("SearchFlow", "ScheduleViewModel: 검색어 및 데이터 완전 초기화 완료")
    }

    fun createSchedule(
        request: CreateScheduleRequest,
        placeId: String? = null,
        calendarId: Long? = null,
        selectedColor: Int?
    ) {
        viewModelScope.launch {
            try {
                // 1. 토큰 준비
                val token = authDataStore.getAccessToken() ?: ""
                val fullToken = if (token.isNotEmpty() && !token.startsWith("Bearer ")) "Bearer $token" else token

                // 2. Repository 호출 (이제 Result가 아닌 RawDefaultResponse를 반환함)
                val response = repository.createSchedule(
                    accessToken = if (token.isEmpty()) null else fullToken,
                    request = request,
                    placeId = placeId,
                    calendarId = calendarId,
                    selectedColor = selectedColor // 인자 전달
                )

                // 3. 결과 처리 (response.isSuccess 직접 확인)
                if (response.isSuccess) {
                    withContext(Dispatchers.IO) {
                        repository.refreshSchedules()
                    }
                    _createScheduleEvent.value = true
                } else {
                    Log.e("ScheduleViewModel", "저장 실패: ${response.message}")
                    _createScheduleEvent.value = false
                }
            } catch (e: Exception) {
                Log.e("ScheduleViewModel", "예외 발생: ${e.message}")
                _createScheduleEvent.value = false
            }
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
        repeatInfo: RepeatInfo? = null,
        placeId: String? = null,
        customAlarms: List<Int>? = null,
        calendarId: Long? = null,
        selectedColor: Int // 스펠링 수정: selecetedColor -> selectedColor
    ) {
        val settings = userSettings.value
        val reminders = mutableListOf<ReminderRequest>()

        // 1. 알림 설정 로직 (기존 유지)
        val alarmList = customAlarms ?: settings?.scheduleAlarms ?: emptyList()
        alarmList.forEach { minutes ->
            reminders.add(ReminderRequest(reminderType = "SCHEDULE", minutesBefore = minutes))
        }
        settings?.departureAlarms?.forEach { minutes ->
            reminders.add(ReminderRequest(reminderType = "DEPARTURE", minutesBefore = minutes))
        }

        // 2. Request 객체 생성
        val request = CreateScheduleRequest(
            title = title,
            isAllDay = isAllDay,
            startDate = startDate,
            endDate = endDate,
            startTime = startTime,
            endTime = endTime,
            memo = memo,
            isPathIncluded = (place != null),
            isRepeat = (repeatInfo != null),
            repeatInfo = repeatInfo,
            place = place,
            reminders = reminders,
            route = null
        )

        // 3. 위에서 정의한 createSchedule 함수 호출
        createSchedule(
            request = request,
            placeId = placeId,
            calendarId = calendarId,
            selectedColor = selectedColor // 색상 전달
        )
    }
    // 이벤트 초기화 함수 (연속 호출 방지)
    fun resetCreateEvent() {
        _createScheduleEvent.value = null
    }

    fun getScheduleDetail(id: Long){
        viewModelScope.launch {
            val token = authDataStore.getAccessToken()
            if(token != null){
                val response = repository.getScheduleDetail(token, id)
                _scheduleDetailInfo.value = response.result
            }
        }
    }

    fun getCalendarNameById(calendarId: Long): String {
        return try {
            // 레포지토리에 이 함수가 정의되어 있어야 합니다.
            repository.getCalendarName(calendarId) ?: "내 일정"
        } catch (e: Exception) {
            "내 일정"
        }
    }

}
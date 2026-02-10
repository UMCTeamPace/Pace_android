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

import javax.inject.Inject


@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val repository: ScheduleRepository,
    private val authDataStore: AuthDataStore
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

    private val _scheduleRouteInfo= MutableStateFlow<RouteInfo?>(null)
    val scheduleRouteInfo = _scheduleRouteInfo.value

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

        // [수정] 검색어가 비어있어도 return하지 않습니다.
        // 대신 DB가 "모든 텍스트"를 찾을 수 있도록 와일드카드를 준비합니다.
        val dbQuery = if (query.isBlank()) "%" else "%$query%"

        android.util.Log.d("SearchFlow", "검색 실행 - 입력값: '$query', DB쿼리: '$dbQuery'")

        viewModelScope.launch(Dispatchers.IO) {
            val currentColors = _filterColors.value
            val includeRoute = _filterIncludeRoute.value

            val results = repository.searchSchedules(
                query = dbQuery, // %를 포함한 쿼리 전달
                colors = currentColors,
                includeRoute = includeRoute,
                startDate = searchStartDate.format(dateFormatter),
                endDate = searchEndDate.format(dateFormatter)
            )

            android.util.Log.d("SearchFlow", "최종 검색 결과 개수: ${results.size}")
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
        viewModelScope.launch(Dispatchers.IO) { repository.refreshSchedules() }
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

    fun createSchedule(request: CreateScheduleRequest) {
        viewModelScope.launch {
            try {
                // 1. 토큰 가져오기
                val token = authDataStore.getAccessToken()
                if (token != null) {
                    val fullToken = if (token.startsWith("Bearer ")) token else "Bearer $token"

                    // 2. 리포지토리를 통해 서버 전송
                    val response = repository.createSchedule(fullToken, request)

                    // [수정 완료] success -> isSuccess / data -> result
                    if (response.isSuccess) {
                        // response.result는 CreateScheduleResponse 객체입니다.
                        Log.d("API_CREATE", "일정 생성 성공: ${response.result}")

                        // 3. 로컬 DB(Room) 새로고침 및 성공 알림
                        repository.refreshSchedules()
                        _createScheduleEvent.value = true
                    } else {
                        // 서버에서 내려준 에러 메시지 출력
                        Log.e("API_CREATE", "일정 생성 실패: ${response.message}")
                        _createScheduleEvent.value = false
                    }
                } else {
                    Log.e("API_CREATE", "인증 토큰이 없습니다.")
                    _createScheduleEvent.value = false
                }
            } catch (e: Exception) {
                Log.e("API_CREATE", "네트워크 에러 발생: ${e.message}")
                _createScheduleEvent.value = false
            }
        }
    }

    // 이벤트 초기화 함수 (연속 호출 방지)
    fun resetCreateEvent() {
        _createScheduleEvent.value = null
    }
}
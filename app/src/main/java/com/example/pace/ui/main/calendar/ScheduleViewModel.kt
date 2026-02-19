package com.example.pace.ui.main.calendar

import android.content.Context
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
import com.example.pace.data.model.request.RouteRequest
import com.example.pace.data.model.request.UpdateScheduleEditRouteRequest
import com.example.pace.data.model.request.UpdateScheduleRequest
import com.example.pace.data.model.request.UpdateScheduleRouteRequest
import com.example.pace.data.model.response.RawDefaultResponse
import com.example.pace.data.model.response.RouteInfo
import com.example.pace.data.model.response.ScheduleDetailResponse
import com.example.pace.data.model.response.UpdateScheduleRouteResponse
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
import com.google.gson.Gson
import dagger.hilt.android.internal.Contexts.getApplication
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.firstOrNull

import javax.inject.Inject
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull // searchSchedules에서 필요
import kotlinx.coroutines.flow.update

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val repository: ScheduleRepository,
    private val authDataStore: AuthDataStore,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context
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
    private val _scheduleDetailInfoMap = MutableStateFlow<Map<Long, ScheduleDetailResponse>>(emptyMap())
    val scheduleDetailInfoMap = _scheduleDetailInfoMap

    private val _updateScheduleEvent = MutableStateFlow<Boolean?>(null)
    val updateScheduleEvent: StateFlow<Boolean?> = _updateScheduleEvent

    private val _routeDetails = MutableStateFlow<Map<Long, RouteInfo>>(emptyMap())

    // 2. Fragment에서 관찰할 Public StateFlow
    val routeDetails: StateFlow<Map<Long, RouteInfo>> = _routeDetails

    // 뷰모델 내부
    private val _scheduleMap = MutableStateFlow<Map<LocalDate, List<Schedule>>>(emptyMap())
    val scheduleMapLocal: StateFlow<Map<LocalDate, List<Schedule>>> = _scheduleMap

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
            // 💡 현재 룸 DB에 저장된 캘린더 설정값 가져오기
            val currentSettings = settingsRepository.getUserSettings().firstOrNull()
            val selectedIds = currentSettings?.syncedCalendarIds ?: emptyList()

            val results = repository.searchSchedules(
                query = dbQuery,
                colors = _filterColors.value,
                includeRoute = _filterIncludeRoute.value,
                startDate = searchStartDate.format(dateFormatter),
                endDate = searchEndDate.format(dateFormatter),
                selectedIds = selectedIds // 💡 파라미터 전달
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
    val scheduleMap: StateFlow<Map<LocalDate, List<Schedule>>> = combine(
        repository.allSchedules,
        settingsRepository.getUserSettings()
    ) { schedules, settings ->
        val selectedIds = settings?.syncedCalendarIds ?: emptyList()

        schedules.filter { schedule ->
            // 💡 백엔드(ROUTE) 일정은 무조건 노출 || 일반 일정은 선택된 캘린더일 때만 노출
            schedule.type == "ROUTE" || selectedIds.isEmpty() || selectedIds.contains(schedule.calendarId)
        }.groupBy { schedule ->
            LocalDate.parse(schedule.startDate, dateFormatter)
        }
    }.flowOn(Dispatchers.IO)
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

                // [수정] 고정된 날짜 대신 오늘 날짜를 기준으로 설정
                val today = LocalDate.now().format(dateFormatter)
                // 종료 날짜는 오늘로부터 1개월 뒤 혹은 1년 뒤 등으로 설정 가능
                val oneMonthLater = LocalDate.now().plusMonths(1).format(dateFormatter)

                // 1. 서버 데이터를 최신화 (서버->로컬)
                repository.getScheduleList(fullToken, today, oneMonthLater, null, null)

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
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (schedule.type == "ROUTE") {
                    // 💡 1. 경로 일정일 때: 서버 API 호출
                    // Schedule 엔티티를 CreateScheduleRequest(또는 서버가 원하는 DTO)로 변환
                    val request = mapScheduleToRequest(schedule)

                    val token = authDataStore.getAccessToken() ?: ""
                    val fullToken = if (token.isNotEmpty() && !token.startsWith("Bearer ")) "Bearer $token" else token

                    // 서버 업데이트 API 호출 (기존에 작성해둔 repository 함수 활용)
                    val response = repository.updateRouteSchedule(
                        accessToken = fullToken,
                        scheduleId = schedule.serverId ?: schedule.id, // serverId가 있으면 우선 사용
                        request = request,
                        calendarId = schedule.calendarId,
                        selectedColor = schedule.eventColor ?: 0
                    )

                    if (response.isSuccess) {
                        _updateScheduleEvent.value = true
                        Log.d("ScheduleViewModel", "경로 일정 서버 수정 성공")
                    } else {
                        throw Exception(response.message)
                    }

                } else {
                    // 💡 일반 일정: Repository가 시스템(Provider)과 로컬(Room)을 모두 수정함
                    repository.updateSchedule(schedule)

                    // 수정 직후 UI 반영을 위해 Event 발생
                    withContext(Dispatchers.Main) {
                        _updateScheduleEvent.value = true
                    }
                }

                // 💡 [중요] 수정 후 캘린더 화면 등에 즉시 반영되도록 데이터 새로고침
                refreshSchedules()

            } catch (e: Exception) {
                Log.e("ScheduleViewModel", "일정 수정 실패: ${e.message}")
                _updateScheduleEvent.value = false
            }
        }
    }


    private fun mapScheduleToRequest(schedule: Schedule): CreateScheduleRequest {
        val placeRequest = schedule.placeJson?.let {
            Gson().fromJson(it, PlaceRequest::class.java)
        }

        // 💡 Int 색상을 Hex String으로 변환
        val colorHex = schedule.eventColor?.let {
            String.format("#%06X", (0xFFFFFF and it))
        } ?: "#DC354B"

        return CreateScheduleRequest(
            title = schedule.title ?: "",
            isAllDay = schedule.isAllDay,
            startDate = schedule.startDate,
            endDate = schedule.endDate,
            startTime = schedule.startTime,
            endTime = schedule.endTime,
            memo = schedule.memo,
            isPathIncluded = schedule.withRoute,
            isRepeat = (schedule.repeatRule != null),
            repeatInfo = null,
            place = placeRequest, // 💡 기존 null에서 placeRequest로 수정
            reminders = emptyList(),
            route = null,
            calendarId = schedule.calendarId.toString(), // 💡 명세에 맞춰 String 추가
            color = colorHex                            // 💡 색상 추가
        )
    }
    fun updateRouteSchedule(
        scheduleId: Long,
        request: CreateScheduleRequest,
        calendarId: Long?,
        selectedColor: Int
    ) {
        viewModelScope.launch {
            try {
                // 1. 토큰 준비
                val token = authDataStore.getAccessToken() ?: ""
                val fullToken = if (token.isNotEmpty() && !token.startsWith("Bearer ")) "Bearer $token" else token

                // 2. Repository 호출 (곧 작성할 레포지토리 함수)
                val response = repository.updateRouteSchedule(
                    accessToken = fullToken,
                    scheduleId = scheduleId,
                    request = request,
                    calendarId = calendarId,
                    selectedColor = selectedColor
                )

                // 3. 결과 처리
                if (response.isSuccess) {
                    // 성공 시 로컬 DB를 최신화하여 화면에 반영
                    withContext(Dispatchers.IO) {
                        repository.refreshSchedules()
                    }
                    _updateScheduleEvent.value = true
                    Log.d("ScheduleViewModel", "경로 일정 서버 수정 성공: $scheduleId")
                } else {
                    Log.e("ScheduleViewModel", "경로 일정 서버 수정 실패: ${response.message}")
                    _updateScheduleEvent.value = false
                }
            } catch (e: Exception) {
                Log.e("ScheduleViewModel", "수정 중 예외 발생: ${e.message}")
                _updateScheduleEvent.value = false
            }
        }
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
                val token = authDataStore.getAccessToken() ?: ""
                val fullToken = if (token.isNotEmpty() && !token.startsWith("Bearer ")) "Bearer $token" else token

                // 1. Repository가 서버 응답값을 받아 로컬 DB에 저장할 때까지 대기
                val response = repository.createSchedule(
                    accessToken = if (token.isEmpty()) null else fullToken,
                    request = request,
                    placeId = placeId,
                    calendarId = calendarId,
                    selectedColor = selectedColor
                )

                if (response.isSuccess) {
                    // 워커에 등록
                    val serverId = response.result?.scheduleId
                    val arrival = request.route?.arrivalTime
                    if (serverId != null && arrival != null) {
                        scheduleFinalizeWorker(serverId, request.startDate, arrival)
                    }

                    // 2. [수정] Dispatchers.IO에서 확실히 처리가 끝난 후 UI를 갱신하도록 보장
                    withContext(Dispatchers.IO) {
                        // 여기서 repository.refreshSchedules()가 서버 리스트를 다시 긁어오면서
                        // 방금 저장한 초록색 일정을 덮어쓰지 않는지 확인이 필요합니다.
                        repository.refreshSchedules()
                    }
                    _createScheduleEvent.value = true
                } else {
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
        calendarId: Long? = null, // 💡 UI에서 관리하는 Long 타입 ID
        selectedColor: Int        // 💡 UI에서 선택된 Int 타입 색상
    ) {
        val settings = userSettings.value
        val reminders = mutableListOf<ReminderRequest>()

        // 1. 알림 설정 로직
        val alarmList = customAlarms ?: settings?.scheduleAlarms ?: emptyList()
        alarmList.forEach { minutes ->
            reminders.add(ReminderRequest(reminderType = "SCHEDULE", minutesBefore = minutes))
        }

        // 2. 반복 정보 처리
        val finalRepeatInfo = if (repeatInfo?.repeatType?.uppercase() == "NONE") {
            null
        } else {
            repeatInfo
        }

        // 3. 종료 날짜 결정
        val finalEndDate: String = when {
            !endDate.isNullOrBlank() && !endDate.startsWith("1970") -> endDate
            finalRepeatInfo != null && !finalRepeatInfo.repeatEndDate.isNullOrBlank() -> finalRepeatInfo.repeatEndDate!!
            else -> startDate
        }

        // 💡 Int 색상을 서버가 원하는 Hex String(예: #FF0000)으로 변환
        val colorHex = String.format("#%06X", (0xFFFFFF and selectedColor))

        // 4. [수정] Request 객체 생성 (calendarId와 color 필드 추가)
        val request = CreateScheduleRequest(
            title = title,
            isAllDay = isAllDay,
            startDate = startDate,
            endDate = finalEndDate,
            startTime = startTime,
            endTime = endTime,
            memo = memo,
            isPathIncluded = (place != null),
            isRepeat = (finalRepeatInfo != null),
            repeatInfo = finalRepeatInfo,
            place = place,
            reminders = reminders,
            route = null,
            calendarId = calendarId?.toString(), // 💡 명세에 맞춰 String으로 변환하여 추가
            color = colorHex                     // 💡 Hex String으로 변환하여 추가
        )

        // 5. Repository 호출
        viewModelScope.launch {
            try {
                val token = authDataStore.getAccessToken() ?: ""
                val fullToken = if (token.isNotEmpty() && !token.startsWith("Bearer ")) "Bearer $token" else token

                val response = repository.createSchedule(
                    accessToken = if (token.isEmpty()) null else fullToken,
                    request = request,
                    placeId = placeId,
                    calendarId = calendarId, // 로컬 DB 처리를 위해 원래의 Long 값도 전달
                    selectedColor = selectedColor
                )

                if (response.isSuccess) {
                    withContext(Dispatchers.IO) {
                        repository.refreshSchedules()
                    }
                    _createScheduleEvent.value = true
                }
            } catch (e: Exception) {
                Log.e("ScheduleViewModel", "일반 일정 생성 중 예외: ${e.message}")
            }
        }
    }

    fun getRepeatDescription(info: RepeatInfo?): String {
        if (info == null || info.repeatType.uppercase() == "NONE") return "반복 안 함"

        val typeStr = when(info.repeatType.uppercase()) {
            "DAILY" -> "매일"
            "WEEKLY" -> "매주"
            "MONTHLY" -> "매월"
            "YEARLY" -> "매년"
            else -> ""
        }

        val endStr = when(info.endType.uppercase()) {
            "COUNT" -> ", ${info.endCount}회 반복"
            "DATE" -> ", ${info.repeatEndDate}까지"
            else -> ""
        }

        return "$typeStr 반복$endStr"
    }

    // 이벤트 초기화 함수 (연속 호출 방지)
    fun resetCreateEvent() {
        _createScheduleEvent.value = null
    }

    // ViewModel 내부의 함수 수정
    fun getScheduleDetail(scheduleId: Long) {
        viewModelScope.launch {
            // 1. 먼저 DataStore에서 액세스 토큰을 가져옵니다.
            val token = authDataStore.getAccessToken() ?: ""

            // 2. 리포지토리 함수에 (토큰, ID) 두 가지 인자를 모두 전달합니다.
            // 💡 인터페이스 정의가 (accessToken, scheduleId) 순서이므로 이를 맞춥니다.
            val response = repository.getScheduleDetail(token, scheduleId)

            if (response.isSuccess && response.result != null) {
                // 3. 변수명(_scheduleDetailInfo) 일치 확인
                _scheduleDetailInfo.value = response.result
                _scheduleDetailInfoMap.update {
                    it + (scheduleId to response.result)
                }

                Log.d("DEBUG_TAG", "상세 정보 로드 성공: ID ${response.result.scheduleId}")
            } else {
                Log.e("DEBUG_TAG", "상세 정보 로드 실패: ${response.message}")
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

    fun deleteSchedule(id: Long, withRoute: Boolean) {
        viewModelScope.launch {
            if (withRoute) {
                // 💡 [추가] 예약된 워커 취소 (태그나 이름을 통해 취소 가능)
                androidx.work.WorkManager.getInstance(context).cancelUniqueWork("finalize_$id")
                Log.d("WorkManagerTest", "🗑️ 경로 일정 삭제로 인한 워커 예약 취소: finalize_$id")
                deleteRouteSchedule(id)
            } else {
                // 일반 일정: 단일이든 반복(전체)이든 시스템/로컬 DB에서 제거
                deleteNormalSchedule(id)
            }
        }
    }

    // 1. 일반 일정 삭제 (기기 캘린더 + 로컬 DB)
    private suspend fun deleteNormalSchedule(id: Long) {
        Log.d("DeleteLog", "일반 일정 삭제 시도: ID = $id")
        // Repository 인터페이스에 추가한 deleteNormalSchedule 호출
        val response = repository.deleteNormalSchedule(id)
        if (response.isSuccess) {
            Log.d("DeleteLog", "일반 일정 삭제 성공")
            // 필요 시 UI 이벤트를 위한 StateFlow 업데이트 가능
        } else {
            Log.e("DeleteLog", "일반 일정 삭제 실패: ${response.message}")
        }
    }

    // 2. 경로 일정 삭제 (서버 API + 로컬 DB)
    private suspend fun deleteRouteSchedule(id: Long) {
        Log.d("DeleteLog", "경로 일정 삭제 시도: ID = $id")
        // Repository 인터페이스에 추가한 deleteRouteSchedule 호출
        val response = repository.deleteRouteSchedule(id)
        if (response.isSuccess) {
            Log.d("DeleteLog", "경로 일정 삭제 성공")
        } else {
            Log.e("DeleteLog", "서버 삭제 실패: ${response.message}")
        }
    }

    fun deleteOnlyThisOccurrence(schedule: Schedule, date: LocalDate) {
        viewModelScope.launch {
            // 1. 날짜 형식을 yyyyMMdd로 변환 (예: "20260226") - 시스템 표준에 맞춤
            val dateString = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"))

            // 2. 기존 exDate 리스트 가져오기 (이미 콤마로 구분된 상태)
            val currentExDate = schedule.exDate ?: ""

            // 3. 중복 체크 로직 개선
            val exDateList = currentExDate.split(",").map { it.trim() }.filter { it.isNotEmpty() }

            val newExDate = if (!exDateList.contains(dateString)) {
                if (currentExDate.isEmpty()) dateString else "$currentExDate,$dateString"
            } else {
                currentExDate
            }

            val updatedSchedule = schedule.copy(exDate = newExDate)

            // 4. 시스템 반영 (Calendar Provider)
            repository.updateExDate(updatedSchedule)

            // 💡 5. [중요] Room DB를 최신 상태로 새로고침
            // 이 함수가 호출되어야 수정된 EXDATE가 Room에 저장되고 expandSchedules가 다시 돕니다.
            refreshSchedules()

            Log.d("ExDateLog", "작업 완료 및 새로고침 호출됨")
        }
    }

    // Schedule ID로 단일 일정 정보를 가져오는 함수
    suspend fun getScheduleById(id: Long): Schedule? {
        return withContext(Dispatchers.IO) {
            repository.getScheduleById(id) // Repository에도 이 함수가 정의되어 있어야 합니다.
        }
    }

    fun resetUpdateEvent() {
        _updateScheduleEvent.value = null
    }

    fun parseRepeatRule(rrule: String, endDate: String): RepeatInfo? {
        return repository.parseRRule(rrule, endDate)
    }
    fun buildRRuleString(info: RepeatInfo?): String? {
        if (info == null || info.repeatType.uppercase() == "NONE") return null

        return try {
            val rrule = StringBuilder("FREQ=${info.repeatType.uppercase()}")

            // 간격 설정 (매 2주, 매 3개월 등)
            if (info.repeatInterval > 1) {
                rrule.append(";INTERVAL=${info.repeatInterval}")
            }

            // 요일 설정 (WEEKLY일 때 "MO,WE,FR" 형식)
            if (!info.daysOfWeek.isNullOrEmpty()) {
                val days = info.daysOfWeek.split(",")
                    .mapNotNull { day ->
                        when (day.trim().uppercase()) {
                            "SUNDAY", "SUN", "SU" -> "SU"
                            "MONDAY", "MON", "MO" -> "MO"
                            "TUESDAY", "TUE", "TU" -> "TU"
                            "WEDNESDAY", "WED", "WE" -> "WE"
                            "THURSDAY", "THU", "TH" -> "TH"
                            "FRIDAY", "FRI", "FR" -> "FR"
                            "SATURDAY", "SAT", "SA" -> "SA"
                            else -> null
                        }
                    }.joinToString(",")
                if (days.isNotEmpty()) rrule.append(";BYDAY=$days")
            }

            // 종료 조건 설정
            when (info.endType.uppercase()) {
                "COUNT" -> {
                    if (info.endCount != null) rrule.append(";COUNT=${info.endCount}")
                }
                "DATE" -> {
                    if (!info.repeatEndDate.isNullOrEmpty()) {
                        // "2026-02-18" -> "20260218" 형식으로 변환
                        val untilDate = info.repeatEndDate!!.replace("-", "")
                        rrule.append(";UNTIL=${untilDate}T235959Z")
                    }
                }
            }

            rrule.toString()
        } catch (e: Exception) {
            null
        }
    }

    fun updateRouteScheduleCombined(
        scheduleId: Long,
        generalRequest: UpdateScheduleRequest,
        routeRequest: UpdateScheduleEditRouteRequest
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. 토큰 준비
                val token = authDataStore.getAccessToken() ?: ""
                val fullToken = if (token.startsWith("Bearer ")) token else "Bearer $token"

                // 2. [보정] generalRequest에 담긴 색상과 캘린더 ID가 올바른지 확인
                // 만약 Fragment에서 null로 넘어왔을 경우를 대비해
                // generalRequest의 값을 다시 한번 체크하거나 로깅하는 것이 좋습니다.
                Log.d("UpdateLog", "수정 요청 데이터 - ID: $scheduleId, Color: ${generalRequest.color}, Calendar: ${generalRequest.calendarId}")

                // 3. Repository 호출 (통합 수정 로직)
                val response = repository.updateRouteScheduleCombined(
                    fullToken,
                    scheduleId,
                    generalRequest, // 이 안에 이미 String 타입의 calendarId와 color가 들어있어야 합니다.
                    routeRequest
                )

                // 4. 결과 처리
                if (response.isSuccess) {
                    val arrival = routeRequest.arrivalTime
                    if (arrival != null) {
                        scheduleFinalizeWorker(scheduleId, generalRequest.startDate, arrival)
                    }
                    withContext(Dispatchers.Main) {
                        _updateScheduleEvent.value = true
                        // 수정 성공 후 즉시 로컬 DB 데이터를 서버와 동기화
                        refreshSchedules()
                    }
                    Log.d("UpdateLog", "일정 및 경로 수정 통합 성공: $scheduleId")
                } else {
                    withContext(Dispatchers.Main) {
                        _updateScheduleEvent.value = false
                    }
                    Log.e("UpdateLog", "수정 실패 (Code: ${response.code}): ${response.message}")
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _updateScheduleEvent.value = false
                }
                Log.e("UpdateLog", "통신 예외 발생: ${e.message}")
            }
        }
    }

    fun convertRouteToNormalAtArrival(scheduleId: Long) {
        viewModelScope.launch {
            val success = repository.convertRouteToNormalLocal(scheduleId)
            if (success) {
                refreshSchedules() // 성공 시 UI 갱신
            }
        }
    }

    private fun scheduleFinalizeWorker(scheduleId: Long, startDate: String, arrivalTime: String) {
        try {
            // 1. 시간 파싱 준비
            val cleanArrival = if (arrivalTime.contains("T")) {
                arrivalTime.replace("T", " ").substring(0, 16)
            } else {
                "$startDate $arrivalTime"
            }

            // 2. SDF 설정 및 타임존 지정
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.KOREA)

            // 💡 중요: 서버 시간이 UTC라면 한국 시간으로 바꾸기 위해 타임존 설정
            // 만약 서버가 이미 한국 시간을 주는데 파싱이 꼬이는 거라면 아래 줄을 주석 처리하세요.
            sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")

            val arrivalDate = sdf.parse(cleanArrival)
            val currentTime = System.currentTimeMillis()

            // 3. 지연 시간 계산
            val delay = (arrivalDate?.time ?: 0) - currentTime

            Log.d("WorkManagerTest", """
            [타임존 체크]
            입력된 시간: $cleanArrival
            현재 시간(KST): ${java.text.SimpleDateFormat("HH:mm").format(java.util.Date(currentTime))}
            계산된 실행 시간(KST): ${java.text.SimpleDateFormat("HH:mm").format(arrivalDate)}
            남은 초: ${delay / 1000}초
        """.trimIndent())

            // 10초는 0보다 크므로 아래 예약 로직이 바로 실행됩니다.
            if (delay > 0) {
                val data = androidx.work.Data.Builder()
                    .putLong("schedule_id", scheduleId)
                    .build()

                val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.example.pace.data.worker.ScheduleFinalizeWorker>()
                    .setInitialDelay(delay, java.util.concurrent.TimeUnit.MILLISECONDS) // 10초 뒤로 예약
                    .setInputData(data)
                    .addTag("FINALIZE_$scheduleId")
                    .build()

                androidx.work.WorkManager.getInstance(context)
                    .enqueueUniqueWork(
                        "finalize_$scheduleId",
                        androidx.work.ExistingWorkPolicy.REPLACE,
                        workRequest
                    )
            }
        } catch (e: Exception) {
            Log.e("WorkManager", "❌ 예약 실패: ${e.message}")
        }
    }
    fun fetchRouteDetail(scheduleId: Long) {
        if (_routeDetails.value.containsKey(scheduleId)) return

        viewModelScope.launch {
            try {
                // 1. 저장된 액세스 토큰 가져오기 (예시: tokenRepository 또는 SharedPreferences)
                val accessToken = authDataStore.getAccessToken()

                if (accessToken != null) {
                    // 2. Repository 호출
                    val response = repository.getScheduleDetail(accessToken, scheduleId)

                    // 3. 응답 성공 및 데이터 확인
                    if (response.isSuccess && response.result != null) {
                        // 서버 응답 바디의 result 안에 route가 있는지 확인
                        val routeData = response.result.route
                        if (routeData != null) {
                            _routeDetails.value = _routeDetails.value + (scheduleId to routeData)
                        }
                    }
                } else {
                    Log.e("ScheduleViewModel", "AccessToken이 없습니다.")
                }
            } catch (e: Exception) {
                Log.e("ScheduleViewModel", "Route detail fetch failed: ${e.message}")
            }
        }
    }

    fun togglePinLocally(date: LocalDate, scheduleId: Long) {
        viewModelScope.launch {
            // 1. 현재 메모리에 있는 리스트에서 해당 스케줄 찾기
            val currentSchedules = scheduleMap.value[date] ?: return@launch
            val targetSchedule = currentSchedules.find { it.id == scheduleId } ?: return@launch

            // 2. 새로운 핀 상태 결정
            val newPinStatus = !targetSchedule.isPinned

            // 3. DB 업데이트 (컬럼 하나만 수정하므로 리마인더 안전!)
            repository.updatePinStatus(scheduleId, newPinStatus)

            // 4. UI 반영을 위해 StateFlow 업데이트
            val updatedMap = scheduleMap.value.toMutableMap()
            val updatedList = currentSchedules.map {
                if (it.id == scheduleId) it.copy(isPinned = newPinStatus) else it
            }
            updatedMap[date] = updatedList

            // _scheduleMap 또는 scheduleMap (Mutable 인 것)에 대입
            _scheduleMap.value = updatedMap

            Log.d("PinUpdate", "DB 업데이트 완료: ${targetSchedule.title} -> $newPinStatus")
        }
    }

}
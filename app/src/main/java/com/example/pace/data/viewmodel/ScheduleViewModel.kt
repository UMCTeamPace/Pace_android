package com.example.pace.data.viewmodel

import android.content.Context
import android.graphics.Color
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.pace.data.datasource.AuthDataStore
import com.example.pace.data.model.Schedule
import com.example.pace.data.model.UserSettingsEntity
import com.example.pace.data.model.request.CreateScheduleRequest
import com.example.pace.data.model.request.PlaceRequest
import com.example.pace.data.model.request.ReminderRequest
import com.example.pace.data.model.request.RepeatInfo
import com.example.pace.data.model.request.UpdateScheduleEditRouteRequest
import com.example.pace.data.model.request.UpdateScheduleRequest
import com.example.pace.data.model.response.RouteInfo
import com.example.pace.data.model.response.ScheduleDetailResponse
import com.example.pace.data.repeat.RepeatRuleHelper
import com.example.pace.data.repository.repository.ScheduleRepository
import com.example.pace.data.util.AlarmScheduler
import com.example.pace.data.repository.repository.SettingsRepository
import com.example.pace.data.worker.ScheduleFinalizeWorker
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val repository: ScheduleRepository,
    private val authDataStore: AuthDataStore,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val rangeFormatter = DateTimeFormatter.ofPattern("yyyy. MM. dd")

    // Last search query
    private var lastQuery: String = ""

    // Search date range state
    private var searchStartDate = LocalDate.now().minusYears(1)
    private var searchEndDate = LocalDate.now().plusYears(1)

    // UI search range label
    private val _searchRangeText = MutableStateFlow("")
    val searchRangeText: StateFlow<String> = _searchRangeText

    // Filter state
    private val _filterColor = MutableStateFlow<String?>(null)
    val filterColor: StateFlow<String?> = _filterColor

    private val _filterIncludeRoute = MutableStateFlow(true)
    val filterIncludeRoute: StateFlow<Boolean> = _filterIncludeRoute

    // Search result state
    private val _searchResults = MutableStateFlow<List<Schedule>>(emptyList())
    val searchResults: StateFlow<List<Schedule>> = _searchResults

    // Selected date for calendar UI
    private val _selectedDate = MutableStateFlow<LocalDate>(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate

    private val _filterColors = MutableStateFlow<Set<String>>(emptySet())
    val filterColors: StateFlow<Set<String>> = _filterColors

    private val _isEditMode = MutableStateFlow(false)
    val isEditMode: StateFlow<Boolean> = _isEditMode


    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds

    private val _selectedOccurrenceKeys = MutableStateFlow<Set<String>>(emptySet())
    val selectedOccurrenceKeys: StateFlow<Set<String>> = _selectedOccurrenceKeys

    private val _createScheduleEvent = MutableStateFlow<Boolean?>(null)
    val createScheduleEvent: StateFlow<Boolean?> = _createScheduleEvent

    // Schedule detail
    private val _scheduleDetailInfo = MutableStateFlow<ScheduleDetailResponse?>(null)
    val scheduleDetailInfo: StateFlow<ScheduleDetailResponse?> = _scheduleDetailInfo
    private val _scheduleDetailInfoMap =
        MutableStateFlow<Map<Long, ScheduleDetailResponse>>(emptyMap())
    val scheduleDetailInfoMap = _scheduleDetailInfoMap

    private val _updateScheduleEvent = MutableStateFlow<Boolean?>(null)
    val updateScheduleEvent: StateFlow<Boolean?> = _updateScheduleEvent

    private val _routeDetails = MutableStateFlow<Map<Long, RouteInfo>>(emptyMap())

    // Public StateFlow observed by fragments
    val routeDetails: StateFlow<Map<Long, RouteInfo>> = _routeDetails

    // Calendar view data
    private val _scheduleMap = MutableStateFlow<Map<LocalDate, List<Schedule>>>(emptyMap())
    val scheduleMapLocal: StateFlow<Map<LocalDate, List<Schedule>>> = _scheduleMap

    fun setEditMode(enabled: Boolean) {
        _isEditMode.value = enabled
        if (!enabled) _selectedIds.value = emptySet() // Clear selection when edit mode ends
        if (!enabled) _selectedOccurrenceKeys.value = emptySet()
    }

    fun toggleSelection(id: Long) {
        val current = _selectedIds.value.toMutableSet()
        if (current.contains(id)) current.remove(id) else current.add(id)
        _selectedIds.value = current
    }

    fun toggleOccurrenceSelection(scheduleId: Long, occurrenceDate: String) {
        val key = buildOccurrenceSelectionKey(scheduleId, occurrenceDate)
        val current = _selectedOccurrenceKeys.value.toMutableSet()
        if (current.contains(key)) current.remove(key) else current.add(key)
        _selectedOccurrenceKeys.value = current
    }

    fun buildOccurrenceSelectionKey(scheduleId: Long, occurrenceDate: String): String {
        return "$scheduleId|$occurrenceDate"
    }

    fun deleteSelected() {
        viewModelScope.launch(Dispatchers.IO) {
            // Bulk delete logic
            val idsToDelete = _selectedIds.value.toList()
            // repository.deleteSchedules(idsToDelete) // 삭제 함수는 repository 쪽 구현 확인이 필요합니다.

            withContext(Dispatchers.Main) {
                setEditMode(false) // Exit edit mode after delete
            }
        }
    }
    val usedColors: StateFlow<List<String>> = repository.getUsedColors()
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Companion.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val userSettings: StateFlow<UserSettingsEntity?> = settingsRepository.getUserSettings()
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Companion.WhileSubscribed(5000), null)

    // Raw schedule streams
    val allSchedules = repository.allSchedules
    val calendarEvents = repository.calendarEvents

    init {
        updateRangeText()
        refreshSchedules()
        observeCalendarChanges()
    }

    private fun observeCalendarChanges() {
        viewModelScope.launch {
            calendarEvents
                .debounce(500)
                .collect {
                    Log.d("CALENDAR_OBSERVER", "기기 캘린더 변경 감지, 일정 새로고침 실행")
                    refreshSchedules()
                }
        }
    }


    fun searchWithCurrentQuery() {
        searchSchedules(lastQuery)
    }

    // Update displayed search range text
    private fun updateRangeText() {
        _searchRangeText.value = "${searchStartDate.format(rangeFormatter)} ~ ${searchEndDate.format(rangeFormatter)}"
    }

    // Expand search range by six months
    fun expandSearchRange() {
        searchStartDate = searchStartDate.minusMonths(6)
        searchEndDate = searchEndDate.plusMonths(6)
        updateRangeText()
        searchSchedules(lastQuery) // Re-run search with expanded range
    }

    fun setSelectedDate(date: LocalDate) {
        _selectedDate.value = date
    }

    // Toggle color filter
    fun toggleFilterColor(color: String) {
        val current = _filterColors.value.toMutableSet()
        if (current.contains(color)) {
            current.remove(color)
        } else {
            current.add(color)
        }
        _filterColors.value = current

        Log.d("SearchFlow", "색상 필터 변경: ${_filterColors.value}")

        searchSchedules(lastQuery)
    }

    fun searchSchedules(query: String) {
        lastQuery = query
        val dbQuery = if (query.isBlank()) "%" else "%$query%"

        viewModelScope.launch(Dispatchers.IO) {
            // Load currently synced calendar settings from Room
            val currentSettings = settingsRepository.getUserSettings().firstOrNull()
            val selectedIds = currentSettings?.syncedCalendarIds ?: emptyList()

            val results = repository.searchSchedules(
                query = dbQuery,
                colors = _filterColors.value,
                includeRoute = _filterIncludeRoute.value,
                startDate = searchStartDate.format(dateFormatter),
                endDate = searchEndDate.format(dateFormatter),
                selectedIds = selectedIds
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

    // Calendar list exposed to UI
    val scheduleMap: StateFlow<Map<LocalDate, List<Schedule>>> = combine(
        repository.allSchedules,
        settingsRepository.getUserSettings()
    ) { schedules, settings ->
        val selectedIds = settings?.syncedCalendarIds ?: emptyList()

        schedules.filter { schedule ->
            // Always include route schedules, or include normal schedules from selected calendars
            schedule.type == "ROUTE" || selectedIds.isEmpty() || selectedIds.contains(schedule.calendarId)
        }.flatMap { schedule ->
            val startDate = runCatching { LocalDate.parse(schedule.startDate, dateFormatter) }.getOrNull()
                ?: return@flatMap emptyList()
            val endDate = runCatching { LocalDate.parse(schedule.endDate, dateFormatter) }.getOrNull()
                ?: startDate

            generateSequence(startDate) { current ->
                current.plusDays(1).takeIf { !it.isAfter(endDate) }
            }.map { date ->
                date to schedule
            }.toList()
        }.groupBy(
            keySelector = { it.first },
            valueTransform = { it.second }
        )
    }.flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Companion.WhileSubscribed(5000),
            initialValue = emptyMap()
        )


    fun refreshSchedules() {
        viewModelScope.launch {
            try {
                val token = authDataStore.getAccessToken() ?: return@launch
                val fullToken = if (token.startsWith("Bearer ")) token else "Bearer $token"

                // Use today as the fixed start date
                val today = LocalDate.now().format(dateFormatter)
                // Load one month ahead
                val oneMonthLater = LocalDate.now().plusMonths(1).format(dateFormatter)

                // Sync latest server data to local storage
                repository.getScheduleList(fullToken, today, oneMonthLater, null, null)

                // Refresh local provider schedules
                withContext(Dispatchers.IO) {
                    // Remove schedules already deleted from the system calendar
                    repository.cleanUpSystemDeletedSchedules()

                    // Reload the latest schedules still present in the system calendar
                    repository.refreshSchedules()
                }

                // Refresh search results if a search is active
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
                    // Route schedules are updated through the server API
                    val request = mapScheduleToRequest(schedule)

                    val token = authDataStore.getAccessToken() ?: ""
                    val fullToken = if (token.isNotEmpty() && !token.startsWith("Bearer ")) "Bearer $token" else token

                    val response = repository.updateRouteSchedule(
                        accessToken = fullToken,
                        scheduleId = schedule.serverId ?: schedule.id,
                        request = request,
                        calendarId = schedule.calendarId,
                        selectedColor = schedule.eventColor ?: 0
                    )

                    if (response.isSuccess) {
                        val targetScheduleId = schedule.serverId ?: schedule.id
                        replaceRouteScheduleRuntime(targetScheduleId)
                        _updateScheduleEvent.value = true
                        Log.d("ScheduleViewModel", "경로 일정 서버 수정 성공")
                    } else {
                        throw Exception(response.message)
                    }

                } else {
                    // Normal schedules are updated in the provider and local Room DB
                    repository.updateSchedule(schedule)

                    withContext(Dispatchers.Main) {
                        _updateScheduleEvent.value = true
                    }
                }

                // Reload after update so calendar UI reflects changes immediately
                if (schedule.type != "ROUTE") {
                    refreshSchedules()
                }

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

        // Convert color int to hex
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
            place = placeRequest,
            reminders = emptyList(),
            route = null,
            calendarId = schedule.calendarId.toString(),
            color = colorHex
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
                // Prepare token
                val token = authDataStore.getAccessToken() ?: ""
                val fullToken = if (token.isNotEmpty() && !token.startsWith("Bearer ")) "Bearer $token" else token

                // Call repository
                val response = repository.updateRouteSchedule(
                    accessToken = fullToken,
                    scheduleId = scheduleId,
                    request = request,
                    calendarId = calendarId,
                    selectedColor = selectedColor
                )

                // Handle result
                if (response.isSuccess) {
                    withContext(Dispatchers.IO) {
                        replaceRouteScheduleRuntime(scheduleId)
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
        // Clear last query
        lastQuery = ""

        // Clear search results
        _searchResults.value = emptyList()

        Log.d("SearchFlow", "ScheduleViewModel: 검색어와 결과 초기화 완료")
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

                // Create schedule and wait until local DB is updated
                val response = repository.createSchedule(
                    accessToken = if (token.isEmpty()) null else fullToken,
                    request = request,
                    placeId = placeId,
                    calendarId = calendarId,
                    selectedColor = selectedColor
                )

                if (response.isSuccess) {
                    val serverId = response.result?.scheduleId
                    val arrival = request.route?.arrivalTime

                    withContext(Dispatchers.IO) {
                        repository.refreshSchedules()
                        if (request.route != null && serverId != null) {
                            repository.getScheduleById(serverId)?.let { schedule ->
                                syncRouteScheduleRuntime(schedule, arrival)
                            }
                        }
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
        calendarId: Long? = null,
        selectedColor: Int
    ) {
        val settings = userSettings.value
        val reminders = mutableListOf<ReminderRequest>()

        // Build reminders
        val alarmList = customAlarms ?: settings?.scheduleAlarms ?: emptyList()
        alarmList.forEach { minutes ->
            reminders.add(ReminderRequest(reminderType = "SCHEDULE", minutesBefore = minutes))
        }

        // Normalize repeat info
        val finalRepeatInfo = if (repeatInfo?.repeatType?.uppercase() == "NONE") {
            null
        } else {
            repeatInfo
        }

        // Determine end date
        val finalEndDate: String = when {
            !endDate.isNullOrBlank() && !endDate.startsWith("1970") -> endDate
            finalRepeatInfo != null && !finalRepeatInfo.repeatEndDate.isNullOrBlank() -> finalRepeatInfo.repeatEndDate!!
            else -> startDate
        }

        // Convert color int to hex string
        val colorHex = String.format("#%06X", (0xFFFFFF and selectedColor))

        // Build create request
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
            calendarId = calendarId?.toString(),
            color = colorHex
        )

        // Call repository
        viewModelScope.launch {
            try {
                val token = authDataStore.getAccessToken() ?: ""
                val fullToken = if (token.isNotEmpty() && !token.startsWith("Bearer ")) "Bearer $token" else token

                val response = repository.createSchedule(
                    accessToken = if (token.isEmpty()) null else fullToken,
                    request = request,
                    placeId = placeId,
                    calendarId = calendarId,
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

    // Reset create event after it is consumed
    fun resetCreateEvent() {
        _createScheduleEvent.value = null
    }

    // Load schedule detail
    fun getScheduleDetail(scheduleId: Long) {
        viewModelScope.launch {
            val token = authDataStore.getAccessToken() ?: ""
            val fullToken = if (token.isNotEmpty() && !token.startsWith("Bearer ")) {
                "Bearer $token"
            } else {
                token
            }

            val response = repository.getScheduleDetail(fullToken, scheduleId)

            if (response.isSuccess && response.result != null) {
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
            repository.getCalendarName(calendarId) ?: "기본 일정"
        } catch (e: Exception) {
            "기본 일정"
        }
    }

    fun getCalendarColorById(calendarId: Long): Int? {
        return try {
            repository.getCalendarColor(calendarId)
        } catch (e: Exception) {
            null
        }
    }

    fun deleteSchedule(id: Long, withRoute: Boolean) {
        viewModelScope.launch {
            if (withRoute) {
                // Cancel pending route finalize work
                Log.d("WorkManagerTest", "삭제로 인한 경로 일정 작업 취소: finalize_$id")
                deleteRouteSchedule(id)
            } else {
                deleteNormalSchedule(id)
            }
        }
    }

    // Delete normal schedule from device calendar and local DB
    private suspend fun deleteNormalSchedule(id: Long) {
        Log.d("DeleteLog", "일반 일정 삭제 시도: ID = $id")
        val response = repository.deleteNormalSchedule(id)
        if (response.isSuccess) {
            Log.d("DeleteLog", "일반 일정 삭제 성공")
        } else {
            Log.e("DeleteLog", "일반 일정 삭제 실패: ${response.message}")
        }
    }

    // Delete route schedule through server API and local DB
    private suspend fun deleteRouteSchedule(id: Long) {
        Log.d("DeleteLog", "경로 일정 삭제 시도: ID = $id")
        val existingSchedule = repository.getScheduleById(id)
        val response = repository.deleteRouteSchedule(id)
        if (response.isSuccess) {
            existingSchedule?.let { schedule ->
                cancelRouteScheduleRuntime(schedule)
            }
            Log.d("DeleteLog", "경로 일정 삭제 성공")
        } else {
            Log.e("DeleteLog", "서버 삭제 실패: ${response.message}")
        }
    }

    fun deleteOnlyThisOccurrence(schedule: Schedule, date: LocalDate) {
        viewModelScope.launch(Dispatchers.IO) {
            // Apply the exclusion to the original recurring event
            val seriesSchedule = repository.getScheduleById(schedule.id) ?: schedule
            excludeOccurrenceFromRecurringSchedule(seriesSchedule, date)

            // Do not trigger a full server sync here.
            // Server schedules keep local-only EXDATE overrides in Room,
            // and a full sync would overwrite them immediately.
            repository.refreshSchedules()

            Log.d("ExDateLog", "작업 완료 후 새로고침 호출")
        }
    }

    // Load a single schedule by ID
    suspend fun getScheduleById(id: Long): Schedule? {
        return withContext(Dispatchers.IO) {
            repository.getScheduleById(id)
        }
    }

    fun resetUpdateEvent() {
        _updateScheduleEvent.value = null
    }

    fun parseRepeatRule(rrule: String, endDate: String): RepeatInfo? {
        return repository.parseRRule(rrule, endDate)
    }
    fun buildRRuleString(info: RepeatInfo?): String? = RepeatRuleHelper.buildRRule(info)

    fun updateRouteScheduleCombined(
        scheduleId: Long,
        generalRequest: UpdateScheduleRequest,
        routeRequest: UpdateScheduleEditRouteRequest
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Prepare token
                val token = authDataStore.getAccessToken() ?: ""
                val fullToken = if (token.startsWith("Bearer ")) token else "Bearer $token"

                Log.d("UpdateLog", "수정 요청 데이터 - ID: $scheduleId, Color: ${generalRequest.color}, Calendar: ${generalRequest.calendarId}")

                val response = repository.updateRouteScheduleCombined(
                    fullToken,
                    scheduleId,
                    generalRequest,
                    routeRequest
                )

                if (response.isSuccess) {
                    val arrival = routeRequest.arrivalTime
                    replaceRouteScheduleRuntime(scheduleId, arrival)
                    withContext(Dispatchers.Main) {
                        _updateScheduleEvent.value = true
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
                refreshSchedules()
            }
        }
    }

    private fun syncRouteScheduleRuntime(schedule: Schedule, arrivalTimeOverride: String? = null) {
        scheduleRouteAlarms(schedule)
        scheduleFinalize(schedule, arrivalTimeOverride)
    }

    private suspend fun replaceRouteScheduleRuntime(scheduleId: Long, arrivalTimeOverride: String? = null) {
        repository.getScheduleById(scheduleId)?.let { existing ->
            cancelRouteScheduleRuntime(existing)
        }
        repository.refreshSchedules()
        repository.getScheduleById(scheduleId)?.let { updated ->
            syncRouteScheduleRuntime(updated, arrivalTimeOverride)
        }
    }

    private fun cancelRouteScheduleRuntime(schedule: Schedule) {
        AlarmScheduler.cancelPaceAlarms(
            context = context,
            scheduleId = schedule.id,
            eventReminders = schedule.reminders,
            departureReminders = schedule.departureReminders
        )
        WorkManager.getInstance(context).cancelUniqueWork("finalize_${schedule.id}")
    }

    private fun scheduleRouteAlarms(schedule: Schedule) {
        val scheduleTimeMillis = runCatching {
            val dateTimeText = "${schedule.startDate} ${schedule.startTime}"
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).parse(dateTimeText)?.time
        }.getOrNull() ?: return

        schedule.reminders.forEach { minutes ->
            AlarmScheduler.schedulePaceAlarm(
                context = context,
                scheduleId = schedule.id,
                alarmType = "EVENT",
                scheduleTimeMillis = scheduleTimeMillis,
                leadMinutes = minutes
            )
        }

        schedule.departureReminders.forEach { minutes ->
            AlarmScheduler.schedulePaceAlarm(
                context = context,
                scheduleId = schedule.id,
                alarmType = "DEPARTURE",
                scheduleTimeMillis = scheduleTimeMillis,
                leadMinutes = minutes
            )
        }
    }

    private fun scheduleFinalize(schedule: Schedule, arrivalTimeOverride: String? = null) {
        val arrivalTime = arrivalTimeOverride ?: schedule.routeJson?.let {
            runCatching { Gson().fromJson(it, RouteInfo::class.java).arrivalTime }.getOrNull()
        }
        if (!arrivalTime.isNullOrBlank()) {
            scheduleFinalizeWorker(schedule.id, schedule.startDate, arrivalTime)
        }
    }

    private fun scheduleFinalizeWorker(scheduleId: Long, startDate: String, arrivalTime: String) {
        try {
            // Normalize incoming arrival time
            val cleanArrival = if (arrivalTime.contains("T")) {
                arrivalTime.replace("T", " ").substring(0, 16)
            } else {
                "$startDate $arrivalTime"
            }

            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA)
            sdf.timeZone = TimeZone.getTimeZone("UTC")

            val arrivalDate = sdf.parse(cleanArrival)
            val currentTime = System.currentTimeMillis()

            val delay = (arrivalDate?.time ?: 0) - currentTime

            Log.d("WorkManagerTest", """
            [타임존 체크]
            입력된 시간: $cleanArrival
            현재 시간(KST): ${SimpleDateFormat("HH:mm").format(Date(currentTime))}
            계산된 실행 시간(KST): ${SimpleDateFormat("HH:mm").format(arrivalDate)}
            남은 초: ${delay / 1000}초
        """.trimIndent())

            if (delay > 0) {
                val data = Data.Builder()
                    .putLong("schedule_id", scheduleId)
                    .build()

                val workRequest = OneTimeWorkRequestBuilder<ScheduleFinalizeWorker>()
                    .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                    .setInputData(data)
                    .addTag("FINALIZE_$scheduleId")
                    .build()

                WorkManager.getInstance(context)
                    .enqueueUniqueWork(
                        "finalize_$scheduleId",
                        ExistingWorkPolicy.REPLACE,
                        workRequest
                    )
            }
        } catch (e: Exception) {
            Log.e("WorkManager", "작업 예약 실패: ${e.message}")
        }
    }
    fun fetchRouteDetail(scheduleId: Long) {
        if (_routeDetails.value.containsKey(scheduleId)) return

        viewModelScope.launch {
            try {
                val accessToken = authDataStore.getAccessToken()

                if (accessToken != null) {
                    val fullToken = if (!accessToken.startsWith("Bearer ")) {
                        "Bearer $accessToken"
                    } else {
                        accessToken
                    }
                    val response = repository.getScheduleDetail(fullToken, scheduleId)

                    if (response.isSuccess && response.result != null) {
                        val routeData = response.result.route
                        if (routeData != null) {
                            _routeDetails.value = _routeDetails.value + (scheduleId to routeData)
                        }
                    } else if (isScheduleNotFound(response.code, response.message)) {
                        repository.removeLocalRouteSchedule(scheduleId)
                        _routeDetails.value = _routeDetails.value - scheduleId
                        refreshSchedules()
                    }
                } else {
                    Log.e("ScheduleViewModel", "AccessToken이 없습니다.")
                }
            } catch (e: Exception) {
                Log.e("ScheduleViewModel", "Route detail fetch failed: ${e.message}")
            }
        }
    }

    fun updateRecurringSchedule(
        originalSchedule: Schedule,
        updatedSchedule: Schedule,
        occurrenceDate: LocalDate,
        scope: String
    ) {
        if (scope == "ALL") {
            updateSchedule(updatedSchedule)
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                excludeOccurrenceFromRecurringSchedule(originalSchedule, occurrenceDate)

                val placeRequest = updatedSchedule.placeJson?.let {
                    runCatching { Gson().fromJson(it, PlaceRequest::class.java) }.getOrNull()
                }
                val colorInt = updatedSchedule.eventColor
                    ?: updatedSchedule.calendarColor
                    ?: Color.parseColor("#DC354B")
                val reminders = updatedSchedule.reminders.map {
                    ReminderRequest(reminderType = "SCHEDULE", minutesBefore = it)
                }

                val request = CreateScheduleRequest(
                    title = updatedSchedule.title.orEmpty(),
                    isAllDay = updatedSchedule.isAllDay,
                    startDate = updatedSchedule.startDate,
                    endDate = updatedSchedule.endDate,
                    startTime = if (updatedSchedule.isAllDay) null else updatedSchedule.startTime,
                    endTime = if (updatedSchedule.isAllDay) null else updatedSchedule.endTime,
                    memo = updatedSchedule.memo,
                    isPathIncluded = placeRequest != null,
                    isRepeat = false,
                    repeatInfo = null,
                    place = placeRequest,
                    reminders = reminders,
                    route = null,
                    calendarId = updatedSchedule.calendarId.toString(),
                    color = String.format("#%06X", (0xFFFFFF and colorInt))
                )

                val token = authDataStore.getAccessToken() ?: ""
                val fullToken = if (token.isNotEmpty() && !token.startsWith("Bearer ")) {
                    "Bearer $token"
                } else {
                    token
                }

                val response = repository.createSchedule(
                    accessToken = if (token.isEmpty()) null else fullToken,
                    request = request,
                    placeId = null,
                    calendarId = updatedSchedule.calendarId,
                    selectedColor = colorInt
                )

                repository.refreshSchedules()
                _updateScheduleEvent.value = response.isSuccess
            } catch (e: Exception) {
                Log.e("ScheduleViewModel", "반복 일정 단일 수정 실패: ${e.message}")
                _updateScheduleEvent.value = false
            }
        }
    }

    private suspend fun excludeOccurrenceFromRecurringSchedule(schedule: Schedule, date: LocalDate) {
        val dateString = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        val currentExDate = schedule.exDate ?: ""
        val exDateList = currentExDate.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val newExDate = if (exDateList.contains(dateString)) {
            currentExDate
        } else if (currentExDate.isEmpty()) {
            dateString
        } else {
            "$currentExDate,$dateString"
        }

        val updatedSeries = schedule.copy(exDate = newExDate)
        val spanDays = runCatching {
            ChronoUnit.DAYS.between(
                LocalDate.parse(schedule.startDate, dateFormatter),
                LocalDate.parse(schedule.endDate, dateFormatter)
            ).coerceAtLeast(0)
        }.getOrDefault(0)

        val scheduleForExDate = if (schedule.sourceType == "SYSTEM") {
            updatedSeries.copy(
                startDate = date.format(dateFormatter),
                endDate = date.plusDays(spanDays).format(dateFormatter)
            )
        } else {
            updatedSeries
        }

        repository.updateExDate(scheduleForExDate)
    }

    fun togglePinLocally(date: LocalDate, scheduleId: Long) {
        viewModelScope.launch {
            val currentSchedules = scheduleMap.value[date] ?: return@launch
            val targetSchedule = currentSchedules.find { it.id == scheduleId } ?: return@launch

            val newPinStatus = !targetSchedule.isPinned

            repository.updatePinStatus(scheduleId, newPinStatus)

            val updatedMap = scheduleMap.value.toMutableMap()
            val updatedList = currentSchedules.map {
                if (it.id == scheduleId) it.copy(isPinned = newPinStatus) else it
            }
            updatedMap[date] = updatedList

            _scheduleMap.value = updatedMap

            Log.d("PinUpdate", "DB 업데이트 완료: ${targetSchedule.title} -> $newPinStatus")
        }
    }

    private fun isScheduleNotFound(code: String?, message: String?): Boolean {
        return code == "SCHEDULE404_1" ||
            message == "SCHEDULE404_1" ||
            code == "해당 일정을 찾을 수 없습니다." ||
            message == "해당 일정을 찾을 수 없습니다."
    }

}

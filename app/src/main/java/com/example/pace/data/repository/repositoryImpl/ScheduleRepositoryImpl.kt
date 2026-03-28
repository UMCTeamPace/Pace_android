package com.example.pace.data.repository.repositoryImpl

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Color
import android.provider.CalendarContract
import android.util.Log
import biweekly.component.VEvent
import biweekly.property.DateStart
import biweekly.property.ExceptionDates
import biweekly.property.RecurrenceRule
import biweekly.util.DayOfWeek
import biweekly.util.Frequency
import biweekly.util.ICalDate
import biweekly.util.Recurrence
import com.example.pace.data.api.ScheduleService
import com.example.pace.data.createCalendarObserver
import com.example.pace.data.datasource.AuthDataStore
import com.example.pace.data.datasource.NormalScheduleRemoteDataSource
import com.example.pace.data.datasource.RouteScheduleRemoteDataSource
import com.example.pace.data.db.ScheduleDao
import com.example.pace.data.model.Schedule
import com.example.pace.data.model.request.*
import com.example.pace.data.model.response.*
import com.example.pace.data.repository.repository.ScheduleRepository
import com.example.pace.data.util.safeApiCall
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.*
import javax.inject.Inject
import kotlin.code

class ScheduleRepositoryImpl @Inject constructor(
    private val api: ScheduleService,
    private val scheduleDao: ScheduleDao,
    private val routeRemoteDataSource: RouteScheduleRemoteDataSource,
    private val normalDataSource: NormalScheduleRemoteDataSource,
    private val authDataStore: AuthDataStore,
    @ApplicationContext private val context: Context
) : ScheduleRepository {

    companion object {
        private val ROUTE_SOURCE_ZONE: ZoneId = ZoneId.of("Asia/Seoul")
        private val UTC_API_TIME_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        private const val SWAGGER_LOG_TAG = "SwaggerScheduleRequest"
        private const val LOG_CHUNK_SIZE = 3000
        private const val REPEAT_EXPAND_TAG = "RepeatExpand"
    }

    // 1. 로컬 데이터 Flow (RRULE 전개 로직 적용)
    override val allSchedules: Flow<List<Schedule>> = scheduleDao.getAllSchedules()
        .map { rawList -> expandSchedules(rawList) }
        .flowOn(Dispatchers.IO)

    override val calendarEvents: Flow<Unit> = createCalendarObserver(context)

    // 2. 로컬 DB 관리 메서드
    override suspend fun updateSchedule(schedule: Schedule) {
        withContext(Dispatchers.IO) {
            try {
                // 💡 [수정 포인트] 장소 데이터 정합성 맞추기
                // placeJson이 있는데 location이 비어있거나, 장소가 변경되었을 경우를 대비해 보정합니다.
                val calibratedSchedule = if (!schedule.placeJson.isNullOrEmpty()) {
                    try {
                        val placeData = Gson().fromJson(schedule.placeJson, com.example.pace.data.model.request.PlaceRequest::class.java)
                        // JSON 내의 targetName을 location 필드에 강제로 채워줍니다.
                        schedule.copy(location = placeData.targetName)
                    } catch (e: Exception) {
                        schedule
                    }
                } else {
                    schedule
                }

                if (calibratedSchedule.sourceType == "SYSTEM") {
                    // 1. 보정된 데이터로 시스템 캘린더 수정 (이제 location에 값이 확실히 존재함)
                    val isSystemUpdated = normalDataSource.updateCalendarEvent(calibratedSchedule)

                    if (isSystemUpdated) {
                        // 2. Room DB 업데이트
                        scheduleDao.updateSchedule(calibratedSchedule)
                        Log.d("UpdateLog", "✅ 시스템/로컬 DB 업데이트 완료 (장소: ${calibratedSchedule.location})")
                    } else {
                        Log.e("UpdateLog", "❌ 시스템 캘린더 업데이트 실패")
                    }
                } else {
                    scheduleDao.updateSchedule(calibratedSchedule)
                }
            } catch (e: Exception) {
                Log.e("UpdateLog", "일정 수정 중 오류 발생: ${e.message}")
            }
        }
    }

    override suspend fun updateExDate(schedule: Schedule) {
        withContext(Dispatchers.IO) {
            try {
                // 1. 만약 시스템 일정이라면 "삭제 행(Exception Event)"을 생성합니다.
                if (schedule.sourceType == "SYSTEM") {
                    val contentResolver = context.contentResolver

                    // 삭제하려는 날짜의 시작 시간 계산 (밀리초)
                    // schedule.startDate는 현재 "2026-02-26" 형태라고 가정
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    val startMillis = sdf.parse("${schedule.startDate} ${schedule.startTime}")?.time
                        ?: System.currentTimeMillis()

                    val values = ContentValues().apply {
                        // 핵심 1: 원본 일정의 TITLE, CALENDAR_ID 등을 복사
                        put(CalendarContract.Events.TITLE, schedule.title)
                        put(CalendarContract.Events.CALENDAR_ID, schedule.calendarId)
                        put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)

                        // 핵심 2: 원본 일정의 ID를 ORIGINAL_ID로 지정 (부모 연결)
                        put(CalendarContract.Events.ORIGINAL_ID, schedule.id)

                        // 핵심 3: 이 회차의 원래 시작 시간을 ORIGINAL_INSTANCE_TIME으로 지정
                        put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, startMillis)

                        // 핵심 4: 상태를 '취소됨(CANCELED)'으로 설정
                        put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CANCELED)

                        // 필수 값들
                        put(CalendarContract.Events.DTSTART, startMillis)
                        put(CalendarContract.Events.DTEND, startMillis + 3600000) // 1시간 뒤
                        put(CalendarContract.Events.ALL_DAY, if (schedule.isAllDay) 1 else 0)
                    }

                    // 시스템 DB에 '삭제 행' 삽입
                    val uri = contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                    Log.d("ExDateLog", "✅ 시스템에 삭제 행 생성 완료: $uri")

                    // 2. Room DB 동기화를 위해 즉시 refresh 호출 (또는 직접 insert)
                    // 이 삭제 행도 Room에 들어와야 expandSchedules가 인식함
                    refreshSchedules()
                }
                else {
                    // 우리 앱 전용 일정(DB)인 경우 기존처럼 exDate 문자열 방식으로 처리
                    scheduleDao.updateSchedule(schedule)
                }

            } catch (e: Exception) {
                Log.e("ExDateLog", "삭제 행 생성 실패: ${e.message}")
            }
        }
    }

    // [중요] 시스템 삭제분 정리 로직 구현 - 경로 일정 보호 추가
    override suspend fun cleanUpSystemDeletedSchedules() {
        withContext(Dispatchers.IO) {
            // 1. Room DB에서 기기 연동 일정(DEVICE)만 조회
            val localSystemSchedules = scheduleDao.getSchedulesWithSystemId()

            localSystemSchedules.forEach { schedule ->

                // 💡 [수정 포인트] 경로 포함 일정은 시스템 캘린더에 없어도 삭제하지 않음
                if (schedule.withRoute == true) {
                    Log.d("SyncCleanUp", "경로 포함 일정 보호 (삭제 건너뜀): ${schedule.title}")
                    return@forEach // 이 일정은 체크하지 않고 다음 일정으로 넘어감
                }

                // 2. 일반 일정인 경우에만 시스템 캘린더의 _ID로 조회
                val exists = checkEventExistsInProvider(schedule.id)

                if (!exists) {
                    scheduleDao.deleteScheduleById(schedule.id)
                    Log.d("SyncCleanUp", "시스템 삭제 감지 -> 로컬 DB 제거: ${schedule.title}")
                }
            }
        }
    }

    private fun checkEventExistsInProvider(eventId: Long): Boolean {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        val cursor = context.contentResolver.query(
            uri,
            arrayOf(CalendarContract.Events._ID),
            null, null, null
        )
        val exists = (cursor?.count ?: 0) > 0
        cursor?.close()
        return exists
    }

    override suspend fun refreshSchedules() {
        try {
            val systemSchedules = normalDataSource.getSchedules()
            val systemIds = systemSchedules.map { it.id }

            withContext(Dispatchers.IO) {
                if (systemSchedules.isNotEmpty()) {
                    val localSchedulesOnce = scheduleDao.getAllSchedulesOnce()
                    val localScheduleMap = localSchedulesOnce.associateBy { it.id }

                    val mergedSchedules = systemSchedules.map { remote ->
                        val local = localScheduleMap[remote.id]

                        if (local != null) {
                            // 💡 [핵심] 시스템 데이터(remote)를 업데이트하되,
                            // 우리 앱 전용 필드(local)는 절대로 잃어버리지 않게 수동으로 다 넣어줍니다.
                            remote.copy(
                                // 1. 우리 앱의 고유 상태 보존
                                isPinned = local.isPinned,
                                isCompleted = local.isCompleted,
                                isSwiped = local.isSwiped,

                                // 2. [가장 중요] 장소 정보 및 리마인더 보존
                                placeJson = local.placeJson,
                                departureReminders = local.departureReminders,
                                reminders = if (remote.reminders.isEmpty()) local.reminders else remote.reminders,

                                // 3. 타입 정보 유지
                                type = local.type,
                                sourceType = local.sourceType,
                                serverId = local.serverId,
                                routeId = local.routeId,

                                // 4. 시스템에서 변경될 수 있는 값들은 remote를 따름
                                calendarId = if (remote.calendarId == 0L) local.calendarId else remote.calendarId,
                                eventColor = remote.eventColor ?: local.eventColor
                            )
                        } else {
                            remote
                        }
                    }

                    // 여기서 insertAll 할 때, 위에 가공된(Pace 데이터가 살아있는) 리스트가 들어감
                    scheduleDao.insertAll(mergedSchedules)
                    Log.d("SYNC_LOG", "1. 필드 보존하며 시스템 일정 업데이트 완료")
                }

                if (systemIds.isNotEmpty()) {
                    scheduleDao.deleteRemovedDeviceSchedules(systemIds)
                }
            }
        } catch (e: Exception) {
            Log.e("SYNC_LOG", "❌ 동기화 중 오류: ${e.message}")
        }
    }

    override fun getUsedColors(): Flow<List<String>> = scheduleDao.getUsedColorsRaw().map { list ->
        list.mapNotNull { it.color }
    }

    // 3. 서버 API 메서드 (safeApiCall 활용)
    override suspend fun getScheduleList(
        accessToken: String,
        startDate: String,
        endDate: String?,
        lastDate: String?,
        lastId: Long?
    ) = safeApiCall {
        val response = api.getScheduleList(accessToken, startDate, endDate, lastDate, lastId)
        if (response.isSuccess && response.result != null) {
            val serverSchedules = response.result.content

            serverSchedules.forEach { dto ->
                val existing = scheduleDao.getScheduleById(dto.scheduleId)
                val entity = dto.toScheduleEntity()

                // 💡 핵심: 기존 데이터가 '경로 포함'인데 서버 데이터가 '일반'이라면 보호한다.
                if (existing != null && existing.withRoute == true) {
                    // 기존의 경로 데이터와 withRoute 상태를 유지한 채 필요한 부분만 업데이트
                    val protectedEntity = entity.copy(
                        withRoute = true,
                        // 필요한 경우 route 데이터도 여기서 복사 (existing.route)
                    )
                    scheduleDao.insertSingle(protectedEntity) // 개별 insert (REPLACE)
                } else {
                    scheduleDao.insertSingle(entity)
                }
            }
        }
        response
    }

    override suspend fun getScheduleDetail(accessToken: String, scheduleId: Long) = safeApiCall {
        api.getScheduleDetail(accessToken, scheduleId)
    }

    override suspend fun updateSchedule(
        accessToken: String,
        scheduleId: Long,
        scope: String,
        request: UpdateScheduleRequest
    ): RawDefaultResponse<ScheduleDetailResponse> = safeApiCall {
        // api.updateSchedule이 이미 ScheduleService에서 RawDefaultResponse<ScheduleDetailResponse>를 반환하므로 바로 리턴
        api.updateSchedule(accessToken, scheduleId, scope, request)
    }

    override suspend fun deleteSchedules(accessToken: String, request: DeleteScheduleRequest) = safeApiCall {
        api.deleteSchedules(accessToken, request)
    }

    override suspend fun updateScheduleRoute(accessToken: String, scheduleId: Long, request: UpdateScheduleRouteRequest) = safeApiCall {
        api.updateScheduleRoute(accessToken, scheduleId, request)
    }

    override suspend fun deleteScheduleRoute(accessToken: String, scheduleId: Long) = safeApiCall {
        api.deleteScheduleRoute(accessToken, scheduleId)
    }

    override suspend fun convertRouteToGeneral(accessToken: String, id: Long) = safeApiCall {
        api.convertRouteToGeneral(accessToken, id)
    }

    override suspend fun searchSchedules(
        query: String,
        colors: Set<String>,
        includeRoute: Boolean,
        startDate: String,
        endDate: String,
        selectedIds: List<Long> // ViewModel에서 넘겨받은 값
    ): List<Schedule> {
        val raw = scheduleDao.searchSchedulesWithRange("%$query%", startDate, endDate)

        return expandSchedules(raw).filter { schedule ->
            // 1. 캘린더 필터링 (백엔드 일정은 통과, 일반 일정만 체크)
            val calendarMatch = if (schedule.type == "ROUTE") {
                true // 💡 백엔드(경로) 일정은 필터링을 걸지 않음
            } else {
                // 일반 일정은 선택된 리스트에 있거나, 리스트가 비어있을 때만 노출
                selectedIds.isEmpty() || selectedIds.contains(schedule.calendarId)
            }

            // 2. 기존 필터링 로직 (색상, 경로 포함 여부)
            val sColor = schedule.eventColor?.toString() ?: ""
            val cColor = schedule.calendarColor?.toString() ?: ""
            val colorMatch = colors.isEmpty() || colors.any { it.equals(sColor, true) || it.equals(cColor, true) }
            val routeMatch = includeRoute || schedule.type != "ROUTE"

            // 💡 모든 조건 결합
            calendarMatch && colorMatch && routeMatch
        }
    }

    override suspend fun createSchedule(
        accessToken: String?,
        request: CreateScheduleRequest,
        placeId: String?,
        calendarId: Long?,
        selectedColor: Int?
    ) = safeApiCall {
        val defaultColorInt = android.graphics.Color.parseColor("#DC354B")
        val finalColor = selectedColor ?: defaultColorInt

        // 💡 공통: 0 방어 및 캘린더 정보 즉석 쿼리 (함수 사용 안함)
        val targetId = if ((calendarId ?: 0L) <= 0L) 1L else calendarId!!
        var dName = "내 일정"
        var aName = "Pace"

        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME
            ),
            "${CalendarContract.Calendars._ID} = ?",
            arrayOf(targetId.toString()),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                dName = cursor.getString(0) ?: dName
                aName = cursor.getString(1) ?: aName
            }
        }

        if (request.route == null) {
            // --- [CASE 1] 일반 일정: 시스템 캘린더 및 로컬 DB 저장 ---
            val systemId = normalDataSource.insertToCalendarProvider(
                request = request,
                selectedCalendarId = targetId, // 0 대신 보정된 ID 사용
                selectedColor = finalColor
            )

            if (systemId != -1L) {
                val localSchedule = Schedule(
                    id = systemId,
                    title = request.title,
                    startDate = request.startDate,
                    endDate = request.endDate,
                    startTime = request.startTime ?: "00:00",
                    endTime = request.endTime ?: "23:59",
                    isAllDay = request.isAllDay,
                    memo = request.memo,
                    location = request.place?.targetName,
                    placeJson = if (placeId != null) {
                        "{\"placeId\":\"$placeId\"}"
                    } else if (request.place != null) {
                        Gson().toJson(request.place)
                    } else {
                        null
                    },

                    // 💡 동적으로 가져온 정보 적용
                    calendarId = targetId,
                    calendarDisplayName = dName,
                    calendarAccountName = aName,

                    reminders = request.reminders.map { it.minutesBefore },
                    withRoute = (placeId != null),
                    isCompleted = false,
                    isPinned = false,
                    isSwiped = false,
                    type = "NORMAL",
                    eventColor = finalColor,
                    calendarColor = finalColor,
                    sourceType = "SYSTEM",
                    serverId = null,
                    routeId = null,
                    repeatRule = buildRRuleFromRequest(request.repeatInfo),
                    exDate = null
                )
                scheduleDao.insertAll(listOf(localSchedule))

                RawDefaultResponse(
                    isSuccess = true,
                    code = "COMMON200",
                    message = "로컬 일정 저장 성공",
                    result = null
                )
            } else {
                RawDefaultResponse(
                    isSuccess = false,
                    code = "LOCAL_ERROR",
                    message = "저장 실패",
                    result = null
                )
            }
        } else {
            // --- [CASE 2] 경로 일정: 서버 API 호출 ---
            val token = accessToken ?: ""
            val routeRequest = request.toCreateRouteScheduleRequest(calendarId)
            val response = api.createRouteSchedule(
                token,
                routeRequest
            )

            if (response.isSuccess && response.result != null) {
                val serverResult = response.result
                val info = serverResult.scheduleInfo // 💡 서버가 준 핵심 정보
                val routeData = serverResult.route // 서버가 준 경로 데이터
                val gson = Gson()
                val allReminders = serverResult.reminders ?: emptyList()
                Log.d("DEBUG_DATA", "전체 리마인더 개수: ${allReminders.size}")
                val eventReminders = allReminders
                    .filter { it.reminderType == "EVENT" }
                    .map { it.minutesBefore }

                val departureReminders = allReminders
                    .filter { it.reminderType == "DEPARTURE" }
                    .map { it.minutesBefore }
                Log.d("DEBUG_DATA", "추출된 출발 알람: $departureReminders")
                val placeAndRouteJson = if (routeData != null) {
                    gson.toJson(routeData)
                } else {
                    gson.toJson(serverResult.place)
                }
                Log.d("DEBUG_DATA", "JSON 생성 여부: ${placeAndRouteJson != null}")

                // 1. 서버가 준 캘린더 ID를 최우선으로 가져옴 (DTO가 String이므로 변환)
                val serverCalendarId = info.calendarId?.toLongOrNull() ?: calendarId ?: 1L

                // 2. 해당 ID로 시스템 캘린더 정보 즉시 조회 (동적 이름 취득)
                var dName = "내 일정"
                var aName = "Pace"
                context.contentResolver.query(
                    CalendarContract.Calendars.CONTENT_URI,
                    arrayOf(
                        CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                        CalendarContract.Calendars.ACCOUNT_NAME
                    ),
                    "${CalendarContract.Calendars._ID} = ?",
                    arrayOf(serverCalendarId.toString()),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        dName = cursor.getString(0) ?: dName
                        aName = cursor.getString(1) ?: aName
                    }
                }

                // 3. 룸 DB용 엔티티 직접 생성 (toEntity를 쓰지 않고 여기서 다 채움)
                val colorHex = info.color ?: "#DC354B"
                val colorInt = try {
                    Color.parseColor(colorHex)
                } catch (e: Exception) {
                    finalColor
                }


                val displayLocation = if (routeData != null) {
                    // 로그에 찍힌 routeData의 정보를 조합
                    "${routeData.originName} → ${routeData.destName}"
                } else {
                    serverResult.place?.targetName ?: request.place?.targetName ?: info.title ?: "장소 정보 없음"
                }

                val scheduleToSave = Schedule(
                    id = serverResult.scheduleId, // 서버 ID를 Primary Key로 사용
                    title = info.title,
                    startDate = info.startDate,
                    endDate = info.endDate,
                    startTime = info.startTime?.take(5) ?: "00:00",
                    endTime = info.endTime?.take(5) ?: "23:59",
                    isAllDay = info.isAllDay,
                    memo = info.memo,
                    location = displayLocation,


                    calendarId = serverCalendarId,
                    calendarDisplayName = dName,
                    calendarAccountName = aName,

                    eventColor = colorInt,
                    calendarColor = colorInt,

                    reminders = eventReminders,
                    departureReminders = departureReminders, // 새로 추가한 필드에 저장
                    placeJson = placeAndRouteJson,

                    withRoute = true,
                    type = "ROUTE",
                    sourceType = "SERVER",
                    serverId = serverResult.scheduleId,
                    isCompleted = false,
                    isPinned = false,
                    isSwiped = false,
                    repeatRule = null,
                    exDate = null
                )

                // 💡 [추가] DB 삽입 직전, 객체 내부 상태를 낱낱이 파헤쳐봅시다.
                Log.d("SAVE_DEBUG", """
                ========= 삽입 데이터 최종 확인 =========
                일정 제목: ${scheduleToSave.title}
                이벤트 리마인더 (EVENT): ${scheduleToSave.reminders}
                출발 리마인더 (DEPARTURE): ${scheduleToSave.departureReminders}
                장소(Location): ${scheduleToSave.location}  <-- 이걸 꼭 찍어보세요!
                장소 JSON (placeJson): ${scheduleToSave.placeJson?.take(100)}... (일부출력)
                경로 타입 여부: ${scheduleToSave.type}
                ======================================
            """.trimIndent())

                // 4. 로컬 DB에 삽입
                scheduleDao.insertAll(listOf(scheduleToSave))

                // 2. [중요] 저장이 끝난 뒤에 성공 응답을 반환합니다.
                // 하지만 이렇게 해도 ViewModel에서 바로 refresh를 호출하면 덮어쓰기 위험이 있습니다.
                return@safeApiCall response
            } else {
                return@safeApiCall response
            }
        }
    }

    private fun CreateScheduleRequest.toCreateRouteScheduleRequest(
        calendarId: Long?
    ): CreateRouteScheduleRequest {
        return CreateRouteScheduleRequest(
            title = title,
            startDate = startDate,
            endDate = endDate,
            startTime = startTime,
            endTime = endTime,
            calendarId = calendarId?.toString() ?: this.calendarId,
            color = color,
            memo = memo,
            isPathIncluded = isPathIncluded,
            place = place,
            reminders = reminders,
            route = requireNotNull(route) { "Route schedule request requires route" }.normalizeRouteTimesForApi()
        )
    }

    private fun RouteRequest.normalizeRouteTimesForApi(): RouteRequest {
        return copy(
            arrivalTime = arrivalTime.toUtcIsoString(),
            departureTime = departureTime.toUtcIsoString(),
            routeDetails = routeDetails.map { detail ->
                detail.copy(
                    transitDetail = detail.transitDetail?.copy(
                        departureTime = detail.transitDetail.departureTime.toUtcIsoString(),
                        arrivalTime = detail.transitDetail.arrivalTime.toUtcIsoString()
                    )
                )
            }
        )
    }

    private fun UpdateScheduleEditRouteRequest.normalizeRouteTimesForApi(): UpdateScheduleEditRouteRequest {
        return copy(
            arrivalTime = arrivalTime.toUtcIsoString(),
            departureTime = departureTime.toUtcIsoString(),
            routeDetails = routeDetails.map { detail ->
                detail.copy(
                    transitDetail = detail.transitDetail?.copy(
                        departureTime = detail.transitDetail.departureTime.toUtcIsoString(),
                        arrivalTime = detail.transitDetail.arrivalTime.toUtcIsoString()
                    )
                )
            }
        )
    }

    private fun String?.toUtcIsoString(): String? {
        if (this.isNullOrBlank()) return this
        return try {
            if (endsWith("Z")) {
                OffsetDateTime.parse(this)
                    .withOffsetSameInstant(ZoneOffset.UTC)
                    .format(UTC_API_TIME_FORMATTER)
            } else {
                LocalDateTime.parse(this)
                    .atZone(ROUTE_SOURCE_ZONE)
                    .withZoneSameInstant(ZoneOffset.UTC)
                    .format(UTC_API_TIME_FORMATTER)
            }
        } catch (_: DateTimeParseException) {
            this
        }
    }

    override fun getCalendarName(calendarId: Long): String? {
        val projection = arrayOf(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
        val uri = CalendarContract.Calendars.CONTENT_URI
        val selection = "${CalendarContract.Calendars._ID} = ?"
        val selectionArgs = arrayOf(calendarId.toString())

        return context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    override fun getCalendarColor(calendarId: Long): Int? {
        val projection = arrayOf(CalendarContract.Calendars.CALENDAR_COLOR)
        val uri = CalendarContract.Calendars.CONTENT_URI
        val selection = "${CalendarContract.Calendars._ID} = ?"
        val selectionArgs = arrayOf(calendarId.toString())

        return context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else null
        }
    }

    // --- 반복 일정 전개 및 헬퍼 함수 (기존 유지) ---
    private fun expandSchedules(rawSchedules: List<Schedule>): List<Schedule> {
        val expandedList = mutableListOf<Schedule>()
        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        val currentLocalDate = LocalDate.now()
        val rangeStartLocalDate = currentLocalDate.minusYears(2)
        val rangeEndLocalDate = currentLocalDate.plusYears(2)

        val rangeStartDate = Date.from(rangeStartLocalDate.atStartOfDay(ZoneId.systemDefault()).toInstant())
        val rangeEndDate = Date.from(rangeEndLocalDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant())

        // 💡 [추가] 삼성/구글에서 '행'으로 생성된 삭제 정보들만 모읍니다.
        // Key: 부모ID(originalId), Value: 삭제된 날짜 리스트(startDate)
        val cancellationMap = rawSchedules
            .filter { it.status == 2 && it.originalId != 0L }
            .groupBy({ it.originalId }, { it.startDate })

        // 💡 [추가] 화면에 실제로 그릴 수 있는 일정들(status가 2가 아닌 것)만 순회합니다.
        val activeSchedules = rawSchedules.filter { it.status != 2 }

        activeSchedules.forEach { schedule ->
            val startLocalDate = try {
                LocalDate.parse(schedule.startDate, dateFormatter)
            } catch (e: Exception) {
                Log.e("ExpandLog", "시작 날짜 파싱 실패: ${schedule.startDate}")
                return@forEach
            }
            val endLocalDate = try {
                LocalDate.parse(schedule.endDate, dateFormatter)
            } catch (e: Exception) {
                startLocalDate
            }

            // 1. 반복 일정 처리
            if (!schedule.repeatRule.isNullOrBlank()) {
                val dtStartString = "${schedule.startDate} 00:00"
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).apply {
                    if (schedule.isAllDay) {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }
                }
                val dtStartDate = try { sdf.parse(dtStartString) } catch (e: Exception) { null }
                val spanDays = ChronoUnit.DAYS.between(startLocalDate, endLocalDate).coerceAtLeast(0)

                if (dtStartDate != null) {
                    try {
                        val event = VEvent()
                        event.setDateStart(DateStart(dtStartDate))

                        val recur = parseRecurrenceString(schedule.repeatRule!!)
                        if (recur != null) event.setRecurrenceRule(RecurrenceRule(recur))

                        // 기존 exDate 문자열 처리
                        val exdates = ExceptionDates()
                        if (!schedule.exDate.isNullOrBlank()) {
                            schedule.exDate.split(',').forEach { dateStr ->
                                val trimmed = dateStr.trim()
                                if (trimmed.isEmpty()) return@forEach

                                try {
                                    // 💡 수정: 모든 기호 제거 후 숫자 8자리(yyyyMMdd)만 추출
                                    val numericOnly = trimmed.replace(Regex("[^0-9]"), "")
                                    if (numericOnly.length >= 8) {
                                        val yyyyMMdd = numericOnly.substring(0, 8)
                                        val dateOnly = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).parse(yyyyMMdd)
                                        dateOnly?.let {
                                            val cal = Calendar.getInstance().apply {
                                                time = it
                                                set(Calendar.HOUR_OF_DAY, 0)
                                                set(Calendar.MINUTE, 0)
                                                set(Calendar.SECOND, 0)
                                                set(Calendar.MILLISECOND, 0)
                                            }
                                            exdates.getValues().add(ICalDate(cal.time, false))
                                        }
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("ScheduleRepository", "EXDATE 파싱 최종 실패: $trimmed")
                                }
                            }
                            if (exdates.getValues().isNotEmpty()) {
                                event.addExceptionDates(exdates)
                            }
                        }

                        // 💡 [추가] 맵에 담아둔 삼성/구글의 '삭제 행' 날짜들도 exdates 객체에 통합
                        cancellationMap[schedule.id]?.forEach { deletedDateStr ->
                            try {
                                val sdfSimple = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                val dateObj = sdfSimple.parse(deletedDateStr)
                                dateObj?.let {
                                    val cal = Calendar.getInstance().apply {
                                        time = it
                                        set(Calendar.HOUR_OF_DAY, 0)
                                        set(Calendar.MINUTE, 0)
                                        set(Calendar.SECOND, 0)
                                        set(Calendar.MILLISECOND, 0)
                                    }
                                    exdates.getValues().add(ICalDate(cal.time, false))
                                }
                            } catch (e: Exception) {
                                Log.e("ExpandLog", "삭제행 날짜 통합 실패: $deletedDateStr")
                            }
                        }

                        if (exdates.getValues().isNotEmpty()) {
                            event.addExceptionDates(exdates)
                        }

                        // 전개 시작
                        val iterator = event.getDateIterator(
                            if (schedule.isAllDay) TimeZone.getTimeZone("UTC") else TimeZone.getDefault()
                        )
                        iterator.advanceTo(rangeStartDate)
                        var count = 0
                        while (iterator.hasNext() && count < 1000) {
                            val occurrenceDate = iterator.next()
                            if (occurrenceDate.after(rangeEndDate)) break

                            val occurrenceLocalDate = if (schedule.isAllDay) {
                                occurrenceDate.toInstant().atZone(ZoneOffset.UTC).toLocalDate()
                            } else {
                                occurrenceDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
                            }
                            val occurrenceEndLocalDate = occurrenceLocalDate.plusDays(spanDays)
                            val formattedDate = occurrenceLocalDate.format(dateFormatter)
                            if (schedule.isAllDay) {
                                Log.d(
                                    REPEAT_EXPAND_TAG,
                                    "반복 확장: id=${schedule.id}, title=${schedule.title}, 원본=${schedule.startDate}~${schedule.endDate}, occurrence=${formattedDate}~${occurrenceEndLocalDate.format(dateFormatter)}, rawInstant=${occurrenceDate.toInstant()}"
                                )
                            }

                            // 💡 [중요] ICal4j 라이브러리에 따라 addExceptionDates가 완벽히 필터링 못할 경우를 대비한 2중 체크
                            // cancellationMap에 해당 부모ID와 현재 날짜가 등록되어 있다면 건너뜁니다.
                            val systemDeletedDates = cancellationMap[schedule.id] ?: emptyList()
                            if (systemDeletedDates.contains(formattedDate)) {
                                continue
                            }

                            expandedList.add(schedule.copy(
                                startDate = formattedDate,
                                endDate = occurrenceEndLocalDate.format(dateFormatter),
                                startTime = if (schedule.isAllDay) "00:00" else schedule.startTime,
                                endTime = if (schedule.isAllDay) "23:59" else schedule.endTime
                            ))
                            count++
                        }
                    } catch (e: Exception) {
                        Log.e("ExpandLog", "반복 전개 오류: ${e.message}")
                        if (startLocalDate.isBefore(rangeEndLocalDate) && endLocalDate.isAfter(rangeStartLocalDate)) {
                            expandedList.add(schedule)
                        }
                    }
                }
            }
            // 2. 단일 기간제 일정
            else if (startLocalDate.isBefore(endLocalDate)) {
                var current = startLocalDate
                while (!current.isAfter(endLocalDate)) {
                    if (!current.isBefore(rangeStartLocalDate) && current.isBefore(rangeEndLocalDate)) {
                        expandedList.add(schedule.copy(startDate = current.format(dateFormatter), endDate = current.format(dateFormatter)))
                    }
                    current = current.plusDays(1)
                }
            }
            // 3. 일반 단일 일정
            else {
                if (!startLocalDate.isBefore(rangeStartLocalDate) && startLocalDate.isBefore(rangeEndLocalDate)) {
                    expandedList.add(schedule)
                }
            }
        }
        return expandedList
    }

    private fun parseRecurrenceString(rruleStr: String): Recurrence? {
        return try {
            val cleanRrule = rruleStr.replace("RRULE:", "").trim()
            val parts = cleanRrule.split(";")
            val params = parts.associate {
                val split = it.split("=")
                if (split.size == 2) split[0].uppercase() to split[1] else "" to ""
            }

            val freqStr = params["FREQ"] ?: return null
            val builder = Recurrence.Builder(Frequency.valueOf(freqStr))

            params["INTERVAL"]?.toIntOrNull()?.let { builder.interval(it) }
            params["COUNT"]?.toIntOrNull()?.let { builder.count(it) }

            // UNTIL(날짜 종료) 파싱 부분 수정
            params["UNTIL"]?.let { untilStr ->
                try {
                    // 1. format 결정 (if-else 식을 명확히 정의)
                    val format = if (untilStr.contains("T")) {
                        SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.getDefault()).apply {
                            timeZone = TimeZone.getTimeZone("UTC")
                        }
                    } else {
                        SimpleDateFormat("yyyyMMdd", Locale.getDefault())
                    }

                    // 2. 파싱 및 적용
                    val date = format.parse(untilStr)
                    if (date != null) {
                        builder.until(ICalDate(date, untilStr.contains("T")))
                    } else{

                    }
                } catch (e: Exception) {
                    Log.e("RRULE_PARSE", "UNTIL 파싱 실패: $untilStr", e)
                }
            }

            params["BYDAY"]?.let { byDayStr ->
                byDayStr.split(",").forEach { dayCode ->
                    val dayOfWeek = when(dayCode.takeLast(2)) {
                        "SU" -> DayOfWeek.SUNDAY; "MO" -> DayOfWeek.MONDAY; "TU" -> DayOfWeek.TUESDAY
                        "WE" -> DayOfWeek.WEDNESDAY; "TH" -> DayOfWeek.THURSDAY; "FR" -> DayOfWeek.FRIDAY
                        "SA" -> DayOfWeek.SATURDAY; else -> null
                    }
                    if(dayOfWeek != null) builder.byDay(dayOfWeek)
                }
            }
            builder.build()
        } catch (e: Exception) { null }
    }

    // 변환 확장 함수들
    private fun CreateScheduleResponse.toEntity(selectedColorStr: String, fallbackCalendarId: Long?): Schedule {
        val info = this.scheduleInfo
        val finalColorHex = info.color ?: selectedColorStr
        val colorInt = try { android.graphics.Color.parseColor(finalColorHex) } catch (e: Exception) { android.graphics.Color.RED }

        // 1. [중요] 0L 방어 및 캘린더 ID 확정
        val finalId = info.calendarId?.toLongOrNull() ?: fallbackCalendarId ?: 1L

        // 2. 시스템 캘린더 정보 즉석 쿼리
        var dName = "내 일정"
        var aName = "Pace"
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, CalendarContract.Calendars.ACCOUNT_NAME),
            "${CalendarContract.Calendars._ID} = ?",
            arrayOf(finalId.toString()),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                dName = cursor.getString(0) ?: dName
                aName = cursor.getString(1) ?: aName
            }
        }

        return Schedule(
            id = this.scheduleId,
            title = info.title,
            startDate = info.startDate,
            endDate = info.endDate,
            startTime = info.startTime?.take(5) ?: "00:00",
            endTime = info.endTime?.take(5) ?: "23:59",
            isAllDay = info.isAllDay,
            memo = info.memo,
            location = this.place?.targetName,

            // 💡 3. 하드코딩 대신 동적으로 가져온 값 저장
            calendarId = finalId,
            calendarDisplayName = dName,
            calendarAccountName = aName,

            withRoute = true,
            type = "ROUTE",
            eventColor = colorInt,
            calendarColor = colorInt,
            isCompleted = false,
            isPinned = false,
            isSwiped = false,
            sourceType = "SERVER",
            repeatRule = null,
            exDate = null,
            serverId = this.scheduleId
        )
    }

    private fun ScheduleItem.toScheduleEntity(): Schedule {
        val colorHex = this.scheduleInfo.color ?: "#DC354B"
        val colorInt = try {
            android.graphics.Color.parseColor(colorHex)
        } catch (e: Exception) {
            android.graphics.Color.parseColor("#DC354B")
        }
        val serverCalendarId = this.scheduleInfo.calendarId?.toLongOrNull() ?: 1L

        // 3. 타입 판별 (경로 포함 여부)
        val isRouteType = (this.scheduleInfo.isPathIncluded == true) || (this.route != null)

        val allReminders = this.reminders ?: emptyList()
        val eventRemindersList = allReminders
            .filter { it.reminderType == "EVENT" }
            .map { it.minutesBefore }
        val departureRemindersList = allReminders
            .filter { it.reminderType == "DEPARTURE" }
            .map { it.minutesBefore }

        // 💡 2. 덮어쓰기 방지! JSON 바구니에 압축해서 담기
        val gson = Gson()
        val placeJsonString = this.place?.let { gson.toJson(it) }
        val routeJsonString = this.route?.let { gson.toJson(it) }

        return Schedule(
            id = this.scheduleId,
            title = this.scheduleInfo.title,
            startDate = this.scheduleInfo.startDate,
            endDate = this.scheduleInfo.endDate,
            startTime = this.scheduleInfo.startTime?.take(5) ?: "00:00",
            endTime = this.scheduleInfo.endTime?.take(5) ?: "23:59",
            isAllDay = this.scheduleInfo.isAllDay,
            memo = this.scheduleInfo.memo,

            // 경로 일정이면 도착지 이름을, 아니면 장소 이름을 위치로 지정
            location = this.route?.destName ?: this.place?.targetName,

            calendarId = serverCalendarId,
            eventColor = colorInt,
            calendarColor = colorInt,
            calendarDisplayName = "내 일정",
            calendarAccountName = "Pace",

            // 알림과 경로 상세 필드 추가
            reminders = eventRemindersList,
            departureReminders = departureRemindersList,
            placeJson = placeJsonString,
            routeJson = routeJsonString,

            withRoute = isRouteType,
            type = if (isRouteType) "ROUTE" else "NORMAL",
            isCompleted = false,
            isPinned = false,
            isSwiped = false,
            sourceType = "SERVER",
            serverId = this.scheduleId,
            repeatRule = null,
            exDate = null
        )
    }

    private fun buildRRuleFromRequest(info: RepeatInfo?): String? {
        // 1. 반복 정보가 없으면 즉시 null 반환 (Statement로서의 return)
        if (info == null || info.repeatType.uppercase() == "NONE") return null

        return try {
            val rrule = StringBuilder("FREQ=${info.repeatType.uppercase()}")

            // 1. 간격
            if (info.repeatInterval > 1) {
                rrule.append(";INTERVAL=${info.repeatInterval}")
            }

            // 2. 요일
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
                if (days.isNotEmpty()) {
                    rrule.append(";BYDAY=$days")
                }
            }

            // 3. 종료 조건 (when 문을 식이 아닌 문장으로 사용)
            when (info.endType.uppercase()) {
                "COUNT" -> {
                    val count = info.endCount
                    if (count != null && count > 0) {
                        rrule.append(";COUNT=$count")
                    }
                }
                "DATE" -> {
                    val endDate = info.repeatEndDate
                    if (!endDate.isNullOrEmpty()) {
                        val untilDate = endDate.replace("-", "")
                        rrule.append(";UNTIL=${untilDate}T235959Z")
                    }
                }
                else -> {
                    // 아무것도 하지 않음 (무한 반복)
                }
            }

            // 최종 문자열 반환
            rrule.toString()
        } catch (e: Exception) {
            Log.e("RRULE_ERROR", "RRULE 생성 실패: ${e.message}")
            null
        }
    }

    override suspend fun getScheduleListForRoute(
        accessToken: String,
        startDate: String,
        endDate: String?
    ): RawDefaultResponse<RouteOnlyScheduleData?> {

        // 실제 Retrofit Service 호출 (Service의 함수 이름은 getScheduleList 였음)
        val response = api.getScheduleList(
            accessToken = accessToken,
            startDate = startDate,
            endDate = null,
            lastDate = null,
            lastId = null
        )
        val nowTime = java.time.LocalTime.now()

        // 2. 변환 로직
        val mappedData: RouteOnlyScheduleData? = response.result?.content
            ?.filter { item -> // 1. 일단 조건 맞는거 다 찾기 (find -> filter)
                val isRouteItem = item.place == null && item.route != null

                val isFuture = try {
                    val itemTime = java.time.LocalTime.parse(item.scheduleInfo.startTime)
                    itemTime.isAfter(nowTime)
                } catch (e: Exception) {
                    false
                }
                isRouteItem && isFuture
            }
            ?.minByOrNull { item -> // 2. 그 중에서 시간이 가장 빠른(작은) 것 찾기
                item.scheduleInfo.startTime ?: "23:59:59"
            }
            ?.let { item ->
                val route = item.route
                val flattenedDetails = route?.routeDetails?.map { detail ->
                    // 안쪽 객체의 값을 바깥쪽 변수들로 복사 (Flattening)
                    detail.copy(
                        transitType = detail.transitDetail?.transitType,
                        lineColor = detail.transitDetail?.lineColor,
                        lineName = detail.transitDetail?.lineName,
                        shortName = detail.transitDetail?.shortName,
                        departureStop = detail.transitDetail?.departureStop
                    )
                }

                RouteOnlyScheduleData(
                    scheduleId = item.scheduleId,
                    scheduleInfo = item.scheduleInfo,
                    route = item.route
                )
            }

        return RawDefaultResponse(
            code = response.code,
            message = response.message,
            isSuccess = response.isSuccess,
            result = mappedData
        )
    }

    override suspend fun getAllRouteSchedules(
        accessToken: String,
        startDate: String,
        endDate: String?
    ): RawDefaultResponse<List<RouteOnlyScheduleData?>> {

        val response = api.getScheduleList(
            accessToken = accessToken,
            startDate = startDate,
            endDate = endDate,
            lastDate = null,
            lastId = null
        )

        val mappedList: List<RouteOnlyScheduleData?> = response.result?.content
            ?.filter { it.place == null && it.route != null }
            ?.map { item ->
                val route = item.route
                val flattenedDetails = route?.routeDetails?.map { detail ->
                    detail.copy(
                        transitType = detail.transitDetail?.transitType,
                        lineColor = detail.transitDetail?.lineColor,
                        lineName = detail.transitDetail?.lineName,
                        shortName = detail.transitDetail?.shortName,
                        departureStop = detail.transitDetail?.departureStop
                    )
                }

                RouteOnlyScheduleData(
                    scheduleId = item.scheduleId,
                    scheduleInfo = item.scheduleInfo,
                    route = route?.copy(routeDetails = flattenedDetails)
                )
            } ?: emptyList()

        return RawDefaultResponse(
            code = response.code,
            message = response.message,
            isSuccess = response.isSuccess,
            result = mappedList
        )
    }


    // 일반 일정 삭제 구현
    override suspend fun deleteNormalSchedule(id: Long): RawDefaultResponse<String> {
        return withContext(Dispatchers.IO) {
            try {
                // 1. 시스템 캘린더 프로바이더에서 삭제
                val deleteUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
                val deletedRows = context.contentResolver.delete(deleteUri, null, null)

                // 2. 로컬 DB(Room)에서 삭제
                scheduleDao.deleteScheduleById(id)

                Log.d("DeleteLog", "일반 일정 삭제 완료: ID=$id, Provider 삭제 행=$deletedRows")
                RawDefaultResponse(isSuccess = true, code = "200", message = "기기 일정 삭제 완료", result = "SUCCESS")
            } catch (e: Exception) {
                Log.e("DeleteLog", "일반 일정 삭제 중 오류: ${e.message}")
                RawDefaultResponse(isSuccess = false, code = "LOCAL_ERROR", message = e.message ?: "Unknown Error", result = null)
            }
        }
    }

    // 경로 일정 삭제 구현
    override suspend fun deleteRouteSchedule(id: Long): RawDefaultResponse<String> {
        return withContext(Dispatchers.IO) {
            try {
                val token = authDataStore.getAccessToken() ?: ""
                // [추가] 토큰 접두사 처리 (서버 사양에 따라 필요할 수 있음)
                val fullToken = if (token.isNotEmpty() && !token.startsWith("Bearer ")) "Bearer $token" else token

                val request = DeleteScheduleRequest(scheduleIds = listOf(id))
                val response = api.deleteSchedules(fullToken, request)

                if (response.isSuccess) {
                    // 서버 삭제 성공 시에만 로컬 DB에서 제거
                    scheduleDao.deleteScheduleById(id)
                    Log.d("DeleteLog", "경로 일정 서버/로컬 삭제 완료: ID=$id")
                } else {
                    Log.e("DeleteLog", "경로 일정 서버 삭제 실패: ${response.message}")
                }
                response
            } catch (e: Exception) {
                Log.e("DeleteLog", "경로 일정 삭제 중 예외 발생: ${e.message}")
                RawDefaultResponse(isSuccess = false, code = "SERVER_ERROR", message = e.message ?: "Unknown Error", result = null)
            }
        }
    }
    override suspend fun getScheduleById(id: Long): Schedule? {
        return scheduleDao.getScheduleById(id)
    }

    override fun parseRRule(rruleStr: String, endDate: String): RepeatInfo? {
        // 1. 이미 구현하신 내부 private 함수 호출
        val recur = parseRecurrenceString(rruleStr) ?: return null

        // 2. Recurrence 객체를 RepeatInfo 데이터 클래스로 맵핑
        return try {
            RepeatInfo(
                repeatType = recur.frequency.name, // DAILY, WEEKLY 등
                repeatInterval = recur.interval ?: 1,
                endType = when {
                    recur.count != null -> "COUNT"
                    recur.until != null -> "DATE"
                    else -> "NEVER"
                },
                endCount = recur.count ?: 1,
                repeatEndDate = recur.until?.let { icalDate ->
                    // ICalDate를 yyyy-MM-dd 문자열로 변환
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(icalDate.date)
                } ?: endDate
            )
        } catch (e: Exception) {
            Log.e("RRULE_PARSE", "RepeatInfo 변환 실패", e)
            null
        }
    }

    override suspend fun updateRouteSchedule(
        accessToken: String,
        scheduleId: Long,
        request: CreateScheduleRequest,
        calendarId: Long?,
        selectedColor: Int
    ): RawDefaultResponse<CreateScheduleResponse> = safeApiCall {
        // 1. 색상 변환
        val colorHex = String.format("#%06X", (0xFFFFFF and selectedColor))
        val finalRequest = request.copy(
            color = colorHex,
            calendarId = calendarId?.toString() // 💡 요청 시에도 캘린더 ID 반영
        )

        // 2. 서비스 호출 (Service에 선언된 이름 확인: updateRouteSchedule)
        val response = api.updateRouteSchedule(
            accessToken = accessToken,
            scheduleId = scheduleId,
            scope = "SINGLE",
            request = finalRequest.toCreateRouteScheduleRequest(calendarId)
        )

        // 3. 성공 시 로컬 DB 업데이트 로직 (기존 구현 유지)
        if (response.isSuccess && response.result != null) {
            val serverResult = response.result
            val updatedEntity = serverResult.toEntity(colorHex, calendarId).copy(
                // 추가로 보정할 값이 있다면 여기서 처리 (예: serverId 등)
                serverId = serverResult.scheduleId,
                type = "ROUTE"
            )
            scheduleDao.insertAll(listOf(updatedEntity))
            refreshSchedules()
        }

        response
    }
    override suspend fun updateRouteScheduleCombined(
        accessToken: String,
        scheduleId: Long,
        generalRequest: UpdateScheduleRequest,
        routeRequest: UpdateScheduleEditRouteRequest
    ): RawDefaultResponse<UpdateScheduleRouteResponse> {
        return try {
            // 1단계: 기존 경로 삭제 (DELETE)
            try {
                api.deleteScheduleRoute(accessToken, scheduleId)
                Log.d("UpdateFlow", "1단계 - 기존 경로 삭제 완료")
            } catch (e: Exception) {
                Log.d("UpdateFlow", "1단계 - 삭제할 경로가 없거나 실패했으나 무시")
            }

            // 2단계: 일반 정보 수정 (PATCH)
            // 💡 서버의 Place 중복 에러(500)를 피하기 위해 place를 null로 보냅니다.
            val patchRes = api.updateSchedule(
                accessToken,
                scheduleId,
                "SINGLE",
                generalRequest.copy(
                    place = null,
                    isPathIncluded = false
                )
            )

            if (!patchRes.isSuccess) {
                Log.e("UpdateFlow", "2단계 실패: ${patchRes.message}")
                return RawDefaultResponse(false, patchRes.code, "기본 정보 수정 실패: ${patchRes.message}", null)
            }

            Log.d("UpdateFlow", "2단계 - 기본 정보 수정 성공")

            // 3단계: 새로운 경로 등록 (PUT)
            val putRes = api.updateScheduleEditRoute(
                accessToken,
                scheduleId,
                routeRequest.normalizeRouteTimesForApi()
            )

            if (putRes.isSuccess) {
                Log.d("UpdateFlow", "3단계 - 경로 등록 성공")
                // 💡 4단계를 생략함으로써 Place Duplicate Entry 에러를 완전히 차단합니다.
                putRes
            } else {
                Log.e("UpdateFlow", "3단계 실패: ${putRes.message}")
                RawDefaultResponse(false, putRes.code, "경로 정보 등록 실패: ${putRes.message}", null)
            }

        } catch (e: Exception) {
            Log.e("UpdateFlow", "통신 중 예외 발생: ${e.message}")
            RawDefaultResponse(false, "CLIENT_ERROR", e.message ?: "오류 발생", null)
        }
    }
    override suspend fun convertRouteToNormalLocal(scheduleId: Long): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // 1. 기존 일정 정보 조회 (Room DB)
                val oldSchedule = scheduleDao.getScheduleById(scheduleId) ?: return@withContext false
                if (oldSchedule.type != "ROUTE") return@withContext true // 이미 일반 일정이면 성공으로 간주

                Log.d("CONVERT_DEBUG", "원본 일정: ${oldSchedule.title}, 날짜: ${oldSchedule.startDate}, 시간: ${oldSchedule.startTime}")

                // 2. 서버에서 경로 일정 삭제 (기존 API 호출)
                val deleteResult = deleteRouteSchedule(scheduleId)

                if (deleteResult.isSuccess) {
                    // 3. placeJson 파싱 및 PlaceRequest 생성
                    val gson = Gson()
                    val placeRequest = try {
                        val routeData = gson.fromJson(oldSchedule.placeJson, RouteRequest::class.java)
                        PlaceRequest(
                            targetName = routeData?.destName ?: oldSchedule.location ?: "목적지",
                            targetLat = routeData?.destLat ?: 0.0,
                            targetLng = routeData?.destLng ?: 0.0
                        )
                    } catch (e: Exception) {
                        PlaceRequest(
                            targetName = oldSchedule.location ?: "목적지",
                            targetLat = 0.0,
                            targetLng = 0.0
                        )
                    }

                    // 4. 리마인더 필터링 (EVENT 타입만)
                    val eventReminders = oldSchedule.reminders.map { minutes ->
                        ReminderRequest(reminderType = "EVENT", minutesBefore = minutes)
                    }

                    // 5. 일반 일정용 CreateScheduleRequest 생성
                    val request = CreateScheduleRequest(
                        title = oldSchedule.title ?: "제목 없음",
                        isAllDay = oldSchedule.isAllDay,
                        startDate = oldSchedule.startDate,
                        endDate = oldSchedule.endDate,
                        startTime = oldSchedule.startTime,
                        endTime = oldSchedule.endTime,
                        memo = oldSchedule.memo,
                        isPathIncluded = true,
                        isRepeat = oldSchedule.repeatRule != null,
                        repeatInfo = null,
                        place = placeRequest,
                        reminders = eventReminders,
                        route = null, // 로컬 저장 유도 핵심
                        calendarId = oldSchedule.calendarId.toString(),
                        color = String.format("#%06X", (0xFFFFFF and (oldSchedule.eventColor ?: 0)))
                    )

                    Log.d("CONVERT_DEBUG", """
                    [저장 요청 데이터]
                    제목: ${request.title}
                    시작일: ${request.startDate}
                    시작시간: ${request.startTime}
                    캘린더ID: ${oldSchedule.calendarId}
                    타입변경확인: route is null? ${request.route == null}
                """.trimIndent())

                    // 6. createSchedule 호출 (accessToken=null 전달로 로컬/시스템 저장 수행)
                    val response = createSchedule(
                        accessToken = null,
                        request = request,
                        calendarId = oldSchedule.calendarId,
                        selectedColor = oldSchedule.eventColor
                    )

                    Log.d("CONVERT_DEBUG", "최종 생성 결과: ${response.isSuccess}, 메시지: ${response.message}")

                    response.isSuccess
                } else {
                    Log.e("CONVERT_DEBUG", "서버 삭제 실패로 전환 중단")
                    false
                }
            } catch (e: Exception) {
                Log.e("RepoImpl", "전환 중 오류 발생: ${e.message}")
                false
            }
        }
    }

    override suspend fun updatePinStatus(id: Long, isPinned: Boolean) {
        // DAO의 @Query 함수 호출
        scheduleDao.updatePinStatus(id, isPinned)
    }

}

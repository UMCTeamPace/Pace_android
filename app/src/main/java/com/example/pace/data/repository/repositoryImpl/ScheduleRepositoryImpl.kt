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
import com.example.pace.data.repeat.RepeatRuleHelper
import com.example.pace.data.repository.repository.ScheduleRepository
import com.example.pace.data.util.safeApiCall
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.work.WorkManager
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
    }

    // Expose schedules after recurrence expansion
    override val allSchedules: Flow<List<Schedule>> = scheduleDao.getAllSchedules()
        .map { rawList -> expandSchedules(rawList) }
        .flowOn(Dispatchers.IO)

    override val calendarEvents: Flow<Unit> = createCalendarObserver(context)

    // Update a normal schedule in provider and Room
    override suspend fun updateSchedule(schedule: Schedule) {
        withContext(Dispatchers.IO) {
            try {
                val calibratedSchedule = if (!schedule.placeJson.isNullOrEmpty()) {
                    try {
                        val placeData = Gson().fromJson(schedule.placeJson, com.example.pace.data.model.request.PlaceRequest::class.java)
                        schedule.copy(location = placeData.targetName)
                    } catch (e: Exception) {
                        schedule
                    }
                } else {
                    schedule
                }

                if (calibratedSchedule.sourceType == "SYSTEM") {
                    val isSystemUpdated = normalDataSource.updateCalendarEvent(calibratedSchedule)

                    if (isSystemUpdated) {
                        scheduleDao.updateSchedule(calibratedSchedule)
                        Log.d("UpdateLog", "로컬 일정 DB 업데이트 완료 (장소: ${calibratedSchedule.location})")
                    } else {
                        Log.e("UpdateLog", "캘린더 프로바이더 업데이트 실패")
                    }
                } else {
                    scheduleDao.updateSchedule(calibratedSchedule)
                }
            } catch (e: Exception) {
                Log.e("UpdateLog", "일정 업데이트 중 예외 발생: ${e.message}")
            }
        }
    }

    override suspend fun updateExDate(schedule: Schedule) {
        withContext(Dispatchers.IO) {
            try {
                // Create a canceled exception event for a single occurrence
                if (schedule.sourceType == "SYSTEM") {
                    val contentResolver = context.contentResolver

                    val (startMillis, endMillis, eventTimeZone) = if (schedule.isAllDay) {
                        val startLocalDate = LocalDate.parse(schedule.startDate)
                        val endLocalDate = LocalDate.parse(schedule.endDate)
                        Triple(
                            startLocalDate.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
                            endLocalDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
                            "UTC"
                        )
                    } else {
                        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        val parsedStart = sdf.parse("${schedule.startDate} ${schedule.startTime}")?.time
                            ?: System.currentTimeMillis()
                        val parsedEnd = sdf.parse("${schedule.endDate} ${schedule.endTime}")?.time
                            ?: (parsedStart + 3600000L)
                        Triple(parsedStart, parsedEnd, TimeZone.getDefault().id)
                    }

                    val values = ContentValues().apply {
                        put(CalendarContract.Events.TITLE, schedule.title)
                        put(CalendarContract.Events.CALENDAR_ID, schedule.calendarId)
                        put(CalendarContract.Events.EVENT_TIMEZONE, eventTimeZone)

                        put(CalendarContract.Events.ORIGINAL_ID, schedule.id)
                        put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, startMillis)
                        put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CANCELED)
                        put(CalendarContract.Events.DTSTART, startMillis)
                        put(CalendarContract.Events.DTEND, endMillis)
                        put(CalendarContract.Events.ALL_DAY, if (schedule.isAllDay) 1 else 0)
                    }

                    val uri = contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                    Log.d("ExDateLog", "예외 일정 생성 완료: $uri")

                    refreshSchedules()
                }
                else {
                    scheduleDao.updateSchedule(schedule)
                }

            } catch (e: Exception) {
                Log.e("ExDateLog", "예외 일정 생성 실패: ${e.message}")
            }
        }
    }

    // Remove local schedules that were deleted from the device calendar
    override suspend fun cleanUpSystemDeletedSchedules() {
        withContext(Dispatchers.IO) {
            val localSystemSchedules = scheduleDao.getSchedulesWithSystemId()

            localSystemSchedules.forEach { schedule ->

                if (schedule.withRoute == true) {
                    Log.d("SyncCleanUp", "경로 일정은 정리 대상에서 제외: ${schedule.title}")
                    return@forEach
                }

                val exists = checkEventExistsInProvider(schedule.id)

                if (!exists) {
                    scheduleDao.deleteScheduleById(schedule.id)
                    Log.d("SyncCleanUp", "시스템 캘린더에서 사라진 일정 정리: ${schedule.title}")
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
                            // Merge provider data with locally managed fields.
                            // Provider values stay primary, but app-only flags and overrides are preserved.
                            remote.copy(
                                isPinned = local.isPinned,
                                isCompleted = local.isCompleted,
                                isSwiped = local.isSwiped,

                                placeJson = local.placeJson,
                                departureReminders = local.departureReminders,
                                reminders = if (remote.reminders.isEmpty()) local.reminders else remote.reminders,
                                repeatRule = local.repeatRule ?: remote.repeatRule,
                                exDate = local.exDate ?: remote.exDate,

                                type = local.type,
                                sourceType = local.sourceType,
                                serverId = local.serverId,
                                routeId = local.routeId,

                                // 4. Prefer provider-derived fields, but keep local-only flags
                                calendarId = if (remote.calendarId == 0L) local.calendarId else remote.calendarId,
                                eventColor = remote.eventColor ?: local.eventColor
                            )
                        } else {
                            remote
                        }
                    }

                    scheduleDao.insertAll(mergedSchedules)
                }

                if (systemIds.isNotEmpty()) {
                    scheduleDao.deleteRemovedDeviceSchedules(systemIds)
                }
            }
        } catch (e: Exception) {
            Log.e("ScheduleRepository", "새로고침 중 예외 발생: ${e.message}")
        }
    }

    override fun getUsedColors(): Flow<List<String>> = scheduleDao.getUsedColorsRaw().map { list ->
        list.mapNotNull { it.color }
    }

    // Server API wrappers
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

                if (existing != null && existing.withRoute == true) {
                    val protectedEntity = entity.copy(
                        withRoute = true,
                    )
                    scheduleDao.insertSingle(protectedEntity)
                } else {
                    scheduleDao.insertSingle(entity)
                }
            }

            if (lastDate == null && lastId == null) {
                cleanUpDeletedServerRouteSchedules(
                    startDate = startDate,
                    endDate = endDate ?: startDate,
                    serverSchedules = serverSchedules
                )
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
        selectedIds: List<Long>
    ): List<Schedule> {
        val raw = scheduleDao.searchSchedulesWithRange("%$query%", startDate, endDate)

        return expandSchedules(raw).filter { schedule ->
            val calendarMatch = if (schedule.type == "ROUTE") {
                true
            } else {
                selectedIds.isEmpty() || selectedIds.contains(schedule.calendarId)
            }

            val sColor = schedule.eventColor?.toString() ?: ""
            val cColor = schedule.calendarColor?.toString() ?: ""
            val colorMatch = colors.isEmpty() || colors.any { it.equals(sColor, true) || it.equals(cColor, true) }
            val routeMatch = includeRoute || schedule.type != "ROUTE"

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

        val targetId = if ((calendarId ?: 0L) <= 0L) 1L else calendarId!!
        var dName = "기본 일정"
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
            // CASE 1. Normal schedule stored in Calendar Provider and Room
            val systemId = normalDataSource.insertToCalendarProvider(
                request = request,
                selectedCalendarId = targetId,
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
                    message = "로컬 일정 생성 성공",
                    result = null
                )
            } else {
                RawDefaultResponse(
                    isSuccess = false,
                    code = "LOCAL_ERROR",
                    message = "로컬 일정 생성 실패",
                    result = null
                )
            }
        } else {
            // CASE 2. Route schedule stored through server API
            val token = accessToken ?: ""
            val routeRequest = request.toCreateRouteScheduleRequest(calendarId)
            logRequestForSwagger("POST /api/v1/schedules", routeRequest)
            val response = api.createRouteSchedule(
                token,
                routeRequest
            )

            if (response.isSuccess && response.result != null) {
                val serverResult = response.result
                val info = serverResult.scheduleInfo
                val routeData = serverResult.route
                val gson = Gson()
                val allReminders = serverResult.reminders ?: emptyList()
                Log.d("DEBUG_DATA", "전체 알림 개수: ${allReminders.size}")
                val eventReminders = allReminders
                    .filter { it.reminderType == "EVENT" }
                    .map { it.minutesBefore }

                val departureReminders = allReminders
                    .filter { it.reminderType == "DEPARTURE" }
                    .map { it.minutesBefore }
                Log.d("DEBUG_DATA", "출발 알림 목록: $departureReminders")
                val placeAndRouteJson = if (routeData != null) {
                    gson.toJson(routeData)
                } else {
                    gson.toJson(serverResult.place)
                }
                Log.d("DEBUG_DATA", "JSON 생성 여부: ${placeAndRouteJson != null}")

                val serverCalendarId = info.calendarId?.toLongOrNull() ?: calendarId ?: 1L

                var dName = "기본 일정"
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

                val colorHex = info.color ?: "#DC354B"
                val colorInt = try {
                    Color.parseColor(colorHex)
                } catch (e: Exception) {
                    finalColor
                }


                val displayLocation = if (routeData != null) {
                    "${routeData.originName} -> ${routeData.destName}"
                } else {
                    serverResult.place?.targetName ?: request.place?.targetName ?: info.title ?: "장소 없음"
                }

                val scheduleToSave = Schedule(
                    id = serverResult.scheduleId,
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
                    departureReminders = departureReminders,
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

                Log.d("SAVE_DEBUG", """
                ========= 저장 대상 DB 데이터 =========
                일정 제목: ${scheduleToSave.title}
                이벤트 알림: ${scheduleToSave.reminders}
                출발 알림: ${scheduleToSave.departureReminders}
                장소(Location): ${scheduleToSave.location}
                장소 JSON(placeJson): ${scheduleToSave.placeJson?.take(100)}...
                일정 타입: ${scheduleToSave.type}
                ======================================
            """.trimIndent())

                scheduleDao.insertAll(listOf(scheduleToSave))

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
            when {
                endsWith("Z") -> {
                    OffsetDateTime.parse(this)
                        .withOffsetSameInstant(ZoneOffset.UTC)
                        .format(UTC_API_TIME_FORMATTER)
                }

                contains("+") || lastIndexOf('-') > "yyyy-MM-dd".lastIndex -> {
                    OffsetDateTime.parse(this)
                        .withOffsetSameInstant(ZoneOffset.UTC)
                        .format(UTC_API_TIME_FORMATTER)
                }

                else -> {
                    // Route API currently returns UTC timestamps without an explicit offset.
                    // Treat timezone-less route times as already-UTC and only normalize format.
                    LocalDateTime.parse(this)
                        .atOffset(ZoneOffset.UTC)
                        .format(UTC_API_TIME_FORMATTER)
                }
            }
        } catch (_: DateTimeParseException) {
            this
        }
    }

    private fun logRequestForSwagger(endpoint: String, body: Any) {
        val prettyJson = GsonBuilder()
            .serializeNulls()
            .setPrettyPrinting()
            .create()
            .toJson(body)

        Log.d(SWAGGER_LOG_TAG, endpoint)
        prettyJson.chunked(LOG_CHUNK_SIZE).forEachIndexed { index, chunk ->
            Log.d(SWAGGER_LOG_TAG, "chunk=${index + 1}\n$chunk")
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

    // Expand recurring schedules and exclude canceled/overridden occurrences
    private fun expandSchedules(rawSchedules: List<Schedule>): List<Schedule> {
        val expandedList = mutableListOf<Schedule>()
        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        val currentLocalDate = LocalDate.now()
        val rangeStartLocalDate = currentLocalDate.minusYears(2)
        val rangeEndLocalDate = currentLocalDate.plusYears(2)

        val rangeStartDate = Date.from(rangeStartLocalDate.atStartOfDay(ZoneId.systemDefault()).toInstant())
        val rangeEndDate = Date.from(rangeEndLocalDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant())

        // Maps original recurring event id to canceled occurrence dates
        val cancellationMap = rawSchedules
            .filter { it.status == 2 && it.originalId != 0L }
            .groupBy({ it.originalId }, { it.startDate })

        val overrideMap = rawSchedules
            .filter { it.status != 2 && it.originalId != 0L }
            .groupBy({ it.originalId }, { it.startDate })

        val activeSchedules = rawSchedules.filter { it.status != 2 }

        activeSchedules.forEach { schedule ->
            val startLocalDate = try {
                LocalDate.parse(schedule.startDate, dateFormatter)
            } catch (e: Exception) {
                Log.e("ExpandLog", "시작일 파싱 실패: ${schedule.startDate}")
                return@forEach
            }
            val endLocalDate = try {
                LocalDate.parse(schedule.endDate, dateFormatter)
            } catch (e: Exception) {
                startLocalDate
            }

            // Recurring schedule
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

                        // EXDATE from stored schedule
                        val exdates = ExceptionDates()
                        if (!schedule.exDate.isNullOrBlank()) {
                            schedule.exDate.split(',').forEach { dateStr ->
                                val trimmed = dateStr.trim()
                                if (trimmed.isEmpty()) return@forEach

                                try {
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
                                    android.util.Log.e("ScheduleRepository", "EXDATE 파싱 실패: $trimmed")
                                }
                            }
                            if (exdates.getValues().isNotEmpty()) {
                                event.addExceptionDates(exdates)
                            }
                        }

                        // Deleted exception events from provider
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
                                Log.e("ExpandLog", "삭제 예외 날짜 파싱 실패: $deletedDateStr")
                            }
                        }

                        if (exdates.getValues().isNotEmpty()) {
                            event.addExceptionDates(exdates)
                        }

                        // Expand occurrences
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

                            val systemDeletedDates = cancellationMap[schedule.id] ?: emptyList()
                            if (systemDeletedDates.contains(formattedDate)) {
                                continue
                            }
                            val overriddenDates = overrideMap[schedule.id] ?: emptyList()
                            if (overriddenDates.contains(formattedDate)) {
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
                        Log.e("ExpandLog", "반복 확장 실패: ${e.message}")
                        if (startLocalDate.isBefore(rangeEndLocalDate) && endLocalDate.isAfter(rangeStartLocalDate)) {
                            expandedList.add(schedule)
                        }
                    }
                }
            }
            // Multi-day non-recurring schedule
            else if (startLocalDate.isBefore(endLocalDate)) {
                var current = startLocalDate
                while (!current.isAfter(endLocalDate)) {
                    if (!current.isBefore(rangeStartLocalDate) && current.isBefore(rangeEndLocalDate)) {
                        expandedList.add(schedule.copy(startDate = current.format(dateFormatter), endDate = current.format(dateFormatter)))
                    }
                    current = current.plusDays(1)
                }
            }
            else {
                if (!startLocalDate.isBefore(rangeStartLocalDate) && startLocalDate.isBefore(rangeEndLocalDate)) {
                    expandedList.add(schedule)
                }
            }
        }
        return expandedList
    }

    private fun parseRecurrenceString(rruleStr: String): Recurrence? = RepeatRuleHelper.parseRecurrenceString(rruleStr)

    // Map server create response to local entity
    private fun CreateScheduleResponse.toEntity(selectedColorStr: String, fallbackCalendarId: Long?): Schedule {
        val info = this.scheduleInfo
        val finalColorHex = info.color ?: selectedColorStr
        val colorInt = try { android.graphics.Color.parseColor(finalColorHex) } catch (e: Exception) { android.graphics.Color.RED }

        val finalId = info.calendarId?.toLongOrNull() ?: fallbackCalendarId ?: 1L

        var dName = "기본 일정"
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

        // Route schedules are identified either by flag or route payload
        val isRouteType = (this.scheduleInfo.isPathIncluded == true) || (this.route != null)

        val allReminders = this.reminders ?: emptyList()
        val eventRemindersList = allReminders
            .filter { it.reminderType == "EVENT" }
            .map { it.minutesBefore }
        val departureRemindersList = allReminders
            .filter { it.reminderType == "DEPARTURE" }
            .map { it.minutesBefore }

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

            location = this.route?.destName ?: this.place?.targetName,

            calendarId = serverCalendarId,
            eventColor = colorInt,
            calendarColor = colorInt,
            calendarDisplayName = "기본 일정",
            calendarAccountName = "Pace",

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

    private suspend fun syncServerScheduleDetail(accessToken: String, scheduleId: Long) {
        val detailResponse = api.getScheduleDetail(accessToken, scheduleId)
        if (!detailResponse.isSuccess || detailResponse.result == null) {
            Log.w("UpdateFlow", "Route schedule detail sync skipped: ${detailResponse.message}")
            return
        }

        val existing = scheduleDao.getScheduleById(scheduleId)
        scheduleDao.insertSingle(detailResponse.result.toScheduleEntity(existing))
    }

    private fun ScheduleDetailResponse.toScheduleEntity(existing: Schedule?): Schedule {
        val colorHex = scheduleInfo.color ?: "#DC354B"
        val colorInt = try {
            android.graphics.Color.parseColor(colorHex)
        } catch (e: Exception) {
            android.graphics.Color.parseColor("#DC354B")
        }
        val serverCalendarId = scheduleInfo.calendarId?.toLongOrNull()
            ?: existing?.calendarId
            ?: 1L
        val eventRemindersList = reminders
            .filter { it.reminderType == "EVENT" }
            .map { it.minutesBefore }
        val departureRemindersList = reminders
            .filter { it.reminderType == "DEPARTURE" }
            .map { it.minutesBefore }
        val gson = Gson()
        val placeJsonString = place?.let { gson.toJson(it) }
        val routeJsonString = route?.let { gson.toJson(it) }
        val isRouteType = (scheduleInfo.isPathIncluded == true) || (route != null)

        return Schedule(
            id = scheduleId,
            title = scheduleInfo.title,
            startDate = scheduleInfo.startDate,
            endDate = scheduleInfo.endDate,
            startTime = scheduleInfo.startTime?.take(5) ?: "00:00",
            endTime = scheduleInfo.endTime?.take(5) ?: "23:59",
            isAllDay = scheduleInfo.isAllDay,
            memo = scheduleInfo.memo,
            location = route?.destName ?: place?.targetName,
            calendarId = serverCalendarId,
            calendarDisplayName = existing?.calendarDisplayName ?: "기본 일정",
            calendarAccountName = existing?.calendarAccountName ?: "Pace",
            reminders = eventRemindersList,
            departureReminders = departureRemindersList,
            withRoute = isRouteType,
            isCompleted = existing?.isCompleted ?: false,
            isPinned = existing?.isPinned ?: false,
            isSwiped = existing?.isSwiped ?: false,
            type = if (isRouteType) "ROUTE" else "NORMAL",
            eventColor = colorInt,
            calendarColor = colorInt,
            serverId = scheduleId,
            sourceType = "SERVER",
            placeJson = placeJsonString,
            routeJson = routeJsonString,
            repeatRule = existing?.repeatRule,
            exDate = existing?.exDate
        )
    }

    private fun buildRRuleFromRequest(info: RepeatInfo?): String? = RepeatRuleHelper.buildRRule(info)

    override suspend fun getScheduleListForRoute(
        accessToken: String,
        startDate: String,
        endDate: String?
    ): RawDefaultResponse<RouteOnlyScheduleData?> {

        val response = api.getScheduleList(
            accessToken = accessToken,
            startDate = startDate,
            endDate = null,
            lastDate = null,
            lastId = null
        )
        val nowTime = java.time.LocalTime.now()

        val mappedData: RouteOnlyScheduleData? = response.result?.content
            ?.filter { item ->
                val isRouteItem = item.place == null && item.route != null

                val isFuture = try {
                    val itemTime = java.time.LocalTime.parse(item.scheduleInfo.startTime)
                    itemTime.isAfter(nowTime)
                } catch (e: Exception) {
                    false
                }
                isRouteItem && isFuture
            }
            ?.minByOrNull { item ->
                item.scheduleInfo.startTime ?: "23:59:59"
            }
            ?.let { item ->
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

    override suspend fun getLocalRouteSchedules(
        startDate: String,
        endDate: String
    ): List<RouteOnlyScheduleData> = withContext(Dispatchers.IO) {
        scheduleDao.getServerRouteSchedulesInRange(startDate, endDate)
            .mapNotNull { it.toLocalRouteOnlyScheduleData() }
    }

    private fun Schedule.toLocalRouteOnlyScheduleData(): RouteOnlyScheduleData? {
        val routeInfo = routeJson
            ?.takeIf { it.isNotBlank() }
            ?.let { json ->
                runCatching { Gson().fromJson(json, RouteInfo::class.java) }.getOrNull()
            }
            ?: return null

        val colorHex = eventColor?.let { color ->
            String.format(Locale.US, "#%06X", 0xFFFFFF and color)
        }

        return RouteOnlyScheduleData(
            scheduleId = id,
            scheduleInfo = ScheduleInfo(
                title = title ?: "",
                isAllDay = isAllDay,
                startDate = startDate,
                endDate = endDate,
                startTime = startTime,
                endTime = endTime,
                memo = memo,
                isPathIncluded = true,
                color = colorHex,
                calendarId = calendarId.toString()
            ),
            route = routeInfo
        )
    }


    // Delete a normal schedule from Calendar Provider and Room
    override suspend fun deleteNormalSchedule(id: Long): RawDefaultResponse<String> {
        return withContext(Dispatchers.IO) {
            try {
                val deleteUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
                val deletedRows = context.contentResolver.delete(deleteUri, null, null)

                scheduleDao.deleteScheduleById(id)

                Log.d("DeleteLog", "일반 일정 삭제 완료: ID=$id, Provider rows=$deletedRows")
                RawDefaultResponse(isSuccess = true, code = "200", message = "로컬 일정 삭제 완료", result = "SUCCESS")
            } catch (e: Exception) {
                Log.e("DeleteLog", "일반 일정 삭제 실패: ${e.message}")
                RawDefaultResponse(isSuccess = false, code = "LOCAL_ERROR", message = e.message ?: "Unknown Error", result = null)
            }
        }
    }

    // Delete a route schedule from the server and Room
    override suspend fun deleteRouteSchedule(id: Long): RawDefaultResponse<String> {
        return withContext(Dispatchers.IO) {
            try {
                val token = authDataStore.getAccessToken() ?: ""
                val fullToken = if (token.isNotEmpty() && !token.startsWith("Bearer ")) "Bearer $token" else token

                val request = DeleteScheduleRequest(scheduleIds = listOf(id))
                val response = api.deleteSchedules(fullToken, request)

                if (response.isSuccess) {
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

    override fun parseRRule(rruleStr: String, endDate: String): RepeatInfo? = RepeatRuleHelper.parseRepeatInfo(rruleStr, endDate)

    override suspend fun updateRouteSchedule(
        accessToken: String,
        scheduleId: Long,
        request: CreateScheduleRequest,
        calendarId: Long?,
        selectedColor: Int
    ): RawDefaultResponse<CreateScheduleResponse> = safeApiCall {
        val colorHex = String.format("#%06X", (0xFFFFFF and selectedColor))
        val finalRequest = request.copy(
            color = colorHex,
            calendarId = calendarId?.toString()
        )

        val response = api.updateRouteSchedule(
            accessToken = accessToken,
            scheduleId = scheduleId,
            scope = "SINGLE",
            request = finalRequest.toCreateRouteScheduleRequest(calendarId)
        )

        if (response.isSuccess && response.result != null) {
            val serverResult = response.result
            val updatedEntity = serverResult.toEntity(colorHex, calendarId).copy(
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
            // Step 1. Delete previous route section
            try {
                api.deleteScheduleRoute(accessToken, scheduleId)
                Log.d("UpdateFlow", "1단계 - 기존 경로 삭제 완료")
            } catch (e: Exception) {
                Log.d("UpdateFlow", "1단계 - 기존 경로 삭제 응답 없음, 계속 진행")
            }

            // Step 2. Patch general schedule fields
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
                return RawDefaultResponse(false, patchRes.code, "일정 기본 정보 수정 실패: ${patchRes.message}", null)
            }

            Log.d("UpdateFlow", "2단계 - 일정 기본 정보 수정 완료")

            // Step 3. Update route payload
            val putRes = api.updateScheduleEditRoute(
                accessToken,
                scheduleId,
                routeRequest.normalizeRouteTimesForApi()
            )

            if (putRes.isSuccess) {
                syncServerScheduleDetail(accessToken, scheduleId)
                Log.d("UpdateFlow", "3단계 - 경로 정보 갱신 완료")
                putRes
            } else {
                Log.e("UpdateFlow", "3단계 실패: ${putRes.message}")
                RawDefaultResponse(false, putRes.code, "경로 정보 갱신 실패: ${putRes.message}", null)
            }

        } catch (e: Exception) {
            Log.e("UpdateFlow", "통합 수정 중 예외 발생: ${e.message}")
            RawDefaultResponse(false, "CLIENT_ERROR", e.message ?: "오류 발생", null)
        }
    }
    override suspend fun convertRouteToNormalLocal(scheduleId: Long): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val oldSchedule = scheduleDao.getScheduleById(scheduleId) ?: return@withContext false
                if (oldSchedule.type != "ROUTE") return@withContext true

                Log.d("CONVERT_DEBUG", "변환 대상 일정: ${oldSchedule.title}, 날짜: ${oldSchedule.startDate}, 시간: ${oldSchedule.startTime}")

                val deleteResult = deleteRouteSchedule(scheduleId)

                if (deleteResult.isSuccess) {
                    val gson = Gson()
                    val placeRequest = try {
                        val routeData = gson.fromJson(oldSchedule.placeJson, RouteRequest::class.java)
                        PlaceRequest(
                            targetName = routeData?.destName ?: oldSchedule.location ?: "장소 없음",
                            targetLat = routeData?.destLat ?: 0.0,
                            targetLng = routeData?.destLng ?: 0.0
                        )
                    } catch (e: Exception) {
                        PlaceRequest(
                            targetName = oldSchedule.location ?: "장소 없음",
                            targetLat = 0.0,
                            targetLng = 0.0
                        )
                    }

                    val eventReminders = oldSchedule.reminders.map { minutes ->
                        ReminderRequest(reminderType = "EVENT", minutesBefore = minutes)
                    }

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
                        route = null,
                        calendarId = oldSchedule.calendarId.toString(),
                        color = String.format("#%06X", (0xFFFFFF and (oldSchedule.eventColor ?: 0)))
                    )

                    Log.d("CONVERT_DEBUG", """
                    [변환 요청 데이터]
                    제목: ${request.title}
                    시작일: ${request.startDate}
                    시작시간: ${request.startTime}
                    캘린더 ID: ${oldSchedule.calendarId}
                    route is null: ${request.route == null}
                """.trimIndent())

                    val response = createSchedule(
                        accessToken = null,
                        request = request,
                        calendarId = oldSchedule.calendarId,
                        selectedColor = oldSchedule.eventColor
                    )

                    Log.d("CONVERT_DEBUG", "변환 생성 결과: ${response.isSuccess}, message: ${response.message}")

                    response.isSuccess
                } else {
                    Log.e("CONVERT_DEBUG", "서버 경로 일정 삭제 실패로 변환 중단")
                    false
                }
            } catch (e: Exception) {
                Log.e("RepoImpl", "변환 중 예외 발생: ${e.message}")
                false
            }
        }
    }

    override suspend fun updatePinStatus(id: Long, isPinned: Boolean) {
        scheduleDao.updatePinStatus(id, isPinned)
    }

    override suspend fun removeLocalRouteSchedule(scheduleId: Long) {
        withContext(Dispatchers.IO) {
            WorkManager.getInstance(context).cancelUniqueWork("finalize_$scheduleId")
            scheduleDao.deleteScheduleById(scheduleId)
            Log.d("ScheduleRepository", "404 응답으로 경로 일정 로컬 정리: $scheduleId")
        }
    }

    private suspend fun cleanUpDeletedServerRouteSchedules(
        startDate: String,
        endDate: String,
        serverSchedules: List<ScheduleItem>
    ) {
        val serverRouteIds = serverSchedules
            .filter { item -> item.place == null && item.route != null }
            .map { it.scheduleId }
            .toSet()

        val localRouteSchedules = scheduleDao.getServerRouteSchedulesInRange(startDate, endDate)
        val staleRouteIds = localRouteSchedules
            .map { it.id }
            .filterNot { it in serverRouteIds }

        if (staleRouteIds.isEmpty()) return

        val workManager = WorkManager.getInstance(context)
        staleRouteIds.forEach { scheduleId ->
            workManager.cancelUniqueWork("finalize_$scheduleId")
        }

        scheduleDao.deleteSchedulesByIds(staleRouteIds)
        Log.d("ScheduleRepository", "서버에서 삭제된 경로 일정 정리 완료: ${staleRouteIds.joinToString()}")
    }

}

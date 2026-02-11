package com.example.pace.data.repository.repositoryImpl

import android.content.ContentUris
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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import javax.inject.Inject

class ScheduleRepositoryImpl @Inject constructor(
    private val api: ScheduleService,
    private val scheduleDao: ScheduleDao,
    private val routeRemoteDataSource: RouteScheduleRemoteDataSource,
    private val normalDataSource: NormalScheduleRemoteDataSource,
    private val authDataStore: AuthDataStore,
    @ApplicationContext private val context: Context
) : ScheduleRepository {

    // 1. 로컬 데이터 Flow (RRULE 전개 로직 적용)
    override val allSchedules: Flow<List<Schedule>> = scheduleDao.getAllSchedules()
        .map { rawList -> expandSchedules(rawList) }
        .flowOn(Dispatchers.IO)

    override val calendarEvents: Flow<Unit> = createCalendarObserver(context)

    // 2. 로컬 DB 관리 메서드
    override suspend fun updateSchedule(schedule: Schedule) {
        scheduleDao.updateSchedule(schedule)
    }

    // [중요] 시스템 삭제분 정리 로직 구현
    override suspend fun cleanUpSystemDeletedSchedules() {
        withContext(Dispatchers.IO) {
            // Room DB에서 기기 연동 일정(DEVICE)만 조회
            val localSystemSchedules = scheduleDao.getSchedulesWithSystemId()

            localSystemSchedules.forEach { schedule ->
                // Room의 id가 시스템 캘린더의 _ID로 사용됨
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
            // 기기 캘린더 최신 데이터 로드
            val normalSchedules = normalDataSource.getSchedules()

            // 현재 로컬 DB 데이터 조회
            val localSchedules = scheduleDao.getAllSchedulesOnce()
            val localScheduleMap = localSchedules.associateBy { it.id }

            // 기기 데이터 기준으로 병합 (핀 고정 상태 유지)
            val mergedSchedules = normalSchedules.map { remote ->
                val local = localScheduleMap[remote.id]
                if (local != null) remote.copy(isPinned = local.isPinned) else remote
            }

            // DB 반영
            scheduleDao.insertAll(mergedSchedules)
            Log.d("REPO_SYNC", "로컬 일정 ${mergedSchedules.size}개 동기화 완료")

        } catch (e: Exception) {
            Log.e("REPO_SYNC", "새로고침 중 에러: ${e.message}")
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
            val serverSchedules = response.result.content.map { it.toScheduleEntity() }
            if (serverSchedules.isNotEmpty()) {
                scheduleDao.insertAll(serverSchedules)
            }
        }
        response
    }

    override suspend fun getScheduleDetail(accessToken: String, scheduleId: Long) = safeApiCall {
        api.getScheduleDetail(accessToken, scheduleId)
    }

    override suspend fun updateSchedule(accessToken: String, scheduleId: Long, scope: String, request: UpdateScheduleRequest) = safeApiCall {
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

    override suspend fun searchSchedules(query: String, colors: Set<String>, includeRoute: Boolean, startDate: String, endDate: String): List<Schedule> {
        val raw = scheduleDao.searchSchedulesWithRange("%$query%", startDate, endDate)
        return expandSchedules(raw).filter { schedule ->
            val sColor = schedule.eventColor?.toString() ?: ""
            val cColor = schedule.calendarColor?.toString() ?: ""
            val colorMatch = colors.isEmpty() || colors.any { it.equals(sColor, true) || it.equals(cColor, true) }
            val routeMatch = includeRoute || schedule.type != "ROUTE"
            colorMatch && routeMatch
        }
    }

    override suspend fun createSchedule(
        accessToken: String?,
        request: CreateScheduleRequest,
        placeId: String?,
        calendarId: Long?
    ) = safeApiCall {
        val defaultColorStr = "#DC354B"
        val colorInt = android.graphics.Color.parseColor(defaultColorStr)

        if (request.route == null) {
            val systemId = normalDataSource.insertToCalendarProvider(request)
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
                    placeJson = if (placeId != null) "{\"placeId\":\"$placeId\"}" else null,
                    calendarId = calendarId ?: 1L,
                    calendarDisplayName = "내 일정",
                    calendarAccountName = "Pace",
                    reminders = request.reminders.map { it.minutesBefore },
                    withRoute = (placeId != null),
                    isCompleted = false,
                    isPinned = false,
                    isSwiped = false,
                    type = "NORMAL",
                    eventColor = colorInt,
                    calendarColor = colorInt,
                    sourceType = "DEVICE",
                    serverId = null,
                    routeId = null,
                    repeatRule = null,
                    exdate = null
                )
                scheduleDao.insertAll(listOf(localSchedule))
                RawDefaultResponse(isSuccess = true, code = "COMMON200", message = "로컬 일정 저장 성공", result = null)
            } else {
                RawDefaultResponse(isSuccess = false, code = "LOCAL_ERROR", message = "저장 실패", result = null)
            }
        } else {
            val token = accessToken ?: ""
            val response = api.createSchedule(token, request)
            if (response.isSuccess && response.result != null) {
                val serverResult = response.result
                val newSchedule = serverResult.toEntity(defaultColorStr).copy(
                    location = request.place?.targetName,
                    placeJson = if (placeId != null) "{\"placeId\":\"$placeId\"}" else null,
                    calendarId = calendarId ?: 1L
                )
                scheduleDao.insertAll(listOf(newSchedule))
            }
            response
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

    // --- 반복 일정 전개 및 헬퍼 함수 (기존 유지) ---
    private fun expandSchedules(rawSchedules: List<Schedule>): List<Schedule> {
        // ... (보내주신 biweekly 전개 로직 동일)
        val expandedList = mutableListOf<Schedule>()
        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        val currentLocalDate = LocalDate.now()
        val rangeStartLocalDate = currentLocalDate.minusYears(2)
        val rangeEndLocalDate = currentLocalDate.plusYears(2)

        val rangeStartDate = Date.from(rangeStartLocalDate.atStartOfDay(ZoneId.systemDefault()).toInstant())
        val rangeEndDate = Date.from(rangeEndLocalDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant())

        rawSchedules.forEach { schedule ->
            val startLocalDate = LocalDate.parse(schedule.startDate, dateFormatter)
            val endLocalDate = LocalDate.parse(schedule.endDate, dateFormatter)

            if (!schedule.repeatRule.isNullOrBlank()) {
                val dtStartString = "${schedule.startDate} ${schedule.startTime}"
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                val dtStartDate = try { sdf.parse(dtStartString) } catch (e: Exception) { null }

                if (dtStartDate != null) {
                    try {
                        val event = VEvent()
                        event.setDateStart(DateStart(dtStartDate))
                        val recur = parseRecurrenceString(schedule.repeatRule!!)
                        if (recur != null) event.setRecurrenceRule(RecurrenceRule(recur))

                        if (!schedule.exdate.isNullOrBlank()) {
                            val exdates = ExceptionDates()
                            schedule.exdate!!.split(',').forEach { dateStr ->
                                try {
                                    val date = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.getDefault()).apply {
                                        timeZone = TimeZone.getTimeZone("UTC")
                                    }.parse(dateStr.trim())
                                    if(date != null) exdates.getValues().add(ICalDate(date, true))
                                } catch (e: Exception) {
                                    try {
                                        val date = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).parse(dateStr.trim())
                                        if (date != null) exdates.getValues().add(ICalDate(date, false))
                                    } catch (e2: Exception) {}
                                }
                            }
                            if (exdates.getValues().isNotEmpty()) event.addExceptionDates(exdates)
                        }

                        val iterator = event.getDateIterator(TimeZone.getDefault())
                        iterator.advanceTo(rangeStartDate)
                        var count = 0
                        while (iterator.hasNext() && count < 1000) {
                            val occurrenceDate = iterator.next()
                            if (occurrenceDate.after(rangeEndDate)) break
                            val oZDT = occurrenceDate.toInstant().atZone(ZoneId.systemDefault())
                            expandedList.add(schedule.copy(
                                startDate = oZDT.toLocalDate().format(dateFormatter),
                                endDate = oZDT.toLocalDate().format(dateFormatter),
                                startTime = oZDT.toLocalTime().format(timeFormatter)
                            ))
                            count++
                        }
                    } catch (e: Exception) {
                        if (startLocalDate.isBefore(rangeEndLocalDate) && endLocalDate.isAfter(rangeStartLocalDate)) expandedList.add(schedule)
                    }
                }
            } else if (startLocalDate.isBefore(endLocalDate)) {
                var current = startLocalDate
                while (!current.isAfter(endLocalDate)) {
                    if (!current.isBefore(rangeStartLocalDate) && current.isBefore(rangeEndLocalDate)) {
                        expandedList.add(schedule.copy(startDate = current.format(dateFormatter), endDate = current.format(dateFormatter)))
                    }
                    current = current.plusDays(1)
                }
            } else {
                if (!startLocalDate.isBefore(rangeStartLocalDate) && startLocalDate.isBefore(rangeEndLocalDate)) expandedList.add(schedule)
            }
        }
        return expandedList
    }

    private fun parseRecurrenceString(rruleStr: String): Recurrence? {
        return try {
            val parts = rruleStr.split(";")
            val params = parts.associate {
                val split = it.split("=")
                if (split.size == 2) split[0].uppercase() to split[1] else "" to ""
            }
            val freqStr = params["FREQ"] ?: return null
            val builder = Recurrence.Builder(Frequency.valueOf(freqStr))
            params["INTERVAL"]?.toIntOrNull()?.let { builder.interval(it) }
            params["COUNT"]?.toIntOrNull()?.let { builder.count(it) }
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
    private fun CreateScheduleResponse.toEntity(selectedColorStr: String): Schedule {
        val colorInt = try { Color.parseColor(selectedColorStr) } catch (e: Exception) { Color.RED }
        return Schedule(
            id = this.scheduleId,
            title = this.scheduleInfo.title,
            startDate = this.scheduleInfo.startDate,
            endDate = this.scheduleInfo.endDate,
            startTime = this.scheduleInfo.startTime ?: "00:00",
            endTime = this.scheduleInfo.endTime ?: "00:00",
            isAllDay = this.scheduleInfo.isAllDay,
            memo = this.scheduleInfo.memo,
            location = this.place?.targetName,
            calendarId = 0L,
            calendarDisplayName = "내 일정",
            calendarAccountName = "Pace",
            withRoute = (this.route != null),
            type = if (this.route != null) "ROUTE" else "NORMAL",
            eventColor = colorInt,
            calendarColor = colorInt,
            isCompleted = false,
            isPinned = false,
            isSwiped = false,
            sourceType = "SERVER",
            repeatRule = null,
            exdate = null,
            serverId = this.scheduleId
        )
    }

    private fun ScheduleItem.toScheduleEntity(): Schedule {
        val colorInt = Color.parseColor("#DC354B")
        return Schedule(
            id = this.scheduleId,
            title = this.scheduleInfo.title,
            startDate = this.scheduleInfo.startDate,
            endDate = this.scheduleInfo.endDate,
            startTime = this.scheduleInfo.startTime?.substring(0, 5) ?: "00:00",
            endTime = this.scheduleInfo.endTime?.substring(0, 5) ?: "23:59",
            isAllDay = this.scheduleInfo.isAllDay,
            memo = this.scheduleInfo.memo,
            location = this.place?.targetName,
            calendarId = 0L,
            calendarDisplayName = "내 일정",
            calendarAccountName = "Pace",
            withRoute = (this.route != null),
            type = if (this.route != null) "ROUTE" else "NORMAL",
            eventColor = colorInt,
            calendarColor = colorInt,
            isCompleted = false,
            isPinned = false,
            isSwiped = false,
            sourceType = "SERVER",
            repeatRule = null,
            exdate = null,
            serverId = this.scheduleId
        )
    }
}
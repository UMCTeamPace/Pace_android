package com.example.pace.data.repository.repositoryImpl

import android.content.Context
import android.util.Log
import com.example.pace.data.api.ScheduleService
import com.example.pace.data.createCalendarObserver
import com.example.pace.data.datasource.AuthDataStore
import com.example.pace.data.datasource.NormalScheduleRemoteDataSource
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
import biweekly.component.VEvent
import biweekly.property.DateStart
import biweekly.property.ExceptionDates
import biweekly.property.RecurrenceRule
import biweekly.util.DayOfWeek
import biweekly.util.Frequency
import biweekly.util.ICalDate
import biweekly.util.Recurrence
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import javax.inject.Inject

class ScheduleRepositoryImpl @Inject constructor(
    private val api: ScheduleService,
    private val scheduleDao: ScheduleDao,
    private val normalScheduleDataSource: NormalScheduleRemoteDataSource,
    private val authDataStore: AuthDataStore,
    @ApplicationContext private val context: Context
) : ScheduleRepository {

    // --- [공통 토큰 검사 로직] ---
    private fun ensureValidToken(token: String): String {
        return when {
            token.isBlank() -> {
                Log.e("Auth_Check", "⚠️ Access Token이 비어있습니다!")
                ""
            }
            // 서버가 "Bearer " 접두사를 요구할 경우를 대비한 자동 처리
            !token.startsWith("Bearer ") -> "Bearer $token"
            else -> token
        }
    }

    // 1. 로컬 데이터 Flow (RRULE 전개 로직 적용)
    override val allSchedules: Flow<List<Schedule>> = scheduleDao.getAllSchedules()
        .map { rawList -> expandSchedules(rawList) }
        .flowOn(Dispatchers.IO)

    override val calendarEvents: Flow<Unit> = createCalendarObserver(context)

    // 2. 로컬 DB 관리 메서드
    override suspend fun updateSchedule(schedule: Schedule) {
        scheduleDao.updateSchedule(schedule)
    }

    override suspend fun refreshSchedules() {
        val normalSchedules = normalScheduleDataSource.getSchedules()
        val localSchedules = scheduleDao.getAllSchedulesOnce()
        val localScheduleMap = localSchedules.associateBy { it.id }

        val mergedSchedules = normalSchedules.map { remote ->
            val local = localScheduleMap[remote.id]
            if (local != null) remote.copy(isPinned = local.isPinned) else remote
        }

        val remoteIds = normalSchedules.map { it.id }.toSet()
        val toDelete = localSchedules.filter { it.id !in remoteIds }

        scheduleDao.deleteAll(toDelete)
        scheduleDao.insertAll(mergedSchedules)
    }

    override fun getUsedColors(): Flow<List<String>> = scheduleDao.getUsedColorsRaw().map { list ->
        list.mapNotNull { it.color }
    }.flowOn(Dispatchers.IO)

    // 3. 서버 API 메서드 (수동 토큰 검사 및 safeApiCall 적용)
    override suspend fun getScheduleList(accessToken: String, startDate: String, endDate: String?, lastDate: String?, lastId: Long?) = safeApiCall {
        val validToken = ensureValidToken(accessToken)
        api.getScheduleList(validToken, startDate, endDate, lastDate, lastId)
    }

    override suspend fun createSchedule(accessToken: String, request: CreateScheduleRequest) = safeApiCall {
        val validToken = ensureValidToken(accessToken)
        api.createSchedule(validToken, request)
    }

    override suspend fun getScheduleDetail(accessToken: String, scheduleId: Long) = safeApiCall {
        val validToken = ensureValidToken(accessToken)
        api.getScheduleDetail(validToken, scheduleId)
    }

    override suspend fun updateSchedule(accessToken: String, scheduleId: Long, scope: String, request: UpdateScheduleRequest) = safeApiCall {
        val validToken = ensureValidToken(accessToken)
        api.updateSchedule(validToken, scheduleId, scope, request)
    }

    override suspend fun deleteSchedules(accessToken: String, request: DeleteScheduleRequest) = safeApiCall {
        val validToken = ensureValidToken(accessToken)
        api.deleteSchedules(validToken, request)
    }

    override suspend fun updateScheduleRoute(accessToken: String, scheduleId: Long, request: UpdateScheduleRouteRequest) = safeApiCall {
        val validToken = ensureValidToken(accessToken)
        api.updateScheduleRoute(validToken, scheduleId, request)
    }

    override suspend fun deleteScheduleRoute(accessToken: String, scheduleId: Long) = safeApiCall {
        val validToken = ensureValidToken(accessToken)
        api.deleteScheduleRoute(validToken, scheduleId)
    }

    override suspend fun convertRouteToGeneral(accessToken: String, id: Long) = safeApiCall {
        val validToken = ensureValidToken(accessToken)
        api.convertRouteToGeneral(validToken, id)
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

    // 4. 반복 일정 전개 로직 (biweekly 라이브러리 활용)
    private fun expandSchedules(rawSchedules: List<Schedule>): List<Schedule> {
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
                        if (recur != null) {
                            event.setRecurrenceRule(RecurrenceRule(recur))
                        }

                        // EXDATE (예외 날짜) 처리
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
}
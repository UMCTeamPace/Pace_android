package com.example.pace.data.repository.repositoryImpl

import android.content.Context
import com.example.pace.data.api.ScheduleService
import com.example.pace.data.createCalendarObserver
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
import com.example.pace.data.datasource.AuthDataStore
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import javax.inject.Inject
import android.graphics.Color
import com.example.pace.data.datasource.RouteScheduleRemoteDataSource
import kotlinx.coroutines.flow.toSet

class ScheduleRepositoryImpl @Inject constructor(
    private val api: ScheduleService,
    private val scheduleDao: ScheduleDao,
    private val routeRemoteDataSource: RouteScheduleRemoteDataSource, // 방금 만든 것
    private val normalDataSource: NormalScheduleRemoteDataSource,    // 기기 캘린더용
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

    override suspend fun refreshSchedules() {
        try {
            // 1. 변수명 수정: normalScheduleDataSource -> normalDataSource
            val normalSchedules = normalDataSource.getSchedules()

            // 2. 현재 로컬 DB 데이터 조회
            val localSchedules = scheduleDao.getAllSchedulesOnce()
            val localScheduleMap = localSchedules.associateBy { it.id }

            // 3. 기기 데이터 기준으로 병합 (핀 고정 상태 유지)
            val mergedSchedules = normalSchedules.map { remote ->
                val local = localScheduleMap[remote.id]
                if (local != null) remote.copy(isPinned = local.isPinned) else remote
            }

            // 4. 삭제 대상 식별 (기기에서 사라진 것만 골라냄)
            val remoteIds = normalSchedules.map { it.id }.toSet()
            // 필터 조건: 기기에 없고 + 출처가 DEVICE인 데이터만 삭제 (나중에 서버 데이터 보호용)
            val toDelete = localSchedules.filter { it.id !in remoteIds && it.sourceType == "DEVICE" }

            // 5. DB 반영
            if (toDelete.isNotEmpty()) {
                scheduleDao.deleteAll(toDelete)
            }
            scheduleDao.insertAll(mergedSchedules)

            android.util.Log.d("REPO_SYNC", "로컬 일정 ${mergedSchedules.size}개 동기화 완료")

        } catch (e: Exception) {
            android.util.Log.e("REPO_SYNC", "새로고침 중 에러: ${e.message}")
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
        // 1. 서버 API 호출
        val response = api.getScheduleList(accessToken, startDate, endDate, lastDate, lastId)

        // 2. 서버 통신 성공 및 데이터가 있는 경우 로컬 DB 동기화
        if (response.isSuccess && response.result != null) {
            val serverSchedules = response.result.content.map { it.toScheduleEntity() }

            if (serverSchedules.isNotEmpty()) {
                // Room에 저장 (insertAll은 REPLACE 전략이므로 기존 데이터가 있다면 업데이트됨)
                scheduleDao.insertAll(serverSchedules)
                android.util.Log.d("REPO_SYNC", "서버로부터 ${serverSchedules.size}개의 일정을 가져와 DB에 저장했습니다.")
            }
        }

        response // 최종적으로 ViewModel에 response 반환
    }

    override suspend fun createSchedule(accessToken: String, request: CreateScheduleRequest) = safeApiCall {
        val response = api.createSchedule(accessToken, request)

        if (response.isSuccess && response.result != null) {
            // 서버 응답 결과를 로컬 엔티티로 변환 (필요시 색상 정보 전달)
            val newSchedule = response.result.toEntity(request.title) // 예시로 title 전달, 실제론 색상 필드 사용 가능

            // Room DB에 저장
            // insertAll은 List를 받으므로 listOf로 감싸줍니다.
            scheduleDao.insertAll(listOf(newSchedule))

            android.util.Log.d("REPO_SYNC", "서버 일정 로컬 DB 동기화 완료: ${newSchedule.id}")
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

    // 4. 반복 일정 전개 로직 (biweekly 활용)
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

    // CreateScheduleResponse -> Schedule(Entity) 변환 확장 함수
    fun CreateScheduleResponse.toEntity(selectedColorStr: String = "#DC354B"): Schedule {
        // 16진수 문자열("#RRGGBB")을 Int로 변환
        val colorInt = try {
            Color.parseColor(selectedColorStr)
        } catch (e: Exception) {
            Color.RED // 변환 실패 시 기본색
        }

        return Schedule(
            id = this.scheduleId, // 서버 ID를 Primary Key로 사용
            title = this.scheduleInfo.title,
            startDate = this.scheduleInfo.startDate,
            endDate = this.scheduleInfo.endDate,
            startTime = this.scheduleInfo.startTime ?: "00:00",
            endTime = this.scheduleInfo.endTime ?: "00:00",
            isAllDay = this.scheduleInfo.isAllDay,
            memo = this.scheduleInfo.memo,

            // 장소 이름만 저장 (위도/경도는 무시)
            location = this.place?.targetName,

            // 필수 필드 및 기본값 처리
            calendarId = 0L,
            calendarDisplayName = "내 일정",
            calendarAccountName = "Pace",

            // 앱 고유 데이터
            withRoute = (this.route != null),
            type = if (this.route != null) "ROUTE" else "NORMAL",

            // 색상 (Int 타입으로 매핑)
            eventColor = colorInt,
            calendarColor = colorInt,

            // 기타 상태값
            isCompleted = false,
            isPinned = false,
            isSwiped = false,
            sourceType = "SERVER", // 서버에서 가져온 데이터임을 명시

            // 반복 규칙 (서버 응답에 있다면 매핑, 없으면 null)
            repeatRule = null,
            exdate = null,

            // 필요한 경우 서버 전용 ID 보관
            serverId = this.scheduleId
        )
    }

    private fun ScheduleItem.toScheduleEntity(): Schedule {
        val colorInt = Color.parseColor("#DC354B") // 기본 색상

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
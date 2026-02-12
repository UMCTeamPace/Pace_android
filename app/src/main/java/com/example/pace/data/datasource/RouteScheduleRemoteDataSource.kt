package com.example.pace.data.datasource

import android.provider.CalendarContract
import com.example.pace.data.api.ScheduleService
import com.example.pace.data.model.request.CreateScheduleRequest
import com.example.pace.data.model.request.DeleteScheduleRequest
import com.example.pace.data.model.request.UpdateScheduleRequest
import com.example.pace.data.model.request.UpdateScheduleRouteRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RouteScheduleRemoteDataSource @Inject constructor(
    private val scheduleService: ScheduleService,
    // 💡 context를 생성자에서 주입받습니다.
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) {

    // 1. 전체 일정 목록 가져오기 (페이징)
    suspend fun getScheduleList(
        token: String,
        startDate: String,
        endDate: String? = null,
        lastDate: String? = null,
        lastId: Long? = null
    ) = scheduleService.getScheduleList(token, startDate, endDate, lastDate, lastId)

    // 2. 새로운 일정 생성
    suspend fun createSchedule(token: String, request: CreateScheduleRequest) =
        scheduleService.createSchedule(token, request)

    // 3. 일정 상세 정보 조회
    suspend fun getScheduleDetail(token: String, scheduleId: Long) =
        scheduleService.getScheduleDetail(token, scheduleId)

    // 4. 일정 수정
    suspend fun updateSchedule(
        token: String,
        scheduleId: Long,
        scope: String = "SINGLE",
        request: UpdateScheduleRequest
    ) = scheduleService.updateSchedule(token, scheduleId, scope, request)

    // 5. 일정 삭제 (여러 개 선택 삭제 가능)
    suspend fun deleteSchedules(token: String, request: DeleteScheduleRequest) =
        scheduleService.deleteSchedules(token, request)

    // 6. 일정 내 경로 수정
    suspend fun updateScheduleRoute(
        token: String,
        scheduleId: Long,
        request: UpdateScheduleRouteRequest
    ) = scheduleService.updateScheduleRoute(token, scheduleId, request)

    // 7. 일정 내 경로 삭제
    suspend fun deleteScheduleRoute(token: String, scheduleId: Long) =
        scheduleService.deleteScheduleRoute(token, scheduleId)

    // 8. 경로 일정을 일반 일정으로 변환
    suspend fun convertRouteToGeneral(token: String, id: Long) =
        scheduleService.convertRouteToGeneral(token, id)

    suspend fun insertToCalendarProvider(request: CreateScheduleRequest): Long = withContext(
        Dispatchers.IO) {
        // 💡 applicationContext 대신 주입받은 context를 사용합니다.
        val contentResolver = context.contentResolver

        val startMillis = parseToMillis(request.startDate, request.startTime ?: "00:00")
        val endMillis = parseToMillis(request.endDate, request.endTime ?: "23:59")

        val values = android.content.ContentValues().apply {
            put(CalendarContract.Events.TITLE, request.title)
            put(CalendarContract.Events.DESCRIPTION, request.memo)
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, endMillis)
            put(CalendarContract.Events.ALL_DAY, if (request.isAllDay) 1 else 0)
            put(CalendarContract.Events.CALENDAR_ID, 1)
            put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
        }

        val uri = contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        val eventId = uri?.lastPathSegment?.toLong() ?: -1L

        if (eventId != -1L) {
            request.reminders.forEach { reminder ->
                val reminderValues = android.content.ContentValues().apply {
                    put(CalendarContract.Reminders.MINUTES, reminder.minutesBefore)
                    put(CalendarContract.Reminders.EVENT_ID, eventId)
                    put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                }
                contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
            }
        }
        eventId
    }
    private fun parseToMillis(date: String, time: String): Long {
        return try {
            val dateTime = "$date $time"
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            sdf.parse(dateTime)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
}
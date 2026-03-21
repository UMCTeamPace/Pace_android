package com.example.pace.data.datasource

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.provider.CalendarContract
import android.util.Log
import com.example.pace.data.model.Schedule
import com.example.pace.data.model.request.CreateScheduleRequest
import com.example.pace.data.model.request.RepeatInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.*
import dagger.hilt.android.qualifiers.ApplicationContext // 추가
import javax.inject.Inject // 추가
class NormalScheduleRemoteDataSource @Inject constructor(
    @ApplicationContext private val applicationContext: Context
) {
    companion object {
        private const val REPEAT_DEBUG_TAG = "RepeatDebug"
    }

    suspend fun insertToCalendarProvider(
        request: CreateScheduleRequest,
        selectedCalendarId: Long? = null,
        selectedColor: Int? = null
    ): Long = withContext(Dispatchers.IO) {
        val contentResolver = applicationContext.contentResolver

        val timing = buildCalendarEventTiming(
            startDate = request.startDate,
            endDate = request.endDate,
            startTime = request.startTime,
            endTime = request.endTime,
            isAllDay = request.isAllDay
        )

        val generatedRrule = buildRRule(request.repeatInfo)
        if (generatedRrule != null) {
            Log.d(
                REPEAT_DEBUG_TAG,
                "반복 저장 요청: title=${request.title}, isAllDay=${request.isAllDay}, startDate=${request.startDate}, endDate=${request.endDate}, startTime=${request.startTime}, endTime=${request.endTime}, repeatInfo=${request.repeatInfo}"
            )
            Log.d(
                REPEAT_DEBUG_TAG,
                "반복 저장 값: dtStart=${timing.startMillis}, dtEnd=${timing.endMillis}, duration=${timing.durationForRecurring}, timezone=${timing.timeZoneId}, rrule=$generatedRrule"
            )
        }

        val values = ContentValues().apply {
            put(CalendarContract.Events.TITLE, request.title)
            put(CalendarContract.Events.DESCRIPTION, request.memo)
            put(CalendarContract.Events.EVENT_LOCATION, request.place?.targetName ?: "")
            put(CalendarContract.Events.DTSTART, timing.startMillis)
            put(CalendarContract.Events.ALL_DAY, if (request.isAllDay) 1 else 0)
            put(CalendarContract.Events.CALENDAR_ID, selectedCalendarId ?: 1L)
            put(CalendarContract.Events.EVENT_TIMEZONE, timing.timeZoneId)

            // [색상 처리]
            val finalColor = if (selectedColor != null && selectedColor != 0) selectedColor
            else android.graphics.Color.parseColor("#DC354B")
            put(CalendarContract.Events.EVENT_COLOR, finalColor)

            // [핵심 보완: 반복 일정일 경우 DURATION 처리]
            if (!generatedRrule.isNullOrEmpty()) {
                put(CalendarContract.Events.RRULE, generatedRrule)

                // 반복 일정은 DTEND 대신 DURATION 사용 권장 (P3600S = 3600초 = 1시간)
                put(CalendarContract.Events.DURATION, timing.durationForRecurring)
                // 반복 일정 시 DTEND는 null로 비워두는 것이 표준입니다.
                putNull(CalendarContract.Events.DTEND)
            } else {
                // 반복이 아닐 때는 일반적인 DTEND 사용
                put(CalendarContract.Events.DTEND, timing.endMillis)
            }

            put(CalendarContract.Events.HAS_ALARM, if (request.reminders.isNotEmpty()) 1 else 0)
        }

        val uri = try {
            contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        } catch (e: Exception) {
            Log.e("CALENDAR_INSERT", "Insert 실패: ${e.message}")
            null
        }

        val eventId = uri?.lastPathSegment?.toLong() ?: -1L

        // 3. 알림(Reminders) 저장
        if (eventId != -1L && request.reminders.isNotEmpty()) {
            request.reminders.forEach { reminder ->
                val reminderValues = ContentValues().apply {
                    put(CalendarContract.Reminders.EVENT_ID, eventId)
                    put(CalendarContract.Reminders.MINUTES, reminder.minutesBefore)
                    put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                }
                try {
                    contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
                } catch (e: Exception) {
                    Log.e("CALENDAR_INSERT", "알람 삽입 실패: ${e.message}")
                }
            }
        }

        Log.d("CALENDAR_INSERT", "최종 생성 ID: $eventId")
        eventId
    }
    suspend fun getSchedules(): List<Schedule> = withContext(Dispatchers.IO) {
        val scheduleList = mutableListOf<Schedule>()

        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -30)
        val startRange = calendar.timeInMillis
        calendar.add(Calendar.MONTH, 60)
        val endRange = calendar.timeInMillis

        val selection = "(${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?) AND " +
                "(${CalendarContract.Events.DELETED} = 0)"

        val selectionArgs = arrayOf(startRange.toString(), endRange.toString())

        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.ORIGINAL_ID,
            CalendarContract.Events.STATUS,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.RRULE,
            CalendarContract.Events.EXDATE,
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.CALENDAR_DISPLAY_NAME,
            CalendarContract.Events.EVENT_COLOR,
            CalendarContract.Events.CALENDAR_COLOR,
            CalendarContract.Events.DURATION, // 💡 추가됨
            CalendarContract.Events.DELETED
        )

        try {
            val cursor: Cursor? = applicationContext.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                CalendarContract.Events.DTSTART + " ASC"
            )

            cursor?.use {
                val idIdx = it.getColumnIndexOrThrow(CalendarContract.Events._ID)
                val originalIdIdx = it.getColumnIndexOrThrow(CalendarContract.Events.ORIGINAL_ID)
                val statusIdx = it.getColumnIndexOrThrow(CalendarContract.Events.STATUS)
                val titleIdx = it.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
                val dtStartIdx = it.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
                val dtEndIdx = it.getColumnIndexOrThrow(CalendarContract.Events.DTEND)
                val durationIdx = it.getColumnIndexOrThrow(CalendarContract.Events.DURATION) // 💡 추가
                val allDayIdx = it.getColumnIndexOrThrow(CalendarContract.Events.ALL_DAY)
                val descIdx = it.getColumnIndexOrThrow(CalendarContract.Events.DESCRIPTION)
                val locIdx = it.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION)
                val rruleIdx = it.getColumnIndexOrThrow(CalendarContract.Events.RRULE)
                val exdateIdx = it.getColumnIndexOrThrow(CalendarContract.Events.EXDATE)
                val calIdIdx = it.getColumnIndexOrThrow(CalendarContract.Events.CALENDAR_ID)
                val calNameIdx = it.getColumnIndexOrThrow(CalendarContract.Events.CALENDAR_DISPLAY_NAME)
                val eventColorIdx = it.getColumnIndexOrThrow(CalendarContract.Events.EVENT_COLOR)
                val calColorIdx = it.getColumnIndexOrThrow(CalendarContract.Events.CALENDAR_COLOR)
                val deletedIdx = it.getColumnIndexOrThrow(CalendarContract.Events.DELETED)

                while (it.moveToNext()) {
                    val isDeleted = it.getInt(deletedIdx)
                    if (isDeleted == 1) continue

                    val id = it.getLong(idIdx)
                    val originalId = it.getLong(originalIdIdx)
                    val status = it.getInt(statusIdx)
                    val title = it.getString(titleIdx) ?: ""
                    val dtStart = it.getLong(dtStartIdx)
                    val durationStr = it.getString(durationIdx) // 💡 "P1D", "PT3600S" 등

                    // 💡 [핵심] dtEnd 보정 로직 (Duration 활용)
                    var dtEnd = it.getLong(dtEndIdx)

                    if (dtEnd < dtStart && !durationStr.isNullOrEmpty()) {
                        try {
                            // RFC 2445 Duration 파싱 (P86400S, P1D 등)
                            val numericValue = durationStr.replace(Regex("[^0-9]"), "").toLongOrNull() ?: 0L
                            dtEnd = if (durationStr.contains("D")) {
                                // 날짜 단위 (P1D = 1일)
                                dtStart + (numericValue * 24 * 60 * 60 * 1000)
                            } else {
                                // 초 단위 (PT3600S = 3600초)
                                dtStart + (numericValue * 1000)
                            }
                        } catch (e: Exception) {
                            dtEnd = dtStart // 파싱 실패 시 방어 코드
                        }
                    } else if (dtEnd < dtStart) {
                        dtEnd = dtStart // 데이터가 아예 없는 경우
                    }

                    val isAllDay = it.getInt(allDayIdx) == 1
                    val normalizedEndMillis = normalizeEndMillisForDisplay(
                        startMillis = dtStart,
                        endMillis = dtEnd,
                        isAllDay = isAllDay
                    )
                    val startDate = if (isAllDay) formatMillisToUtcDate(dtStart) else formatMillisToDate(dtStart)
                    val endDate = if (isAllDay) formatMillisToUtcDate(normalizedEndMillis) else formatMillisToDate(normalizedEndMillis)
                    val startTime = if (isAllDay) "00:00" else formatMillisToTime(dtStart)
                    val endTime = if (isAllDay) "23:59" else formatMillisToTime(normalizedEndMillis)
                    val memo = it.getString(descIdx)
                    val location = it.getString(locIdx)
                    val rrule = it.getString(rruleIdx)
                    val exdate = it.getString(exdateIdx)
                    val calendarId = it.getLong(calIdIdx)
                    val calendarName = it.getString(calNameIdx)
                    val eventColor = it.getInt(eventColorIdx)
                    val calendarColor = it.getInt(calColorIdx)

                    val reminders = fetchReminders(id)
                    if (!rrule.isNullOrEmpty()) {
                        Log.d(
                            REPEAT_DEBUG_TAG,
                            "반복 조회 값: id=$id, title=$title, dtStart=$dtStart, rawDtEnd=$dtEnd, duration=$durationStr, isAllDay=$isAllDay, startDate=$startDate, endDate=$endDate, startTime=$startTime, endTime=$endTime, rrule=$rrule"
                        )
                    }

                    scheduleList.add(
                        Schedule(
                            id = id,
                            originalId = originalId,
                            status = status,
                            title = title,
                            startDate = startDate,
                            endDate = endDate,
                            startTime = startTime,
                            endTime = endTime,
                            isAllDay = isAllDay,
                            memo = memo,
                            location = location,
                            repeatRule = rrule,
                            exDate = exdate,
                            calendarId = calendarId,
                            calendarDisplayName = calendarName,
                            calendarAccountName = null,
                            reminders = reminders,
                            eventColor = if (eventColor != 0) eventColor else null,
                            calendarColor = if (calendarColor != 0) calendarColor else null,
                            type = "NORMAL",
                            sourceType = "SYSTEM"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("ScheduleDataSource", "데이터 로드 중 오류 발생: ${e.message}")
        }
        scheduleList
    }
    private fun fetchReminders(eventId: Long): List<Int> {
        val reminderList = mutableListOf<Int>()
        val projection = arrayOf(CalendarContract.Reminders.MINUTES)
        val selection = "${CalendarContract.Reminders.EVENT_ID} = ?"
        val selectionArgs = arrayOf(eventId.toString())

        val cursor: Cursor? = applicationContext.contentResolver.query(
            CalendarContract.Reminders.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )

        cursor?.use {
            while (it.moveToNext()) {
                val minutes = it.getInt(it.getColumnIndexOrThrow(CalendarContract.Reminders.MINUTES))
                reminderList.add(minutes)
            }
        }
        return reminderList
    }
    
    private fun formatMillisToDate(millis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date(millis))
    }

    private fun formatMillisToUtcDate(millis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return sdf.format(Date(millis))
    }

    private fun formatMillisToTime(millis: Long): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date(millis))
    }

    private fun normalizeEndMillisForDisplay(
        startMillis: Long,
        endMillis: Long,
        isAllDay: Boolean
    ): Long {
        if (!isAllDay || endMillis <= startMillis) return endMillis

        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = endMillis
        }
        val isExclusiveMidnightEnd = calendar.get(Calendar.HOUR_OF_DAY) == 0 &&
            calendar.get(Calendar.MINUTE) == 0 &&
            calendar.get(Calendar.SECOND) == 0

        return if (isExclusiveMidnightEnd) {
            endMillis - 1L
        } else {
            endMillis
        }
    }

    private data class CalendarEventTiming(
        val startMillis: Long,
        val endMillis: Long,
        val durationForRecurring: String,
        val timeZoneId: String
    )

    private fun buildCalendarEventTiming(
        startDate: String,
        endDate: String,
        startTime: String?,
        endTime: String?,
        isAllDay: Boolean
    ): CalendarEventTiming {
        return if (isAllDay) {
            val startLocalDate = LocalDate.parse(startDate)
            val endLocalDate = LocalDate.parse(endDate)
            val startMillis = startLocalDate.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
            val endMillis = endLocalDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
            val inclusiveDays = java.time.temporal.ChronoUnit.DAYS.between(startLocalDate, endLocalDate).toInt() + 1

            CalendarEventTiming(
                startMillis = startMillis,
                endMillis = endMillis,
                durationForRecurring = "P${inclusiveDays}D",
                timeZoneId = "UTC"
            )
        } else {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val startMillis = sdf.parse("$startDate ${startTime ?: "00:00"}")?.time ?: System.currentTimeMillis()
            val endMillis = sdf.parse("$endDate ${endTime ?: "23:59"}")?.time ?: (startMillis + 3600000)
            val durationSeconds = ((endMillis - startMillis) / 1000).coerceAtLeast(0)

            CalendarEventTiming(
                startMillis = startMillis,
                endMillis = endMillis,
                durationForRecurring = "P${durationSeconds}S",
                timeZoneId = TimeZone.getDefault().id
            )
        }
    }

    // rrule에 맞춰서 변환
    private fun buildRRule(info: RepeatInfo?): String? {
        if (info == null || info.repeatType.uppercase() == "NONE") return null

        return try {
            val rrule = StringBuilder("FREQ=${info.repeatType.uppercase()}")
            if (info.repeatInterval > 1) rrule.append(";INTERVAL=${info.repeatInterval}")

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

            if (info.endType.uppercase() == "COUNT") {
                rrule.append(";COUNT=${info.endCount}")
            } else if (info.endType.uppercase() == "DATE") {
                // 💡 1. 로컬 변수에 값을 복사합니다.
                val endDate = info.repeatEndDate

                // 💡 2. 복사한 로컬 변수로 체크하면 Smart Cast가 작동합니다.
                if (!endDate.isNullOrEmpty()) {
                    val untilDate = endDate.replace("-", "")
                    rrule.append(";UNTIL=${untilDate}T235959Z")
                }
            }

            rrule.toString()
        } catch (e: Exception) { null }
    }

    suspend fun updateCalendarEvent(schedule: Schedule): Boolean = withContext(Dispatchers.IO) {
        val contentResolver = applicationContext.contentResolver

        // 1. 날짜 및 시간 파싱 (기존 로직 유지)
        val timing = buildCalendarEventTiming(
            startDate = schedule.startDate,
            endDate = schedule.endDate,
            startTime = schedule.startTime,
            endTime = schedule.endTime,
            isAllDay = schedule.isAllDay
        )
        Log.d("CALENDAR_UPDATE", "수정 시도 - 제목: ${schedule.title}, 장소: ${schedule.location}")
        // 2. 업데이트할 데이터 세팅
        val values = ContentValues().apply {
            put(CalendarContract.Events.TITLE, schedule.title)
            put(CalendarContract.Events.DESCRIPTION, schedule.memo)
            put(CalendarContract.Events.EVENT_LOCATION, schedule.location ?: "")
            put(CalendarContract.Events.DTSTART, timing.startMillis)
            put(CalendarContract.Events.ALL_DAY, if (schedule.isAllDay) 1 else 0)
            put(CalendarContract.Events.EVENT_TIMEZONE, timing.timeZoneId)

            // 💡 [해결 1] 캘린더 ID 명시적 업데이트 (중요!)
            put(CalendarContract.Events.CALENDAR_ID, schedule.calendarId)

            // 색상 업데이트
            schedule.eventColor?.let {
                put(CalendarContract.Events.EVENT_COLOR, it)
            }

            if (!schedule.repeatRule.isNullOrEmpty()) {
                put(CalendarContract.Events.RRULE, schedule.repeatRule)
                put(CalendarContract.Events.DURATION, timing.durationForRecurring)
                putNull(CalendarContract.Events.DTEND)
            } else {
                put(CalendarContract.Events.DTEND, timing.endMillis)
                putNull(CalendarContract.Events.DURATION)
                putNull(CalendarContract.Events.RRULE)
            }
        }

        // 3. 실제 업데이트 수행
        val updateUri = android.content.ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, schedule.id)
        val rowsAffected = try {
            contentResolver.update(updateUri, values, null, null)
        } catch (e: Exception) {
            Log.e("CALENDAR_UPDATE", "업데이트 실패: ${e.message}")
            0
        }

        // 4. 💡 [해결 2] 알림(Reminders) 정보 갱신
        if (rowsAffected > 0) {
            updateReminders(schedule.id, schedule.reminders)
        }

        Log.d("CALENDAR_UPDATE", "ID ${schedule.id} 업데이트 완료 (영향받은 행: $rowsAffected)")
        rowsAffected > 0
    }

    // 알림 테이블 수정을 위한 보조 함수
    private fun updateReminders(eventId: Long, minutesList: List<Int>) {
        val cr = applicationContext.contentResolver
        // 기존 알림 삭제
        cr.delete(CalendarContract.Reminders.CONTENT_URI, "${CalendarContract.Reminders.EVENT_ID} = ?", arrayOf(eventId.toString()))

        // 새로운 알림 삽입
        minutesList.forEach { minutes ->
            val values = ContentValues().apply {
                put(CalendarContract.Reminders.MINUTES, minutes)
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            }
            cr.insert(CalendarContract.Reminders.CONTENT_URI, values)
        }
    }

}

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
import java.util.*
import dagger.hilt.android.qualifiers.ApplicationContext // 추가
import javax.inject.Inject // 추가
class NormalScheduleRemoteDataSource @Inject constructor(
    @ApplicationContext private val applicationContext: Context
) {
    suspend fun insertToCalendarProvider(
        request: CreateScheduleRequest,
        selectedCalendarId: Long? = null,
        selectedColor: Int? = null
    ): Long = withContext(Dispatchers.IO) {
        val contentResolver = applicationContext.contentResolver

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val startMillis = sdf.parse("${request.startDate} ${request.startTime ?: "00:00"}")?.time ?: System.currentTimeMillis()
        val endMillis = sdf.parse("${request.endDate} ${request.endTime ?: "23:59"}")?.time ?: (startMillis + 3600000)

        val generatedRrule = buildRRule(request.repeatInfo)

        val values = ContentValues().apply {
            put(CalendarContract.Events.TITLE, request.title)
            put(CalendarContract.Events.DESCRIPTION, request.memo)
            put(CalendarContract.Events.EVENT_LOCATION, request.place?.targetName ?: "")
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.ALL_DAY, if (request.isAllDay) 1 else 0)
            put(CalendarContract.Events.CALENDAR_ID, selectedCalendarId ?: 1L)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)

            // [색상 처리]
            val finalColor = if (selectedColor != null && selectedColor != 0) selectedColor
            else android.graphics.Color.parseColor("#DC354B")
            put(CalendarContract.Events.EVENT_COLOR, finalColor)

            // [핵심 보완: 반복 일정일 경우 DURATION 처리]
            if (!generatedRrule.isNullOrEmpty()) {
                put(CalendarContract.Events.RRULE, generatedRrule)

                // 반복 일정은 DTEND 대신 DURATION 사용 권장 (P3600S = 3600초 = 1시간)
                val durationSeconds = (endMillis - startMillis) / 1000
                put(CalendarContract.Events.DURATION, "P${durationSeconds}S")
                // 반복 일정 시 DTEND는 null로 비워두는 것이 표준입니다.
                putNull(CalendarContract.Events.DTEND)
            } else {
                // 반복이 아닐 때는 일반적인 DTEND 사용
                put(CalendarContract.Events.DTEND, endMillis)
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
                    val memo = it.getString(descIdx)
                    val location = it.getString(locIdx)
                    val rrule = it.getString(rruleIdx)
                    val exdate = it.getString(exdateIdx)
                    val calendarId = it.getLong(calIdIdx)
                    val calendarName = it.getString(calNameIdx)
                    val eventColor = it.getInt(eventColorIdx)
                    val calendarColor = it.getInt(calColorIdx)

                    val reminders = fetchReminders(id)

                    scheduleList.add(
                        Schedule(
                            id = id,
                            originalId = originalId,
                            status = status,
                            title = title,
                            startDate = formatMillisToDate(dtStart),
                            endDate = formatMillisToDate(dtEnd),
                            startTime = formatMillisToTime(dtStart),
                            endTime = formatMillisToTime(dtEnd),
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

    private fun formatMillisToTime(millis: Long): String {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        return sdf.format(Date(millis))
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

        // 1. 날짜 및 시간 파싱
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val startMillis = try {
            sdf.parse("${schedule.startDate} ${schedule.startTime ?: "00:00"}")?.time ?: System.currentTimeMillis()
        } catch (e: Exception) { System.currentTimeMillis() }

        val endMillis = try {
            sdf.parse("${schedule.endDate} ${schedule.endTime ?: "23:59"}")?.time ?: startMillis
        } catch (e: Exception) { startMillis }

        // 2. 업데이트할 데이터 세팅
        val values = ContentValues().apply {
            put(CalendarContract.Events.TITLE, schedule.title)
            put(CalendarContract.Events.DESCRIPTION, schedule.memo)
            put(CalendarContract.Events.EVENT_LOCATION, schedule.location ?: "")
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.ALL_DAY, if (schedule.isAllDay) 1 else 0)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)

            // 색상 업데이트 (null이 아닐 때만)
            schedule.eventColor?.let {
                put(CalendarContract.Events.EVENT_COLOR, it)
            }

            // 💡 반복 일정(RRULE) 처리 로직
            if (!schedule.repeatRule.isNullOrEmpty()) {
                put(CalendarContract.Events.RRULE, schedule.repeatRule)

                // 반복 일정은 DTEND 대신 DURATION 사용 (표준 규격)
                val durationSeconds = (endMillis - startMillis) / 1000
                put(CalendarContract.Events.DURATION, "P${durationSeconds}S")
                putNull(CalendarContract.Events.DTEND)
            } else {
                // 일반 일정은 DURATION 제거하고 DTEND 사용
                put(CalendarContract.Events.DTEND, endMillis)
                putNull(CalendarContract.Events.DURATION)
                putNull(CalendarContract.Events.RRULE)
            }
        }

        // 3. 실제 업데이트 수행 (ID 기반)
        val updateUri = android.content.ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, schedule.id)
        val rowsAffected = try {
            contentResolver.update(updateUri, values, null, null)
        } catch (e: Exception) {
            Log.e("CALENDAR_UPDATE", "업데이트 실패: ${e.message}")
            0
        }

        Log.d("CALENDAR_UPDATE", "ID ${schedule.id} 업데이트 완료 (영향받은 행: $rowsAffected)")
        rowsAffected > 0
    }

}

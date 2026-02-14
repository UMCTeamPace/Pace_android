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

        // 1. 조회 범위 설정 (예: 과거 2.5년 전부터 미래 2.5년 후까지)
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, -30) // 2.5년 전으로 설정
        val startRange = calendar.timeInMillis

        calendar.add(Calendar.MONTH, 60) // 위에서 -30 했으므로 +60을 해야 미래 2.5년이 됨
        val endRange = calendar.timeInMillis

        // 2. 쿼리 조건 수정 (시작일과 종료일 사이의 이벤트를 가져옴)
        // 과거 데이터도 가져오고 싶다면 단순히 >= 조건을 바꾸거나 범위를 지정합니다.
        val selection = "(${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?) AND " +
                "(${CalendarContract.Events.DELETED} = 0) AND " +
                "(${CalendarContract.Events.STATUS} IS NULL OR ${CalendarContract.Events.STATUS} != ${CalendarContract.Events.STATUS_CANCELED})"

        val selectionArgs = arrayOf(startRange.toString(), endRange.toString())

        val projection = arrayOf(
            CalendarContract.Events._ID,
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
            CalendarContract.Events.DELETED
        )



        try {
            // 2. 권한 확인이 통과된 경우에만 쿼리를 실행
            val cursor: Cursor? = applicationContext.contentResolver.query(

                CalendarContract.Events.CONTENT_URI,

                projection,

                selection,

                selectionArgs,

                CalendarContract.Events.DTSTART + " ASC"

            )

    

            cursor?.use {
                // 인덱스 먼저 다 뽑기 (성능 및 안전성)
                val idIdx = it.getColumnIndexOrThrow(CalendarContract.Events._ID)
                val titleIdx = it.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
                val dtStartIdx = it.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
                val dtEndIdx = it.getColumnIndexOrThrow(CalendarContract.Events.DTEND)
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
                    // 1. 삭제 여부 확인
                    val isDeleted = it.getInt(deletedIdx)
                    if (isDeleted == 1) continue

                    // 2. 값 추출 (이 부분이 빠져있었습니다!)
                    val id = it.getLong(idIdx)
                    val title = it.getString(titleIdx) ?: ""
                    val dtStart = it.getLong(dtStartIdx)
                    val dtEnd = it.getLong(dtEndIdx)
                    val isAllDay = it.getInt(allDayIdx) == 1
                    val memo = it.getString(descIdx)
                    val location = it.getString(locIdx)
                    val rrule = it.getString(rruleIdx)
                    val exdate = it.getString(exdateIdx)
                    val calendarId = it.getLong(calIdIdx)
                    val calendarName = it.getString(calNameIdx)
                    val eventColor = it.getInt(eventColorIdx)
                    val calendarColor = it.getInt(calColorIdx)

                    // 3. 알림 데이터 가져오기
                    val reminders = fetchReminders(id)

                    // 4. 리스트에 추가
                    scheduleList.add(

                        Schedule(

                            id = id,

                            title = title,

                            startDate = formatMillisToDate(dtStart),

                            endDate = formatMillisToDate(dtEnd),

                            startTime = formatMillisToTime(dtStart),

                            endTime = formatMillisToTime(dtEnd),

                            isAllDay = isAllDay,

                            memo = memo,

                            location = location,

                            repeatRule = rrule,
                            exdate = exdate,
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
    } catch (e: SecurityException) {
        // 3. 만약의 경우를 대비한 2중 방어막
        android.util.Log.e("ScheduleDataSource", "SecurityException 발생: ${e.message}")
        return@withContext emptyList<Schedule>()
    } catch (e: Exception) {
        android.util.Log.e("ScheduleDataSource", "데이터 로드 중 오류 발생: ${e.message}")
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
            } else if (info.endType.uppercase() == "DATE" && !info.repeatEndDate.isNullOrEmpty()) {
                val untilDate = info.repeatEndDate.replace("-", "")
                rrule.append(";UNTIL=${untilDate}T235959Z")
            }

            rrule.toString()
        } catch (e: Exception) { null }
    }
}

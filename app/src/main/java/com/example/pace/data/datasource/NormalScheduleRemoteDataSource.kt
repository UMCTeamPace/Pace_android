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

        // 1. 시간 계산
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val startMillis = sdf.parse("${request.startDate} ${request.startTime ?: "00:00"}")?.time ?: System.currentTimeMillis()
        val endMillis = sdf.parse("${request.endDate} ${request.endTime ?: "23:59"}")?.time ?: (startMillis + 3600000)

        // 2. 반복 규칙 생성 (중복 호출 방지)
        val generatedRrule = buildRRule(request.repeatInfo)

        // [로그 추가] 시스템에 들어가기 직전 데이터 확인
        Log.d("CALENDAR_INSERT", "=== 시스템 삽입 시도 ===")
        Log.d("CALENDAR_INSERT", "ID: $selectedCalendarId, Color: $selectedColor, RRULE: $generatedRrule")

        val values = ContentValues().apply {
            put(CalendarContract.Events.TITLE, request.title)
            put(CalendarContract.Events.DESCRIPTION, request.memo)
            put(CalendarContract.Events.EVENT_LOCATION, request.place?.targetName ?: "")
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, endMillis)
            put(CalendarContract.Events.ALL_DAY, if (request.isAllDay) 1 else 0)

            // 중요: 캘린더 ID가 1L이면 기본 로컬 캘린더일 확률이 높으며, 색상 변경을 제한할 수 있습니다.
            put(CalendarContract.Events.CALENDAR_ID, selectedCalendarId ?: 1L)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)

            // [색상 적용]
            // selectedColor가 0이거나 null이면 기본색 사용
            val finalColor = if (selectedColor != null && selectedColor != 0) {
                selectedColor
            } else {
                android.graphics.Color.parseColor("#DC354B")
            }
            put(CalendarContract.Events.EVENT_COLOR, finalColor)

            // [반복 설정 적용]
            if (!generatedRrule.isNullOrEmpty()) {
                put(CalendarContract.Events.RRULE, generatedRrule)
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
        if (info == null) return null // 여기서 null이면 반복이 안 됩니다.
        Log.d("CALENDAR_INSERT", "buildRRule 내부 진입: $info")

        return try {
            val rrule = StringBuilder("FREQ=${info.repeatType.uppercase()}")
            if (info.repeatInterval > 0) rrule.append(";INTERVAL=${info.repeatInterval}")

            if (!info.daysOfWeek.isNullOrEmpty()) {
                // 요일 형식이 "MONDAY"면 "MO"로, "MON"이면 "MO"로 변환 필요
                val systemDays = info.daysOfWeek.split(",")
                    .map { it.trim().take(2).uppercase() }
                    .joinToString(",")
                rrule.append(";BYDAY=$systemDays")
            }

            if (info.endType.uppercase() == "COUNT") {
                rrule.append(";COUNT=${info.endCount}")
            } else if (info.endType.uppercase() == "DATE" && !info.repeatEndDate.isNullOrEmpty()) {
                val untilDate = info.repeatEndDate.replace("-", "")
                rrule.append(";UNTIL=${untilDate}T235959Z")
            }

            val result = rrule.toString()
            Log.d("CALENDAR_INSERT", "생성된 최종 문자열: $result")
            result
        } catch (e: Exception) {
            Log.e("CALENDAR_INSERT", "buildRRule 에러: ${e.message}")
            null
        }
    }
}

package com.example.pace.data.datasource

import android.content.Context
import android.database.Cursor
import android.provider.CalendarContract
import android.util.Log
import com.example.pace.data.model.Schedule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class NormalScheduleRemoteDataSource(private val applicationContext: Context) {

    suspend fun getSchedules(): List<Schedule> = withContext(Dispatchers.IO) {
        val scheduleList = mutableListOf<Schedule>()

        // 1. 조회 범위 설정 (예: 과거 1년 전부터 미래 1년 후까지)
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.YEAR, -1) // 1년 전으로 설정
        val startRange = calendar.timeInMillis

        calendar.add(Calendar.YEAR, 2) // 위에서 -1 했으므로 +2를 해야 미래 1년이 됨
        val endRange = calendar.timeInMillis

        // 2. 쿼리 조건 수정 (시작일과 종료일 사이의 이벤트를 가져옴)
        // 과거 데이터도 가져오고 싶다면 단순히 >= 조건을 바꾸거나 범위를 지정합니다.
        val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?"
        val selectionArgs = arrayOf(
            startRange.toString(),
            endRange.toString()
        )

        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.RRULE,
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.CALENDAR_DISPLAY_NAME,
            CalendarContract.Events.EVENT_COLOR,
            CalendarContract.Events.CALENDAR_COLOR
        )



    

            val cursor: Cursor? = applicationContext.contentResolver.query(

                CalendarContract.Events.CONTENT_URI,

                projection,

                selection,

                selectionArgs,

                CalendarContract.Events.DTSTART + " ASC"

            )

    

            cursor?.use {

                while (it.moveToNext()) {

                    val id = it.getLong(it.getColumnIndexOrThrow(CalendarContract.Events._ID))

                    val title = it.getString(it.getColumnIndexOrThrow(CalendarContract.Events.TITLE))

                    val dtStart = it.getLong(it.getColumnIndexOrThrow(CalendarContract.Events.DTSTART))

                    val dtEnd = it.getLong(it.getColumnIndexOrThrow(CalendarContract.Events.DTEND))

                    val isAllDay = it.getInt(it.getColumnIndexOrThrow(CalendarContract.Events.ALL_DAY)) == 1

                    val memo = it.getString(it.getColumnIndexOrThrow(CalendarContract.Events.DESCRIPTION))

                    val location = it.getString(it.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION))

                    val rrule = it.getString(it.getColumnIndexOrThrow(CalendarContract.Events.RRULE))

                    val calendarId = it.getLong(it.getColumnIndexOrThrow(CalendarContract.Events.CALENDAR_ID))

                    val calendarName = it.getString(it.getColumnIndexOrThrow(CalendarContract.Events.CALENDAR_DISPLAY_NAME))

                    val eventColor = it.getInt(it.getColumnIndexOrThrow(CalendarContract.Events.EVENT_COLOR))

                    val calendarColor = it.getInt(it.getColumnIndexOrThrow(CalendarContract.Events.CALENDAR_COLOR))

                    Log.d("ScheduleDataSource", "Fetched colors for ${title}: eventColor=$eventColor, calendarColor=$calendarColor")

    

                    val reminders = fetchReminders(id)

    

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

                            calendarId = calendarId,

                            calendarDisplayName = calendarName,

                            calendarAccountName = null,

                            reminders = reminders,

                            eventColor = eventColor,

                            calendarColor = calendarColor,

                            type = "NORMAL" // Set the type for schedules from this source

                        )

                    )

                }

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
}

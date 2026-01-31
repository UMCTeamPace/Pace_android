package com.example.pace.data.repository

import android.content.Context
import android.database.Cursor
import android.provider.CalendarContract
import com.example.pace.data.db.ScheduleDao
import com.example.pace.data.model.Schedule
import com.example.pace.data.createCalendarObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class ScheduleRepository(
    private val scheduleDao: ScheduleDao,
    private val applicationContext: Context
) {

    val allSchedules: Flow<List<Schedule>> = scheduleDao.getAllSchedules()
    val calendarEvents: Flow<Unit> = createCalendarObserver(applicationContext)

    suspend fun updateSchedule(schedule: Schedule) {
        scheduleDao.updateSchedule(schedule)
    }

    suspend fun refreshSchedules() {
        val freshSchedules = fetchFromCalendarProvider()
        // Here, we could be smarter and merge data to preserve pinned status.
        // For now, a simple clear and insert is implemented.
        scheduleDao.clearAll()
        scheduleDao.insertAll(freshSchedules)
    }

    private suspend fun fetchFromCalendarProvider(): List<Schedule> = withContext(Dispatchers.IO) {
        val scheduleList = mutableListOf<Schedule>()
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
            CalendarContract.Events.CALENDAR_DISPLAY_NAME
        )
        
        val calendar = Calendar.getInstance()
        val selection = "${CalendarContract.Events.DTSTART} >= ?"
        val selectionArgs = arrayOf(calendar.timeInMillis.toString())

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
                        reminders = reminders
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

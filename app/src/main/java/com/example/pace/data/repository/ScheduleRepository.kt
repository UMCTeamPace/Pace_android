package com.example.pace.data.repository

import android.content.Context
import com.example.pace.data.db.ScheduleDao
import com.example.pace.data.datasource.NormalScheduleRemoteDataSource
import com.example.pace.data.model.Schedule
import com.example.pace.data.createCalendarObserver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import biweekly.component.VEvent
import biweekly.property.DateStart
import biweekly.property.ExceptionDates
import biweekly.property.RecurrenceRule
import biweekly.util.DayOfWeek
import biweekly.util.Frequency
import biweekly.util.ICalDate
import biweekly.util.Recurrence
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.util.Date
import java.util.Locale

class ScheduleRepository(
    private val scheduleDao: ScheduleDao,
    private val normalScheduleDataSource: NormalScheduleRemoteDataSource,
    applicationContext: Context
) {

    val allSchedules: Flow<List<Schedule>> = scheduleDao.getAllSchedules()
        .map { rawList ->
            expandSchedules(rawList)
        }
        .flowOn(Dispatchers.IO)

    val calendarEvents: Flow<Unit> = createCalendarObserver(applicationContext)

    suspend fun updateSchedule(schedule: Schedule) {
        scheduleDao.updateSchedule(schedule)
    }

    suspend fun refreshSchedules() {
        val normalSchedules = normalScheduleDataSource.getSchedules()
        val allRemoteSchedules = normalSchedules
        val localSchedules = scheduleDao.getAllSchedulesOnce()
        val localScheduleMap = localSchedules.associateBy { it.id }

        val mergedSchedules = allRemoteSchedules.map { remoteSchedule ->
            val localSchedule = localScheduleMap[remoteSchedule.id]
            if (localSchedule != null) {
                remoteSchedule.copy(isPinned = localSchedule.isPinned)
            } else {
                remoteSchedule
            }
        }

        val remoteScheduleIds = allRemoteSchedules.map { it.id }.toSet()
        val schedulesToDelete = localSchedules.filter { it.id !in remoteScheduleIds }

        scheduleDao.deleteAll(schedulesToDelete)
        scheduleDao.insertAll(mergedSchedules)
    }

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
                val dtStartDate = try {
                    sdf.parse(dtStartString)
                } catch (e: Exception) {
                    null
                }

                if (dtStartDate != null) {
                    try {
                        val event = VEvent()
                        event.setDateStart(DateStart(dtStartDate))

                        val recur = parseRecurrenceString(schedule.repeatRule)
                        if (recur != null) {
                            event.setRecurrenceRule(RecurrenceRule(recur))
                        }

                        if (!schedule.exdate.isNullOrBlank()) {
                            val exdates = ExceptionDates()
                            schedule.exdate.split(',').forEach { dateStr ->
                                try {
                                    val date = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.getDefault()).apply {
                                        timeZone = TimeZone.getTimeZone("UTC")
                                    }.parse(dateStr.trim())
                                    if(date != null) {
                                        exdates.getValues().add(ICalDate(date, true))
                                    }
                                } catch (e: Exception) {
                                     try {
                                        val date = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).parse(dateStr.trim())
                                        if (date != null) {
                                            exdates.getValues().add(ICalDate(date, false))
                                        }
                                    } catch (e2: Exception) {
                                        android.util.Log.w("ScheduleRepository", "Failed to parse EXDATE: $dateStr")
                                    }
                                }
                            }
                            if (exdates.getValues().isNotEmpty()) {
                                event.addExceptionDates(exdates)
                            }
                        }

                        val iterator = event.getDateIterator(TimeZone.getDefault())
                        iterator.advanceTo(rangeStartDate)

                        var count = 0
                        while (iterator.hasNext() && count < 1000) {
                            val occurrenceDate = iterator.next()
                            if (occurrenceDate.after(rangeEndDate)) break

                            val oInstant = occurrenceDate.toInstant()
                            val oZDT = oInstant.atZone(ZoneId.systemDefault())
                            val oLocalDate = oZDT.toLocalDate()
                            val oLocalTime = oZDT.toLocalTime()

                            expandedList.add(schedule.copy(
                                startDate = oLocalDate.format(dateFormatter),
                                endDate = oLocalDate.format(dateFormatter),
                                startTime = oLocalTime.format(timeFormatter)
                            ))
                            count++
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("ScheduleRepository", "Error expanding schedule ID: ${schedule.id}", e)
                        if (startLocalDate.isBefore(rangeEndLocalDate) && endLocalDate.isAfter(rangeStartLocalDate)) {
                            expandedList.add(schedule)
                        }
                    }
                } else {
                     if (startLocalDate.isBefore(rangeEndLocalDate) && endLocalDate.isAfter(rangeStartLocalDate)) {
                        expandedList.add(schedule)
                    }
                }
            }
            else if (startLocalDate.isBefore(endLocalDate)) {
                var current = startLocalDate
                while (!current.isAfter(endLocalDate)) {
                    if (current.isBefore(rangeEndLocalDate) && !current.isBefore(rangeStartLocalDate)) {
                        expandedList.add(schedule.copy(startDate = current.format(dateFormatter), endDate = current.format(dateFormatter)))
                    }
                    current = current.plusDays(1)
                }
            }
            else {
                if (startLocalDate.isBefore(rangeEndLocalDate) && !startLocalDate.isBefore(rangeStartLocalDate)) {
                    expandedList.add(schedule)
                }
            }
        }
        return expandedList
    }

    private fun parseRecurrenceString(rruleStr: String): Recurrence? {
        try {
            val parts = rruleStr.split(";")
            val params = parts.associate {
                val split = it.split("=")
                if (split.size == 2) split[0].uppercase() to split[1] else "" to ""
            }

            val freqStr = params["FREQ"] ?: return null
            val frequency = try { Frequency.valueOf(freqStr) } catch(e:Exception) { return null }

            val builder = Recurrence.Builder(frequency)
            params["INTERVAL"]?.toIntOrNull()?.let { builder.interval(it) }
            params["COUNT"]?.toIntOrNull()?.let { builder.count(it) }

            params["UNTIL"]?.let { untilStr ->
                try {
                    val date = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.getDefault()).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }.parse(untilStr)
                    if(date != null) builder.until(date)
                } catch(e:Exception) {
                    try {
                        val date = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).parse(untilStr)
                        if(date != null) builder.until(date)
                    } catch(e2: Exception) {}
                }
            }

            params["BYDAY"]?.let { byDayStr ->
                byDayStr.split(",").forEach { dayCode ->
                    try {
                        val dayOfWeek = when(dayCode.takeLast(2)) {
                            "SU" -> DayOfWeek.SUNDAY
                            "MO" -> DayOfWeek.MONDAY
                            "TU" -> DayOfWeek.TUESDAY
                            "WE" -> DayOfWeek.WEDNESDAY
                            "TH" -> DayOfWeek.THURSDAY
                            "FR" -> DayOfWeek.FRIDAY
                            "SA" -> DayOfWeek.SATURDAY
                            else -> null
                        }
                        val prefix = dayCode.dropLast(2)
                        val num = if (prefix.isNotEmpty()) prefix.toInt() else null
                        if(dayOfWeek != null) builder.byDay(num, dayOfWeek)
                    } catch(e: Exception) {}
                }
            }

            return builder.build()
        } catch (e: Exception) {
            android.util.Log.e("ScheduleRepo", "RRULE Parsing Error", e)
            return null
        }
    }

    fun getUsedColors(): Flow<List<String>> {
        return scheduleDao.getUsedColorsRaw().map { list ->
            list.mapNotNull { it.color }
        }
    }

    suspend fun searchSchedules(
        query: String,
        colors: Set<String>,
        includeRoute: Boolean,
        startDate: String,
        endDate: String
    ): List<Schedule> {
        val searchQuery = "%$query%"
        val raw = scheduleDao.searchSchedulesWithRange(searchQuery, startDate, endDate)
        if (raw.isEmpty()) return emptyList()
        val expanded = expandSchedules(raw)
        return expanded.filter { schedule ->
            val sColor = schedule.eventColor?.toString() ?: ""
            val cColor = schedule.calendarColor?.toString() ?: ""
            val colorMatch = colors.isEmpty() ||
                    colors.any { it.equals(sColor, ignoreCase = true) } ||
                    colors.any { it.equals(cColor, ignoreCase = true) }
            val routeMatch = includeRoute || schedule.type != "ROUTE"
            colorMatch && routeMatch
        }
    }
}
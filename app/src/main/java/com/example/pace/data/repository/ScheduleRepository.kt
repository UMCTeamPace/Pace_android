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

// TODO: Add RouteScheduleRemoteDataSource here when ready
// import com.example.pace.data.datasource.RouteScheduleRemoteDataSource

class ScheduleRepository(
    private val scheduleDao: ScheduleDao,
    private val normalScheduleDataSource: NormalScheduleRemoteDataSource,
    // TODO: Inject RouteScheduleRemoteDataSource here when ready
    // private val routeScheduleDataSource: RouteScheduleRemoteDataSource,
    applicationContext: Context
) {

    // 원본 데이터를 가져와서 UI가 필요한 형태로 전개(Expand)합니다.
    val allSchedules: Flow<List<Schedule>> = scheduleDao.getAllSchedules().map { rawList ->
        expandSchedules(rawList)
    }
    val calendarEvents: Flow<Unit> = createCalendarObserver(applicationContext)

    suspend fun updateSchedule(schedule: Schedule) {
        scheduleDao.updateSchedule(schedule)
    }

    /**
     * Refreshes all schedules from all remote data sources and merges them
     * with the local database, preserving local-only data like 'isPinned'.
     */
    suspend fun refreshSchedules() {
        // --- 1. Fetch from all remote sources ---
        val normalSchedules = normalScheduleDataSource.getSchedules()
        // TODO: Fetch from route data source when ready
        // val routeSchedules = routeScheduleDataSource.getSchedules()
        
        // For now, we only have normal schedules. In the future, combine lists here.
        val allRemoteSchedules = normalSchedules

        // --- 2. Fetch current local data ---
        val localSchedules = scheduleDao.getAllSchedulesOnce()
        val localScheduleMap = localSchedules.associateBy { it.id }

        // --- 3. Perform Smart Merge ---
        val mergedSchedules = allRemoteSchedules.map { remoteSchedule ->
            val localSchedule = localScheduleMap[remoteSchedule.id]
            if (localSchedule != null) {
                // Preserve local-only data by merging
                remoteSchedule.copy(isPinned = localSchedule.isPinned)
            } else {
                remoteSchedule
            }
        }

        // --- 4. Identify and delete stale schedules ---
        val remoteScheduleIds = allRemoteSchedules.map { it.id }.toSet()
        val schedulesToDelete = localSchedules.filter { it.id !in remoteScheduleIds }

        // --- 5. Update database ---
        scheduleDao.deleteAll(schedulesToDelete)
        scheduleDao.insertAll(mergedSchedules)
    }

    private fun expandSchedules(rawSchedules: List<Schedule>): List<Schedule> {
        val expandedList = mutableListOf<Schedule>()
        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        rawSchedules.forEach { schedule ->
            val startLocalDate = LocalDate.parse(schedule.startDate, dateFormatter)
            val endLocalDate = LocalDate.parse(schedule.endDate, dateFormatter)

            // 1. 기간 일정 전개 (시작일 ~ 종료일 사이의 모든 날짜에 표시)
            if (startLocalDate.isBefore(endLocalDate)) {
                var current = startLocalDate
                while (!current.isAfter(endLocalDate)) {
                    expandedList.add(schedule.copy(startDate = current.format(dateFormatter)))
                    current = current.plusDays(1)
                }
            }
            // 2. 반복 일정 전개 (repeatRule이 있는 경우)
            else if (!schedule.repeatRule.isNullOrEmpty()) {
                // 간단한 매주(WEEKLY) 반복 예시 (필요에 따라 RRULE 파싱 라이브러리 사용 권장)
                if (schedule.repeatRule!!.contains("WEEKLY")) {
                    for (i in 0..24) { // 향후 약 6개월치 전개
                        val repeatedDate = startLocalDate.plusWeeks(i.toLong())
                        expandedList.add(schedule.copy(startDate = repeatedDate.format(dateFormatter)))
                    }
                } else {
                    expandedList.add(schedule)
                }
            }
            // 3. 일반 단일 일정
            else {
                expandedList.add(schedule)
            }
        }
        return expandedList
    }

}


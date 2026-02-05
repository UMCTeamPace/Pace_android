package com.example.pace.data.repository

import android.content.Context
import com.example.pace.data.db.ScheduleDao
import com.example.pace.data.datasource.NormalScheduleRemoteDataSource
import com.example.pace.data.model.Schedule
import com.example.pace.data.createCalendarObserver
import kotlinx.coroutines.flow.Flow

// TODO: Add RouteScheduleRemoteDataSource here when ready
// import com.example.pace.data.datasource.RouteScheduleRemoteDataSource

class ScheduleRepository(
    private val scheduleDao: ScheduleDao,
    private val normalScheduleDataSource: NormalScheduleRemoteDataSource,
    // TODO: Inject RouteScheduleRemoteDataSource here when ready
    // private val routeScheduleDataSource: RouteScheduleRemoteDataSource,
    applicationContext: Context
) {

    val allSchedules: Flow<List<Schedule>> = scheduleDao.getAllSchedules()
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
}


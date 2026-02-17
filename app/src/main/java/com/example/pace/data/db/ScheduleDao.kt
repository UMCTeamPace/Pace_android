package com.example.pace.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.pace.data.model.Schedule
import kotlinx.coroutines.flow.Flow
import androidx.room.Delete
import com.example.pace.data.model.ColorResult

@Dao
interface ScheduleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(schedules: List<Schedule>)

    @Query("SELECT * FROM schedules ORDER BY start_date ASC, start_time ASC")
    fun getAllSchedules(): Flow<List<Schedule>>
    
    @Query("SELECT * FROM schedules")
    suspend fun getAllSchedulesOnce(): List<Schedule>

    @Update
    suspend fun updateSchedule(schedule: Schedule)

    @Delete
    suspend fun deleteAll(schedules: List<Schedule>)

    @Query("DELETE FROM schedules")
    suspend fun clearAll()

    @Query("SELECT * FROM schedules WHERE title LIKE :query")
    suspend fun searchSchedulesOnce(query: String): List<Schedule>

    @Query("""
        SELECT * FROM schedules 
        WHERE title LIKE :query 
        AND start_date <= :endDate 
        AND end_date >= :startDate
    """)
    suspend fun searchSchedulesWithRange(
        query: String,
        startDate: String,
        endDate: String
    ): List<Schedule>


        @Query("""
        SELECT event_color AS color FROM schedules WHERE event_color IS NOT NULL
        UNION
        SELECT calendar_color AS color FROM schedules WHERE calendar_color IS NOT NULL
    """)
        fun getUsedColorsRaw(): Flow<List<ColorResult>>


    @Query("SELECT * FROM schedules WHERE source_type = 'SYSTEM' AND id IS NOT NULL")
    suspend fun getSchedulesWithSystemId(): List<Schedule>
    @Query("DELETE FROM schedules WHERE id = :scheduleId")
    suspend fun deleteScheduleById(scheduleId: Long)
    @Query("DELETE FROM schedules WHERE source_type = 'SYSTEM' AND id NOT IN (:currentSystemIds)")
    suspend fun deleteRemovedDeviceSchedules(currentSystemIds: List<Long>)
    @Query("SELECT * FROM schedules WHERE id = :id")
    suspend fun getScheduleById(id: Long): Schedule?
}

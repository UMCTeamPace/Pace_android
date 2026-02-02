package com.example.pace.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.pace.data.model.Schedule
import kotlinx.coroutines.flow.Flow
import androidx.room.Delete

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
}

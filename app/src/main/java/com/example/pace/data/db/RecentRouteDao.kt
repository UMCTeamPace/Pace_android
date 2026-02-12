package com.example.pace.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.pace.data.model.RecentRoute
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentRouteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentRoute(route: RecentRoute)

    @Query("SELECT * FROM recent_routes ORDER BY saveTime DESC")
    fun getRecentRoutes(): Flow<List<RecentRoute>>

    @Delete
    suspend fun deleteRecentRoute(route: RecentRoute)

    @Query("DELETE FROM recent_routes WHERE saveTime < :threshold")
    suspend fun deleteOldRoutes(threshold: Long)

    // 전체삭제 혹시 몰라서
    @Query("DELETE FROM recent_routes")
    suspend fun clearAllRecentRoutes()
}
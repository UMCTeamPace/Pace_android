package com.example.pace.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.pace.data.model.RecentPlace
import com.example.pace.data.model.RecentSearch
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchDao {
    //최근 검색
    @Query("SELECT * FROM recent_searches ORDER BY timestamp DESC")
    fun getRecentSearches(): Flow<List<RecentSearch>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearch(search: RecentSearch)

    @Delete
    suspend fun deleteSearch(search: RecentSearch)

    @Query("DELETE FROM recent_searches WHERE `query` = :query")
    suspend fun deleteSearchByQuery(query: String)

    @Query("DELETE FROM recent_searches")
    suspend fun clearAllSearches()

    //최근 장소
    @Query("SELECT * FROM recent_places ORDER BY timestamp DESC")
    fun getRecentPlaces(): Flow<List<RecentPlace>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlace(place: RecentPlace)

    @Delete
    suspend fun deletePlace(place: RecentPlace)

    @Query("DELETE FROM my_places WHERE type = :type")
    suspend fun deleteMyPlaceByType(type: String)

    @Query("DELETE FROM recent_places")
    suspend fun clearAllPlaces()

    // 30일 넘으면 장소 삭제
    @Query("DELETE FROM recent_places WHERE timestamp < :threshold")
    suspend fun deleteOldPlaces(threshold: Long)

    @Query("DELETE FROM recent_searches WHERE timestamp < :threshold")
    suspend fun deleteOldSearches(threshold: Long)
}
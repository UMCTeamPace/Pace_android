package com.example.pace.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.pace.data.model.MyPlace
import kotlinx.coroutines.flow.Flow

@Dao
interface MyPlaceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMyPlace(myPlace: MyPlace)

    @Query("SELECT * FROM my_places WHERE type = :type")
    fun getMyPlaceByType(type: String): Flow<MyPlace?>

    @Query("DELETE FROM my_places WHERE type = :type")
    suspend fun deleteMyPlaceByType(type: String)
}
package com.example.pace.data.db

import androidx.room.*
import com.example.pace.data.model.UserSettingsEntity

@Dao
interface UserSettingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettings(settings: UserSettingsEntity)

    @Query("SELECT * FROM user_settings WHERE id = 1")
    suspend fun getSettings(): UserSettingsEntity?

    // WorkManager가 동기화 안 된 데이터를 찾을 때 사용
    @Query("SELECT * FROM user_settings WHERE isSynced = false")
    suspend fun getUnsyncedSettings(): UserSettingsEntity?

    @Query("UPDATE user_settings SET isSynced = :synced WHERE id = 1")
    suspend fun updateSyncStatus(synced: Boolean)
}
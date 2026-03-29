package com.example.pace.data.db

import androidx.room.*
import com.example.pace.data.model.UserSettingsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserSettingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettings(settings: UserSettingsEntity)

    // 1. [추가] 온보딩 직후, 아직 서버 ID가 없는 임시 데이터(ID=1)를 찾기 위함
    @Query("SELECT * FROM user_settings WHERE isSynced = 0 LIMIT 1")
    suspend fun getUnsyncedSettings(): UserSettingsEntity?

    @Query("SELECT * FROM user_settings WHERE id = :memberId")
    suspend fun getSettings(memberId: Long): UserSettingsEntity?

    // 2. [추가] 서버에서 받은 진짜 memberId로 기존 데이터를 업데이트 (ID 자체를 변경)
    // 온보딩 완료 시점(ID 1 -> 서버ID)에 딱 한 번 사용됩니다.
    @Query("UPDATE user_settings SET id = :newId, isSynced = :synced WHERE id = 1")
    suspend fun updateIdAndSyncStatus(newId: Long, synced: Boolean)

    // 3. 일반적인 동기화 상태 업데이트 (이미 서버 ID를 가진 경우)
    @Query("UPDATE user_settings SET isSynced = :synced WHERE id = :memberId")
    suspend fun updateSyncStatus(memberId: Long, synced: Boolean)

    @Query("UPDATE user_settings SET earlyArrivalTime = :minutes, isSynced = 0 WHERE id = :memberId")
    suspend fun updateEarlyArrivalTime(memberId: Long, minutes: Int)

    @Query("SELECT * FROM user_settings WHERE id = :memberId")
    fun getSettingsFlow(memberId: Long): Flow<UserSettingsEntity?>

    @Query("SELECT * FROM user_settings ORDER BY lastUpdated DESC LIMIT 1") // 테이블명은 본인의 엔티티에 맞게 수정
    fun getUserSettings(): Flow<UserSettingsEntity?>

}
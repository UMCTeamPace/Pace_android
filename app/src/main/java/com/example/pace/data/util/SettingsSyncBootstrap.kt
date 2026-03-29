package com.example.pace.data.util

import android.util.Log
import com.example.pace.data.datasource.AuthDataStore
import com.example.pace.data.model.response.toEntity
import com.example.pace.data.repository.repository.SettingsRepository
import kotlinx.coroutines.flow.firstOrNull

private const val SETTINGS_SYNC_STALE_MS = 5 * 60 * 1000L

suspend fun syncMemberSettingsIfNeeded(
    authDataStore: AuthDataStore,
    settingsRepository: SettingsRepository,
    source: String,
    force: Boolean = false
) {
    val accessToken = authDataStore.getAccessToken()
    if (accessToken.isNullOrBlank()) {
        Log.d("SETTINGS_SYNC", "[$source] 액세스 토큰이 없어 설정 동기화를 건너뜁니다.")
        return
    }

    val current = settingsRepository.getUserSettings().firstOrNull()
    val isStale = current == null ||
        !current.isSynced ||
        System.currentTimeMillis() - current.lastUpdated > SETTINGS_SYNC_STALE_MS

    if (!force && !isStale) {
        Log.d("SETTINGS_SYNC", "[$source] 로컬 설정이 최신으로 판단되어 동기화를 건너뜁니다.")
        return
    }

    val bearerToken = if (accessToken.startsWith("Bearer ")) accessToken else "Bearer $accessToken"
    val response = settingsRepository.getMemberSettings(bearerToken)

    if (response.isSuccess && response.result != null) {
        settingsRepository.updateSettingsLocally(response.result.toEntity(current))
        Log.d("SETTINGS_SYNC", "[$source] 서버 설정을 로컬 DB에 반영했습니다.")
    } else {
        Log.e("SETTINGS_SYNC", "[$source] 설정 조회 실패: ${response.code}, ${response.message}")
    }
}

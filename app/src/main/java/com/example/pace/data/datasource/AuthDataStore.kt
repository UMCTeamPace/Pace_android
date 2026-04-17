package com.example.pace.data.datasource

import android.content.SharedPreferences
class AuthDataStore(private val sharedPreferences: SharedPreferences) {

    companion object {
        private const val KEY_ACCESS_TOKEN = "ACCESS_TOKEN"
        private const val KEY_REFRESH_TOKEN = "REFRESH_TOKEN"
        private const val KEY_TEMP_TOKEN = "TEMP_TOKEN"
        private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        // 추가: 권한 요청 기록 확인용 키
        private const val KEY_PERMISSION_REQUESTED = "permission_requested"
    }

    /**
     * 권한 관련 로직
     */

    // 권한 요청을 한 번이라도 시도했는지 여부 저장
    fun setPermissionRequested(requested: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_PERMISSION_REQUESTED, requested).apply()
    }

    // 권한 요청 기록 확인
    fun isPermissionRequested(): Boolean {
        return sharedPreferences.getBoolean(KEY_PERMISSION_REQUESTED, false)
    }

    fun saveAuthData(accessToken: String, refreshToken: String) {
        sharedPreferences.edit().apply {
            putString(KEY_ACCESS_TOKEN, accessToken)
            putString(KEY_REFRESH_TOKEN, refreshToken)
            apply()
        }
    }

    // 혹시 saveTokens라는 이름으로도 쓰고 계셨다면 중복 정의해두셔도 됩니다.
    fun saveTokens(accessToken: String, refreshToken: String) {
        saveAuthData(accessToken, refreshToken)
    }

    fun saveTempToken(tempToken: String) {
        sharedPreferences.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .putString(KEY_TEMP_TOKEN, tempToken)
            .putBoolean(KEY_ONBOARDING_COMPLETE, false)
            .apply()
    }

    fun deleteToken() {
        sharedPreferences.edit().remove(KEY_ACCESS_TOKEN).apply()
    }

    fun clearTokens() {
        sharedPreferences.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_TEMP_TOKEN)
            .apply()
    }

    fun clearAllData() {
        sharedPreferences.edit().clear().apply()
    }

    fun getAccessToken(): String? = sharedPreferences.getString(KEY_ACCESS_TOKEN, null)
    fun getRefreshToken(): String? = sharedPreferences.getString(KEY_REFRESH_TOKEN, null)
    fun getTempToken(): String? = sharedPreferences.getString(KEY_TEMP_TOKEN, null)

    fun clearTempToken() {
        sharedPreferences.edit().remove(KEY_TEMP_TOKEN).apply()
    }

    fun setOnboardingComplete(isComplete: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_ONBOARDING_COMPLETE, isComplete).apply()
    }

    fun isOnboardingComplete(): Boolean {
        return sharedPreferences.getBoolean(KEY_ONBOARDING_COMPLETE, false)
    }
}

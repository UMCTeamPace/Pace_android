package com.example.pace.data.datasource

import android.content.SharedPreferences
class AuthDataStore(private val sharedPreferences: SharedPreferences) {

    fun deleteToken() {
        sharedPreferences.edit().remove("ACCESS_TOKEN").apply()
    }

    fun clearTokens() {
        sharedPreferences.edit()
            .remove("ACCESS_TOKEN")
            .remove("REFRESH_TOKEN")
            .remove("TEMP_TOKEN")
            .apply()
    }

    fun clearAllData() {
        sharedPreferences.edit().clear().apply()
    }


    fun saveTokens(accessToken: String, refreshToken: String) {
        sharedPreferences.edit().apply {
            putString("ACCESS_TOKEN", accessToken)
            putString("REFRESH_TOKEN", refreshToken)
            apply()
        }
    }
    fun saveAuthData(accessToken: String, refreshToken: String) {
        sharedPreferences.edit().apply {
            putString("ACCESS_TOKEN", accessToken)
            putString("REFRESH_TOKEN", refreshToken)
            apply()
        }
    }

    fun saveTempToken(tempToken: String) {
        sharedPreferences.edit()
            .remove("ACCESS_TOKEN")
            .remove("REFRESH_TOKEN")
            .putString("TEMP_TOKEN", tempToken)
            .putBoolean("onboarding_complete", false)
            .apply()
    }

    fun getAccessToken(): String? {
        return sharedPreferences.getString("ACCESS_TOKEN", null)
    }

    fun getRefreshToken(): String? {
        return sharedPreferences.getString("REFRESH_TOKEN", null)
    }

    fun getTempToken(): String? {
        return sharedPreferences.getString("TEMP_TOKEN", null)
    }

    fun clearTempToken() {
        sharedPreferences.edit().remove("TEMP_TOKEN").apply()
    }

    // 1. 설정 완료 상태 저장 (온보딩 마지막 단계에서 호출할 함수)
    fun setOnboardingComplete(isComplete: Boolean) {
        sharedPreferences.edit().putBoolean("onboarding_complete", isComplete).apply()
    }

    // 2. 설정 완료 상태 읽기 (Splash에서 호출하는 함수)
    fun isOnboardingComplete(): Boolean {
        return sharedPreferences.getBoolean("onboarding_complete", false)
    }
}

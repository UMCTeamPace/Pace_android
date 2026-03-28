package com.example.pace.data.datasource

import android.content.SharedPreferences
import com.example.pace.BuildConfig

class AuthDataStore(private val sharedPreferences: SharedPreferences) {

    fun deleteToken() {
        sharedPreferences.edit().remove("ACCESS_TOKEN").apply()
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

    fun getAccessToken(): String? {
        return BuildConfig.BEARER_TOKEN.takeIf { it.isNotBlank() }
            ?: sharedPreferences.getString("ACCESS_TOKEN", null)
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
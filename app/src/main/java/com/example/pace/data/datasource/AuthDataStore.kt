package com.example.pace.data.datasource

import android.content.SharedPreferences

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

    fun getAccessToken(): String? {
        // 실제 저장된 값이 없으니, 스웨거에서 성공했을 때 나온 Bearer ... 토큰을 직접 적어봅니다.
        val tempToken = "Bearer eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiIxIiwiaWF0IjoxNzY5NjE1Nzc4LCJleHAiOjE3NzA4MjUzNzgsInJvbGUiOiJST0xFX1VTRVIiLCJjYXRlZ29yeSI6ImFjY2VzcyJ9.b76UQaY4woR-H9mTU-1AopLCQckxeeamPpyNJRFYXFOxb5pZGcQGpKx3-RYO8rxnnnhFbc6UkQ0tlPX49O3oFg"
        return tempToken
    }
}
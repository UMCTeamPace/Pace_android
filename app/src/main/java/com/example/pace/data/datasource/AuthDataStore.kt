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
        BuildConfig.BEARER_TOKEN
        val tempToken = BuildConfig.BEARER_TOKEN
        return tempToken
    }
}
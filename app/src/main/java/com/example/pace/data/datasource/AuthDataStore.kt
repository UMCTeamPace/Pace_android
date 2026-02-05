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

    fun getAccessToken(): String? = sharedPreferences.getString("ACCESS_TOKEN", null)
}
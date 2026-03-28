package com.example.pace.data.repository

import android.util.Log
import com.example.pace.data.api.WeatherService
import com.example.pace.data.model.response.WeatherResponse
import javax.inject.Inject

class WeatherRepository @Inject constructor(
    private val weatherService: WeatherService
) {
    fun fetchWeather(cityName: String, apiKey: String) {
        weatherService.getWeather(cityName, apiKey).enqueue(object : retrofit2.Callback<WeatherResponse> {
            override fun onResponse(call: retrofit2.Call<WeatherResponse>, response: retrofit2.Response<WeatherResponse>) {

                Log.d("PACE_DEBUG", "Status Code: ${response.code()}")
                Log.d("PACE_DEBUG", "Requested URL: ${call.request().url}")

                if (response.isSuccessful) {
                    val weatherData = response.body()
                    Log.d("PACE_DEBUG", "날씨 데이터 수신 성공: $weatherData")
                } else {

                    Log.e("PACE_DEBUG", "에러 발생: ${response.errorBody()?.string()}")
                }
            }

            override fun onFailure(call: retrofit2.Call<WeatherResponse>, t: Throwable) {

                Log.e("PACE_DEBUG", "통신 실패 원인: ${t.message}")
            }
        })
    }
}
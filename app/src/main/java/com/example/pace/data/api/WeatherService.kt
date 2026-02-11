package com.example.pace.data.api

import com.example.pace.data.model.response.WeatherResponse
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherService {
    @GET("data/2.5/weather")
    fun getWeather(
        @Query("q") cityName: String,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric", // 섭씨 온도를 위해 metric 사용
        @Query("lang") lang: String = "kr"
    ): Call<WeatherResponse>
}
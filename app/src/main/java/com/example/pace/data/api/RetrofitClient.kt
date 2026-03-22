package com.example.pace.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    // 본인의 서버 베이스 URL을 입력하세요.
    private const val BASE_URL = "https://pace-server.kro.kr/"
    private const val WEATHER_BASE_URL = "https://api.openweathermap.org/"

    //공통 okHttpClient
    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY // 통신 내용 전발을 로그로 찍음
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
    }

    //자체 서버용 retrofit
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create()) // JSON 파싱용
            .build()
    }

    // 3. OpenWeather용 Retrofit
    private val weatherRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(WEATHER_BASE_URL)
            .client(okHttpClient) // 로깅 인터셉터 공유
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // 자체 서버 서비스
    val instance: ScheduleService by lazy {
        retrofit.create(ScheduleService::class.java)
    }

    // 날씨 서버 서비스
    val weatherService: WeatherService by lazy {
        weatherRetrofit.create(WeatherService::class.java)
    }
}
package com.example.pace.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    // 본인의 서버 베이스 URL을 입력하세요.
    private const val BASE_URL = "http://ec2-3-35-233-51.ap-northeast-2.compute.amazonaws.com:8080/"
    private const val WEATHER_BASE_URL = "https://api.openweathermap.org/"
    private const val BUS_BASE_URL = "http://ws.bus.go.kr/api/rest/arrive"

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

    // 실시간 버스용 Retrofit
    private val busRetrofit: Retrofit by lazy{
        Retrofit.Builder()
            .baseUrl(BUS_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())     // returnType 변경 가능한 지 보고 추후 수정
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

    // 실시간 버스 서비스
    val busService: BusService by lazy{
        busRetrofit.create(BusService::class.java)
    }
}
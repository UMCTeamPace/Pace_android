package com.example.pace.data.api

import com.example.pace.BuildConfig
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.tickaroo.tikxml.retrofit.TikXmlConverterFactory;

object RetrofitClient {
    // 본인의 서버 베이스 URL을 입력하세요.
    private const val BASE_URL = "https://pace-server.kro.kr/"
    private const val WEATHER_BASE_URL = "https://api.openweathermap.org/"
    private const val BUS_BASE_URL = "http://ws.bus.go.kr/"
    private const val SUBWAY_BASE_URL = "https://apis.data.go.kr/1613000/SubwayInfo/"

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
            .addConverterFactory(TikXmlConverterFactory.create())
            .build()
    }

    // 지하철 시간표용 Retrofit
    private val subwayRetrofit: Retrofit by lazy{
        val gson = GsonBuilder().setLenient().create()
        Retrofit.Builder()
            .baseUrl(SUBWAY_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
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

    // 버스 서비스
    val busService: BusService by lazy{
        busRetrofit.create(BusService::class.java)
    }
    val busParameterService: BusService by lazy{
        retrofit.create(BusService::class.java)
    }

    // 지하철 서비스
    val subwayService: SubwayService by lazy{
        retrofit.create(SubwayService::class.java)
    }
    val subwayTimetableService: SubwayService by lazy{
        subwayRetrofit.create(SubwayService::class.java)
    }
}
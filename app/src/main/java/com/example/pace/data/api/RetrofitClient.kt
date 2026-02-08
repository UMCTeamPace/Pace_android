package com.example.pace.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    // 본인의 서버 베이스 URL을 입력하세요.
    private const val BASE_URL = "http://ec2-3-35-233-51.ap-northeast-2.compute.amazonaws.com:8080/"

    private val okHttpClient: OkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY // 통신 내용 전발을 로그로 찍음
        }
        OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
    }

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create()) // JSON 파싱용
            .build()
    }

    // PaceApplication에서 사용할 서비스 인스턴스
    val instance: ScheduleService by lazy {
        retrofit.create(ScheduleService::class.java)
    }
}
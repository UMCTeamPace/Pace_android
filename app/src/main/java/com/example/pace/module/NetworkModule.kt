package com.example.pace.module

import android.content.SharedPreferences
import com.example.pace.data.datasource.AuthDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlin.jvm.java
import android.content.Context
import com.example.pace.data.api.RouteService
import com.example.pace.data.api.ScheduleService

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BaseRetrofit

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()
    }


    @Provides
    @Singleton
    @BaseRetrofit
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://pace-server.kro.kr/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences {
        // "pace_prefs"라는 이름의 저장소를 생성합니다.
        return context.getSharedPreferences("pace_prefs", Context.MODE_PRIVATE)
    }

    @Provides
    @Singleton
    fun provideAuthDataStore(sharedPreferences: SharedPreferences): AuthDataStore {
        // Hilt가 위에서 생성한 sharedPreferences를 자동으로 주입해줍니다.
        return AuthDataStore(sharedPreferences)
    }
}
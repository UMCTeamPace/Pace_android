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
import com.example.pace.data.api.WeatherService

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BaseRetrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WeatherRetrofit

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(authDataStore: AuthDataStore): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                val token = authDataStore.getAccessToken()
                val originalRequest = chain.request()
                val request = chain.request().newBuilder().apply {
                    if (originalRequest.header("Authorization") == null && token != null) {
                        header("Authorization", token)
                    }
                }.build()
                chain.proceed(request)
            }
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
    @WeatherRetrofit
    fun provideWeatherRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.openweathermap.org/data/2.5/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideWeatherService(@WeatherRetrofit retrofit: Retrofit): WeatherService {
        return retrofit.create(WeatherService::class.java)
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
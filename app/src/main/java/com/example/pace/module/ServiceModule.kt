package com.example.pace.module

import com.example.pace.data.api.AuthControllerService
import com.example.pace.data.api.BusService
import com.example.pace.data.api.MemberControllerService
import com.example.pace.data.api.OnboardingService
import com.example.pace.data.api.PlaceGroupService
import com.example.pace.data.api.SavedPlaceService
import com.example.pace.data.api.ScheduleService
import com.example.pace.data.api.SettingsService
import com.example.pace.data.api.SubwayService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {

    @Provides
    @Singleton
    fun provideSettingsService(
        @BaseRetrofit retrofit: Retrofit
    ): SettingsService {
        return retrofit.create(SettingsService::class.java)
    }

    @Provides
    @Singleton
    fun provideOnboardingService(
        @BaseRetrofit retrofit: Retrofit
    ): OnboardingService {
        return retrofit.create(OnboardingService::class.java)
    }

    @Provides
    @Singleton
    fun providePlaceGroupService(
        @BaseRetrofit retrofit: Retrofit
    ): PlaceGroupService {
        return retrofit.create(PlaceGroupService::class.java)
    }

    @Provides
    @Singleton
    fun provideSavedGroupService(
        @BaseRetrofit retrofit: Retrofit
    ): SavedPlaceService {
        return retrofit.create(SavedPlaceService::class.java)
    }

    @Provides
    @Singleton
    fun provideScheduleService(
        @BaseRetrofit retrofit: Retrofit
    ): ScheduleService {
        return retrofit.create(ScheduleService::class.java)
    }

    @Provides
    @Singleton
    fun provideRouteService(
        @BaseRetrofit retrofit: Retrofit
    ): com.example.pace.data.api.RouteService {
        return retrofit.create(com.example.pace.data.api.RouteService::class.java)
    }

    @Provides
    @Singleton
    fun provideMemberControllerService(
        @BaseRetrofit retrofit: Retrofit
    ): MemberControllerService {
        return retrofit.create(MemberControllerService::class.java)
    }

    @Provides
    @Singleton
    fun provideAuthControllerService(
        @BaseRetrofit retrofit: Retrofit
    ): AuthControllerService {
        return retrofit.create(AuthControllerService::class.java)
    }

    @Provides
    @Singleton
    fun provideSubwayService(@BaseRetrofit retrofit: Retrofit): SubwayService{
        return retrofit.create(SubwayService::class.java)
    }

    @Provides
    @Singleton
    fun provideBusService(@BaseRetrofit retrofit: Retrofit): BusService{
        return retrofit.create(BusService::class.java)
    }
}
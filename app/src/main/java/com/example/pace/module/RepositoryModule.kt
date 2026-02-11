package com.example.pace.module

import android.content.Context
import com.example.pace.data.api.*
import com.example.pace.data.datasource.*
import com.example.pace.data.db.ScheduleDao
import com.example.pace.data.db.UserSettingsDao
import com.example.pace.data.repository.repository.*
import com.example.pace.data.repository.repositoryImpl.*
import com.example.pace.data.repository.repositoryImpl2.MemberControllerRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class) // 1. 앱 전체 범위로 변경
object RepositoryModule {

    @Singleton
    @Provides
    fun providesSettingsRepository(
        settingsService: SettingsService,
        userSettingsDao: UserSettingsDao // 👈 DAO 주입 추가
    ) : SettingsRepository {
        // 구현체 생성자에 DAO와 Service를 순서대로 전달
        return SettingsRepositoryImpl(
            dao = userSettingsDao,
            api = settingsService
        )
    }

    @Singleton
    @Provides
    fun providesOnboardingRepository(
        onboardingService: OnboardingService,
        userSettingsDao: UserSettingsDao
    ) : OnboardingRepository {
        return OnboardingRepositoryImpl(
            api = onboardingService,
            dao = userSettingsDao
        )
    }

    @Singleton
    @Provides
    fun providesPlaceGroupRepository(
        placeGroupService: PlaceGroupService
    ) : PlaceGroupRepository {
        return PlaceGroupRepositoryImpl(placeGroupService)
    }

    @Singleton
    @Provides
    fun providesSavedPlaceRepository(
        savedPlaceService: SavedPlaceService
    ) : SavedPlaceRepository {
        return SavedPlaceRepositoryImpl(savedPlaceService)
    }

    @Singleton
    @Provides
    fun providesScheduleRepository(
        scheduleService: ScheduleService,
        scheduleDao: ScheduleDao,
        authDataStore: AuthDataStore,
        routeRemoteDataSource: RouteScheduleRemoteDataSource,
        normalDataSource: NormalScheduleRemoteDataSource,
        @ApplicationContext context: Context
    ): ScheduleRepository {
        return ScheduleRepositoryImpl(
            api = scheduleService,
            scheduleDao = scheduleDao,
            routeRemoteDataSource = routeRemoteDataSource,
            normalDataSource = normalDataSource,
            authDataStore = authDataStore,
            context = context
        )
    }

    @Singleton
    @Provides
    fun providesMemberControllerRepository(
        memberControllerService: MemberControllerService,
        authDataStore: AuthDataStore,
    ) : MemberControllerRepository {
        return MemberControllerRepositoryImpl(memberControllerService, authDataStore = authDataStore)
    }

    @Singleton
    @Provides
    fun providesAuthControllerRepository(
        authControllerService: AuthControllerService,
        authDataStore: AuthDataStore, // 여기서 이미 객체를 주입받고 있습니다.
    ) : AuthControllerRepository {
        // .toString()을 지우고 authDataStore 객체를 그대로 전달하세요.
        return AuthControllerRepositoryImpl(
            authControllerService,
            authDataStore = authDataStore
        )
    }

    @Singleton
    @Provides
    fun providesRouteRepository(
        routeRepositoryImpl: RouteRepositoryImpl
    ): RouteRepository {
        return routeRepositoryImpl
    }
}
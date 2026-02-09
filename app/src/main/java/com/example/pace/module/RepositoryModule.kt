package com.example.pace.module

import android.content.Context
import com.example.pace.data.api.AuthControllerService
import com.example.pace.data.api.MemberControllerService
import com.example.pace.data.api.OnboardingService
import com.example.pace.data.api.PlaceGroupService
import com.example.pace.data.api.SavedPlaceService
import com.example.pace.data.api.ScheduleService
import com.example.pace.data.api.SettingsService
import com.example.pace.data.datasource.AuthDataStore
import com.example.pace.data.datasource.NormalScheduleRemoteDataSource
import com.example.pace.data.datasource.RouteScheduleRemoteDataSource
import com.example.pace.data.db.ScheduleDao
import com.example.pace.data.repository.repository.AuthControllerRepository
import com.example.pace.data.repository.repository.MemberControllerRepository
import com.example.pace.data.repository.repository.OnboardingRepository
import com.example.pace.data.repository.repository.PlaceGroupRepository
import com.example.pace.data.repository.repository.SavedPlaceRepository
import com.example.pace.data.repository.repository.ScheduleRepository
import com.example.pace.data.repository.repository.SettingsRepository
import com.example.pace.data.repository.repositoryImpl2.MemberControllerRepositoryImpl
import com.example.pace.data.repository.repositoryImpl.OnboardingRepositoryImpl
import com.example.pace.data.repository.repositoryImpl.PlaceGroupRepositoryImpl
import com.example.pace.data.repository.repositoryImpl.SavedPlaceRepositoryImpl
import com.example.pace.data.repository.repositoryImpl.ScheduleRepositoryImpl
import com.example.pace.data.repository.repositoryImpl.SettingsRepositoryImpl
import com.example.pace.data.repository.repositoryImpl.AuthControllerRepositoryImpl
import com.example.pace.module.ServiceModule.provideSettingsService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped

@Module
@InstallIn(ViewModelComponent::class)
object RepositoryModule {

    @ViewModelScoped
    @Provides
    fun providesSettingsRepository(
        settingsService: SettingsService
    ) : SettingsRepository {
        return SettingsRepositoryImpl(settingsService)}

    @ViewModelScoped
    @Provides
    fun providesOnboardingRepository(
        OnboardingService: OnboardingService
    ) : OnboardingRepository {
        return OnboardingRepositoryImpl(OnboardingService)
    }

    @ViewModelScoped
    @Provides
    fun providesPlaceGroupRepository(
        PlaceGroupService: PlaceGroupService
    ) : PlaceGroupRepository {
        return PlaceGroupRepositoryImpl(PlaceGroupService)
    }

    @ViewModelScoped
    @Provides
    fun providesSavedPlaceRepository(
        SavedPlaceService: SavedPlaceService
    ) : SavedPlaceRepository {
        return SavedPlaceRepositoryImpl(SavedPlaceService)
    }

    @ViewModelScoped
    @Provides
    fun providesScheduleRepository(
        scheduleService: ScheduleService,
        scheduleDao: ScheduleDao,
        authDataStore: AuthDataStore,
        routeRemoteDataSource: RouteScheduleRemoteDataSource, // 1. 추가: 서버용 데이터소스
        normalDataSource: NormalScheduleRemoteDataSource,    // 2. 이름 수정: Impl 생성자와 일치시킴
        @ApplicationContext context: Context
    ): ScheduleRepository {
        return ScheduleRepositoryImpl(
            api = scheduleService,
            scheduleDao = scheduleDao,
            routeRemoteDataSource = routeRemoteDataSource, // 3. 인자 추가
            normalDataSource = normalDataSource,           // 4. 이름 수정
            authDataStore = authDataStore,
            context = context
        )
    }

    @ViewModelScoped
    @Provides
    fun providesMemberControllerRepository(
        MemberControllerService: MemberControllerService,
        authDataStore: AuthDataStore,
    ) : MemberControllerRepository {
        return MemberControllerRepositoryImpl(MemberControllerService,authDataStore=authDataStore)
    }

    @ViewModelScoped
    @Provides
    fun providesAuthControllerRepository(
        AuthControllerService: AuthControllerService,
        authDataStore: AuthDataStore,
    ) : AuthControllerRepository {
        return AuthControllerRepositoryImpl(AuthControllerService,authDataStore=authDataStore)
    }


}

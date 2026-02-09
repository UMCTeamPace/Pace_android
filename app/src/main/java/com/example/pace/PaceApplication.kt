package com.example.pace  // 최상위 패키지

import android.app.Application
import android.util.Log
import com.example.pace.data.api.RetrofitClient
import com.example.pace.data.datasource.AuthDataStore
import com.example.pace.data.db.ScheduleDatabase
import com.example.pace.data.datasource.NormalScheduleRemoteDataSource
import com.example.pace.data.db.SearchDatabase

import com.kakao.sdk.common.KakaoSdk
import com.example.pace.data.repository.SearchRepository
import com.example.pace.data.repository.repository.ScheduleRepository
import com.example.pace.data.repository.repositoryImpl.ScheduleRepositoryImpl

class PaceApplication : Application() {
    val authDataStore by lazy {
        AuthDataStore(getSharedPreferences("pace_prefs", MODE_PRIVATE))
    }

    lateinit var repository: ScheduleRepository

    val searchDatabase by lazy { SearchDatabase.getDatabase(this) }
    val searchRepository by lazy {
        SearchRepository(searchDatabase.searchDao(),
            searchDatabase.recentRouteDao(),
            searchDatabase.myPlaceDao()
        )
    }

    override fun onCreate() {
        super.onCreate()

        try {
            // Retrofit 서비스 생성 (본인의 구조에 맞게)
            val scheduleService = RetrofitClient.instance

            // 2. 통합된 Impl 생성자 호출
            repository = ScheduleRepositoryImpl(
                api = scheduleService,
                scheduleDao = ScheduleDatabase.getDatabase(this).scheduleDao(),
                authDataStore = authDataStore,
                normalScheduleDataSource = NormalScheduleRemoteDataSource(this),
                context = this // Impl 생성자에서 @ApplicationContext Context를 받는 부분
            )

            Log.d("PaceApplication", "통합 Repository 초기화 완료")
        } catch (e: Exception) {
            Log.e("PaceApplication", "Repository 초기화 실패", e)
        }

        KakaoSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)
    }


}

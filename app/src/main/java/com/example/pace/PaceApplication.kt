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
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PaceApplication : Application() {
    val authDataStore by lazy {
        AuthDataStore(getSharedPreferences("pace_prefs", MODE_PRIVATE))
    }

    val searchDatabase by lazy { SearchDatabase.getDatabase(this) }
    val searchRepository by lazy {
        SearchRepository(searchDatabase.searchDao(),
            searchDatabase.recentRouteDao(),
            searchDatabase.myPlaceDao()
        )
    }

    override fun onCreate() {
        super.onCreate()

        // Kakao SDK 초기화만 남겨둡니다.
        KakaoSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)

        Log.d("PaceApplication", "Hilt 기반 어플리케이션 시작")
    }


}

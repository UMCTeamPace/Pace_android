package com.example.pace  // 최상위 패키지

import android.app.Application
import android.os.StrictMode
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
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
import javax.inject.Inject
import androidx.work.Configuration


@HiltAndroidApp
class PaceApplication : Application(), Configuration.Provider { // 1. 인터페이스 추가

    @Inject
    lateinit var workerFactory: HiltWorkerFactory // 2. HiltWorkerFactory 주입

    // 3. WorkManager 설정 커스텀
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

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
        // 모든 감지를 무시하고 페널티를 없애는 설정
        // ThreadPolicy (메인 스레드 작업 감지 해제)
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .permitAll() // 모든 제약을 허용 (빨간 테두리 발생 원인 차단)
                .build()
        )

        // VmPolicy (메모리 누수 등 VM 관련 감지 해제)
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .build()
        )
        // Kakao SDK 초기화만 남겨둡니다.
        KakaoSdk.init(this, BuildConfig.KAKAO_NATIVE_APP_KEY)

        Log.d("PaceApplication", "Hilt 기반 어플리케이션 시작")
    }


}

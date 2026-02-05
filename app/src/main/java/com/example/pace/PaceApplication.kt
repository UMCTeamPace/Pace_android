package com.example.pace  // 최상위 패키지

import android.app.Application
import android.util.Log
import com.example.pace.data.db.ScheduleDatabase
import com.example.pace.data.datasource.NormalScheduleRemoteDataSource
import com.example.pace.data.db.SearchDatabase
import com.example.pace.data.repository.ScheduleRepository
import com.example.pace.data.repository.SearchRepository

class PaceApplication : Application() {
    lateinit var repository: ScheduleRepository

    val searchDatabase by lazy { SearchDatabase.getDatabase(this) }
    val searchRepository by lazy { SearchRepository(searchDatabase.searchDao()) }

    override fun onCreate() {
        super.onCreate()
        try {
            repository = ScheduleRepository(
                ScheduleDatabase.getDatabase(this).scheduleDao(),  // ← DB 초기화 오류 확인
                NormalScheduleRemoteDataSource(this),
                this
            )
            Log.d("PaceApplication", "Repository 초기화 완료")  // 로그 추가
        } catch (e: Exception) {
            Log.e("PaceApplication", "Repository 초기화 실패", e)
            throw e  // 크래시로 디버깅
        }
    }
}

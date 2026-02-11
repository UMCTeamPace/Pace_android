package com.example.pace.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.pace.data.converter.Converters
import com.example.pace.data.model.MyPlace
import com.example.pace.data.model.RecentPlace
import com.example.pace.data.model.RecentRoute
import com.example.pace.data.model.RecentSearch
import com.example.pace.data.model.UserSettingsEntity

@Database(
    entities = [
        RecentSearch::class,
        RecentPlace::class,
        RecentRoute::class,
        MyPlace::class,
        UserSettingsEntity::class
    ],
    version = 11, // 👈 [수정] 기존 10에서 11로 버전을 올리세요.
    exportSchema = false
)

@TypeConverters(Converters::class)
abstract class SearchDatabase : RoomDatabase() {

    abstract fun searchDao(): SearchDao
    abstract fun recentRouteDao(): RecentRouteDao
    abstract fun myPlaceDao(): MyPlaceDao

    // 💡 [추가] Hilt가 이 추상 함수를 보고 UserSettingsDao 구현체를 찾아냅니다.
    abstract fun userSettingsDao(): UserSettingsDao

    companion object {
        @Volatile
        private var INSTANCE: SearchDatabase? = null

        fun getDatabase(context: Context): SearchDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SearchDatabase::class.java,
                    "pace_database" // DB 파일 이름 확인
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
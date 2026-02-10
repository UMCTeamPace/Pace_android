package com.example.pace.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.pace.data.model.MyPlace
import com.example.pace.data.model.RecentPlace
import com.example.pace.data.model.RecentRoute
import com.example.pace.data.model.RecentSearch
import com.example.pace.data.model.UserSettingsEntity // 1. 엔티티 임포트 확인

@Database(
    entities = [
        RecentSearch::class,
        RecentPlace::class,
        RecentRoute::class,
        MyPlace::class,
        UserSettingsEntity::class // 3. 여기에 UserSettingsEntity 추가!
    ],
    version = 5, // 4. 엔티티가 추가되었으므로 버전을 올려야 합니다 (4 -> 5)
    exportSchema = false
)
@TypeConverters(Converters::class) // 5. List<Int> 등을 저장하려면 컨버터가 필요합니다
abstract class SearchDatabase : RoomDatabase() {

    abstract fun searchDao(): SearchDao
    abstract fun recentRouteDao(): RecentRouteDao
    abstract fun myPlaceDao(): MyPlaceDao

    // 6. 이 함수를 추가해야 Hilt에서 UserSettingsDao를 주입할 수 있습니다!
    abstract fun userSettingsDao(): UserSettingsDao

    companion object {
        @Volatile
        private var INSTANCE: SearchDatabase? = null

        fun getDatabase(context: Context): SearchDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SearchDatabase::class.java,
                    "pace_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
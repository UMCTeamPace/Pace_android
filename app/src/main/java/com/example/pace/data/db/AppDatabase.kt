package com.example.pace.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.pace.data.converter.Converters
import com.example.pace.data.model.Schedule
import com.example.pace.data.model.UserSettingsEntity

@Database(
    entities = [
        UserSettingsEntity::class,
        Schedule::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userSettingsDao(): UserSettingsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "app_database" // 파일 이름도 구분되게 설정
                )
                    .fallbackToDestructiveMigration() // 버전 충돌 시 초기화 (개발 단계에서 유용)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
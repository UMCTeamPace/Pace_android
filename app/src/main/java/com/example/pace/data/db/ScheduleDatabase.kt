package com.example.pace.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.pace.data.converter.Converters
import com.example.pace.data.model.Schedule
import com.example.pace.data.model.UserSettingsEntity // 👈 1. Import 추가

@Database(
    entities = [Schedule::class, UserSettingsEntity::class],
    version = 17,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class ScheduleDatabase : RoomDatabase() {

    abstract fun scheduleDao(): ScheduleDao

    // 👈 4. UserSettingsDao 접근을 위한 추상 함수 추가
    abstract fun userSettingsDao(): UserSettingsDao

    companion object {
        @Volatile
        private var INSTANCE: ScheduleDatabase? = null

        fun getDatabase(context: Context): ScheduleDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ScheduleDatabase::class.java,
                    "schedule_database"
                )
                    .fallbackToDestructiveMigration() // 스키마 변경 시 기존 데이터 삭제 후 재생성
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

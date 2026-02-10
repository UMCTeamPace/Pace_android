package com.example.pace.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.pace.data.model.UserSettingsEntity

@Database(entities = [UserSettingsEntity::class], version = 1)
@TypeConverters(Converters::class) // 아까 만든 컨버터 등록
abstract class AppDatabase : RoomDatabase() {
    abstract fun userSettingsDao(): UserSettingsDao

    // 싱글톤 패턴으로 구현하는 것이 일반적입니다. (Hilt 미사용 시)
}
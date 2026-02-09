package com.example.pace.module

import android.content.Context
import androidx.room.Room
import com.example.pace.data.db.ScheduleDatabase
import com.example.pace.data.db.ScheduleDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideScheduleDatabase(
        @ApplicationContext context: Context
    ): ScheduleDatabase {
        return Room.databaseBuilder(
            context,
            ScheduleDatabase::class.java,
            "pace_database" // 실제 DB 파일 이름
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideScheduleDao(database: ScheduleDatabase): ScheduleDao {
        return database.scheduleDao()
    }
}
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
import com.example.pace.data.db.SearchDatabase // 실제 Database 클래스명 확인
import com.example.pace.data.db.UserSettingsDao


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

    @Provides
    @Singleton
    fun provideUserSettingsDao(database: SearchDatabase): UserSettingsDao {
        return database.userSettingsDao()
    }

    @Provides
    @Singleton
    fun provideSearchDatabase(
        @ApplicationContext context: Context
    ): SearchDatabase {
        // 이미 SearchDatabase.companion에 만들어둔 getDatabase를 활용합니다.
        return SearchDatabase.getDatabase(context)
    }

}
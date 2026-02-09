package com.example.pace.module

import android.content.Context
import com.example.pace.data.datasource.AuthDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    fun provideAuthDataStore(@ApplicationContext context: Context): AuthDataStore {
        // PaceApplication에 있던 생성 로직을 여기로 가져오는 겁니다.
        val sharedPrefs = context.getSharedPreferences("pace_prefs", Context.MODE_PRIVATE)
        return AuthDataStore(sharedPrefs)
    }
}
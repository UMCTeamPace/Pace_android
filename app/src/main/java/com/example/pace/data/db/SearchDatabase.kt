package com.example.pace.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.pace.data.model.MyPlace
import com.example.pace.data.model.RecentPlace
import com.example.pace.data.model.RecentRoute
import com.example.pace.data.model.RecentSearch

@Database(entities = [RecentSearch::class, RecentPlace::class, RecentRoute::class, MyPlace::class], version = 5, exportSchema = false)
abstract class SearchDatabase : RoomDatabase() {
    abstract fun searchDao(): SearchDao
    abstract fun recentRouteDao(): RecentRouteDao
    abstract fun myPlaceDao(): MyPlaceDao
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
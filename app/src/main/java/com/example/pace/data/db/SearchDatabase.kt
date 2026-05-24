package com.example.pace.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 15,
    exportSchema = false
)

@TypeConverters(Converters::class)
abstract class SearchDatabase : RoomDatabase() {

    abstract fun searchDao(): SearchDao
    abstract fun recentRouteDao(): RecentRouteDao
    abstract fun myPlaceDao(): MyPlaceDao

    abstract fun userSettingsDao(): UserSettingsDao

    companion object {
        @Volatile
        private var INSTANCE: SearchDatabase? = null

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `recent_places`")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `recent_places` (
                        `placeId` TEXT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        PRIMARY KEY(`placeId`)
                    )
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE IF EXISTS `recent_routes`")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `recent_routes` (
                        `startPlaceId` TEXT NOT NULL,
                        `endPlaceId` TEXT NOT NULL,
                        `saveTime` INTEGER NOT NULL,
                        PRIMARY KEY(`startPlaceId`, `endPlaceId`)
                    )
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `recent_places` ADD COLUMN `placeName` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `recent_routes` ADD COLUMN `startPlaceName` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `recent_routes` ADD COLUMN `endPlaceName` TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) = Unit
        }

        fun getDatabase(context: Context): SearchDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SearchDatabase::class.java,
                    "pace_database" // DB 파일 이름 확인
                )
                    .addMigrations(MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

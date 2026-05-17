package com.example.pace.data.model

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey

@Entity(
    tableName = "recent_routes",
    primaryKeys = ["startPlaceId", "endPlaceId"]
)
data class RecentRoute(
    val startPlaceId: String,
    @ColumnInfo(defaultValue = "")
    val startPlaceName: String,
    val endPlaceId: String,
    @ColumnInfo(defaultValue = "")
    val endPlaceName: String,
    val saveTime: Long = System.currentTimeMillis()
)

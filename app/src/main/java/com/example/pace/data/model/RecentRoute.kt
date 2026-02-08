package com.example.pace.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(
    tableName = "recent_routes",
    primaryKeys = ["startPlaceId", "endPlaceId"]
)
data class RecentRoute(
    val startPlaceName: String,
    val startPlaceId: String,
    val endPlaceName: String,
    val endPlaceId: String,
    val saveTime: Long = System.currentTimeMillis()
)

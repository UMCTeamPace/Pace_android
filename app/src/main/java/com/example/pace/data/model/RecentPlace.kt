package com.example.pace.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recent_places")
data class RecentPlace(
    @PrimaryKey val name: String,
    val placeId: String,
    val address: String,
    val category: String,
    val openStatus: String,
    val lat: Double,
    val lng: Double,
    val timestamp: Long = System.currentTimeMillis()
)

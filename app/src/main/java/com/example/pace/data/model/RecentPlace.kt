package com.example.pace.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recent_places")
data class RecentPlace(
    @PrimaryKey val placeId: String,
    val timestamp: Long = System.currentTimeMillis()
)

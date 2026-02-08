package com.example.pace.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "my_places")
data class MyPlace(
    @PrimaryKey val type: String, // "HOME" 또는 "WORK"
    val name: String,
    val placeId: String,
)

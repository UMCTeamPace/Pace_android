package com.example.pace.data.db

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromString(value: String): List<Int> {
        return value.split(",").filter { it.isNotEmpty() }.map { it.toInt() }
    }

    @TypeConverter
    fun fromList(list: List<Int>): String {
        return list.joinToString(",")
    }
}

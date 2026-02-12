package com.example.pace.data.converter

import androidx.room.TypeConverter
import com.google.gson.Gson

class Converters {
    // 기존 Int 리스트 변환 (Gson 방식 유지)
    @TypeConverter
    fun listToJson(value: List<Int>?): String = Gson().toJson(value ?: emptyList<Int>())

    @TypeConverter
    fun jsonToList(value: String): List<Int> =
        Gson().fromJson(value, Array<Int>::class.java).toList()

    // 기존 Long 리스트 변환 (콤마 구분 방식 유지 + 안전 코드 추가)
    @TypeConverter
    fun fromList(value: List<Long>?): String {
        return value?.joinToString(",") ?: ""
    }

    @TypeConverter
    fun toList(value: String?): List<Long> {
        return if (value.isNullOrEmpty()) emptyList()
        // 💡 toLong() 대신 toLongOrNull()을 써서 데이터가 깨져있어도 앱이 안 튕기게 방어합니다.
        else value.split(",").mapNotNull { it.toLongOrNull() }
    }
}
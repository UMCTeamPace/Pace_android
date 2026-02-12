package com.example.pace.data.model

data class CalendarAccount(
    val id: String,
    val displayName: String, // 캘린더 이름 (예: 일정)
    val accountName: String, // 계정명 (예: example@gmail.com)
    val color: Int,
    var isSelected: Boolean = false
)
package com.example.pace.data.model.response

// 💡 서버가 주는 'result' 내부의 데이터 구조와 일치시킵니다.
data class OnboardingResponse(
    val onboardingCompleted: Boolean,
    val accessToken: String,
    val refreshToken: String,
    val earlyArrivalTime: Int,
    val isReminderActive: Boolean,
    val role: String
)
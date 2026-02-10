package com.example.pace.data.repository.repository

import com.example.pace.data.model.UserSettingsEntity
import com.example.pace.data.model.request.OnboardingRequest
import com.example.pace.data.model.response.OnboardingResponse
import com.example.pace.data.model.response.RawDefaultResponse

interface OnboardingRepository {
    // 1. 로컬 DB에 저장하는 함수 추가
    suspend fun saveSettingsToLocal(settings: UserSettingsEntity)

    // 2. 기존 서버 전송 함수
    suspend fun saveOnboardingSettings(
        accessToken: String,
        request: OnboardingRequest
    ): RawDefaultResponse<OnboardingResponse>
}
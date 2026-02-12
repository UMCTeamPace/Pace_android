package com.example.pace.data.repository.repository

import com.example.pace.data.model.UserSettingsEntity
import com.example.pace.data.model.request.OnboardingRequest
import com.example.pace.data.model.response.OnboardingResponse
import com.example.pace.data.model.response.RawDefaultResponse

interface OnboardingRepository {
    suspend fun saveOnboardingSettings(
        accessToken: String,
        request: OnboardingRequest // 💡 파라미터 삭제
    ): RawDefaultResponse<OnboardingResponse>

    suspend fun saveSettingsToLocal(settings: UserSettingsEntity)
}
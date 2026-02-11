package com.example.pace.data.api

import com.example.pace.data.model.request.OnboardingRequest
import com.example.pace.data.model.response.OnboardingResponse
import com.example.pace.data.model.response.RawDefaultResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface OnboardingService {
    @POST("/api/v1/member/onboarding") // 혹은 정해진 경로
    suspend fun saveOnboardingSettings(
        @Header("Authorization") token: String,
        @Body request: OnboardingRequest   // 나머지는 바디로 전송
    ): RawDefaultResponse<OnboardingResponse>
}
package com.example.pace.data.api

import com.example.pace.data.model.request.OnboardingRequest
import com.example.pace.data.model.response.OnboardingResponse
import com.example.pace.data.model.response.RawDefaultResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

interface OnboardingService {
    @POST("/api/v1/member/onboarding")
    suspend fun saveOnboardingSettings(
        @Header("Authorization") accessToken: String,
        @Body request: OnboardingRequest
    ): RawDefaultResponse<OnboardingResponse>

}
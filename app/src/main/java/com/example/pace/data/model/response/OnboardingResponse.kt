package com.example.pace.data.model.response

import com.google.gson.annotations.SerializedName

data class OnboardingResponse(
    @SerializedName("isSuccess") val isSuccess: Boolean,
    @SerializedName("code") val code: String,
    @SerializedName("message") val message: String,
    @SerializedName("result") val result: OnboardingResult
)

data class OnboardingResult(
    @SerializedName("onboarding_completed") val onboardingCompleted: Boolean,
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
    @SerializedName("early_arrival_time") val earlyArrivalTime: Int,
    @SerializedName("is_reminder_active") val isReminderActive: Boolean,
    @SerializedName("role") val role: String
)
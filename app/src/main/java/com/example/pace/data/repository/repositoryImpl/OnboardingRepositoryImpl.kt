package com.example.pace.data.repository.repositoryImpl

import com.example.pace.data.api.OnboardingService
import com.example.pace.data.db.UserSettingsDao
import com.example.pace.data.model.UserSettingsEntity
import com.example.pace.data.model.request.OnboardingRequest
import com.example.pace.data.model.response.OnboardingResponse
import com.example.pace.data.model.response.RawDefaultResponse
import com.example.pace.data.repository.repository.OnboardingRepository
import com.example.pace.data.util.safeApiCall
import javax.inject.Inject

class OnboardingRepositoryImpl @Inject constructor(
    private val api: OnboardingService,
    private val dao: UserSettingsDao // 로컬 DB 주입
) : OnboardingRepository {

    override suspend fun saveSettingsToLocal(settings: UserSettingsEntity) {
        dao.insertSettings(settings)
    }

    override suspend fun saveOnboardingSettings(
        accessToken: String,
        request: OnboardingRequest
    ): RawDefaultResponse<OnboardingResponse> {
        return safeApiCall { api.saveOnboardingSettings(accessToken, request) }
    }
}
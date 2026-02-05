package com.example.pace.data.repository.repositoryImpl

import javax.inject.Inject
import com.example.pace.data.api.SettingsService
import com.example.pace.data.model.request.UpdateSettingsRequest
import com.example.pace.data.model.response.MemberSettingsResponse
import com.example.pace.data.model.response.RawDefaultResponse
import com.example.pace.data.model.response.UpdateSettingsResponse
import com.example.pace.data.repository.repository.SettingsRepository
import com.example.pace.data.util.safeApiCall

class SettingsRepositoryImpl @Inject constructor(
    private val api: SettingsService
) : SettingsRepository {
    override suspend fun getMemberSettings(
        accessToken: String,
        memberId: Long
    ): RawDefaultResponse<MemberSettingsResponse> {
        return safeApiCall { api.getMemberSettings(accessToken, memberId) }
    }

    override suspend fun updateMemberSettings(
        accessToken: String,
        memberId: Long,
        request: UpdateSettingsRequest
    ): RawDefaultResponse<UpdateSettingsResponse> {
        return safeApiCall { api.updateMemberSettings(accessToken, memberId, request) }
    }
}

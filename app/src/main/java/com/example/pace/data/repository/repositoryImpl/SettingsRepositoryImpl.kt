package com.example.pace.data.repository.repositoryImpl

import javax.inject.Inject
import com.example.pace.data.api.SettingsService
import com.example.pace.data.db.UserSettingsDao
import com.example.pace.data.model.UserSettingsEntity
import com.example.pace.data.model.request.UpdateSettingsRequest
import com.example.pace.data.model.response.MemberSettingsResponse
import com.example.pace.data.model.response.RawDefaultResponse
import com.example.pace.data.model.response.UpdateSettingsResponse
import com.example.pace.data.repository.repository.SettingsRepository
import com.example.pace.data.util.safeApiCall

class SettingsRepositoryImpl @Inject constructor(
    private val dao: UserSettingsDao, // Room DAO 주입
    private val api: SettingsService // Retrofit 서비스 주입 (이름은 프로젝트마다 다를 수 있음)
) : SettingsRepository {
    override suspend fun getMemberSettings(
        accessToken: String,
    ): RawDefaultResponse<MemberSettingsResponse> {
        return safeApiCall { api.getMemberSettings(accessToken) }
    }

    override suspend fun updateMemberSettings(
        accessToken: String,
        request: UpdateSettingsRequest
    ): RawDefaultResponse<UpdateSettingsResponse> {
        return safeApiCall { api.updateMemberSettings(accessToken, request) }
    }

    override suspend fun updateSettings(settings: UserSettingsEntity) {
        dao.insertSettings(settings) // Room에 덮어쓰기
    }

}

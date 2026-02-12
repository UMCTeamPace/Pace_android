package com.example.pace.data.repository.repository

import com.example.pace.data.model.UserSettingsEntity
import com.example.pace.data.model.request.UpdateSettingsRequest
import com.example.pace.data.model.response.MemberSettingsResponse
import com.example.pace.data.model.response.RawDefaultResponse
import com.example.pace.data.model.response.UpdateSettingsResponse
import kotlinx.coroutines.flow.Flow
interface SettingsRepository {
    suspend fun getMemberSettings(
         accessToken: String,
    ):RawDefaultResponse<MemberSettingsResponse>


    suspend fun updateMemberSettings(
        accessToken: String,
        request: UpdateSettingsRequest
    ):RawDefaultResponse<UpdateSettingsResponse>

    suspend fun updateSettings(settings: UserSettingsEntity)
    fun getUserSettings(): kotlinx.coroutines.flow.Flow<UserSettingsEntity?>
}
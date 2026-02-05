package com.example.pace.data.repository.repository

import com.example.pace.data.model.request.UpdateSettingsRequest
import com.example.pace.data.model.response.MemberSettingsResponse
import com.example.pace.data.model.response.RawDefaultResponse
import com.example.pace.data.model.response.UpdateSettingsResponse

interface SettingsRepository {
    suspend fun getMemberSettings(
         accessToken: String,
         memberId : Long
    ):RawDefaultResponse<MemberSettingsResponse>


    suspend fun updateMemberSettings(
        accessToken: String,
        memberId : Long,
        request: UpdateSettingsRequest
    ):RawDefaultResponse<UpdateSettingsResponse>
}
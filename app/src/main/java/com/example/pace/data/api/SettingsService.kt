package com.example.pace.data.api

import com.example.pace.data.model.request.UpdateSettingsRequest
import com.example.pace.data.model.response.MemberSettingsResponse
import com.example.pace.data.model.response.RawDefaultResponse
import com.example.pace.data.model.response.UpdateSettingsResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH  // POST 대신 PATCH를 import 하세요
import retrofit2.http.Query

interface SettingsService {
    @GET("/api/v1/member/settings")
    suspend fun getMemberSettings(
        @Header("Authorization") accessToken: String,
    ): RawDefaultResponse<MemberSettingsResponse>

    // 1. @POST에서 @PATCH로 변경
    @PATCH("/api/v1/member/settings")
    suspend fun updateMemberSettings(
        @Header("Authorization") accessToken: String,
        @Body request: UpdateSettingsRequest
    ): RawDefaultResponse<UpdateSettingsResponse>
}
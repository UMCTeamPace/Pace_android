package com.example.pace.data.api

import com.example.pace.data.model.request.UpdateSettingsRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query

import com.example.pace.data.model.response.MemberSettingsResponse
import com.example.pace.data.model.response.RawDefaultResponse
import com.example.pace.data.model.response.UpdateSettingsResponse
import javax.inject.Inject


interface SettingsService {
    @GET("/api/v1/member/settings")
    suspend fun getMemberSettings(
        @Header("Authorization") accessToken: String,
        @Query("memberId") memberId : Long
    ):RawDefaultResponse<MemberSettingsResponse>


    @POST("/api/v1/member/settings")
    suspend fun updateMemberSettings(
        @Header("Authorization") accessToken: String,
        @Query("memberId") memberId : Long,
        @Body request: UpdateSettingsRequest
    ):RawDefaultResponse<UpdateSettingsResponse>

}



package com.example.pace.data.api

import com.example.pace.data.model.response.RawDefaultResponse
import retrofit2.http.DELETE
import retrofit2.http.Header
import retrofit2.http.POST

interface MemberControllerService {
    @POST("/api/v1/member/logout")
    suspend fun logout(
        @Header("Authorization") accessToken: String
    ): RawDefaultResponse<String>

    @DELETE("/api/v1/member/withdrawal")
    suspend fun withdraw(
        @Header("Authorization") accessToken: String
    ): RawDefaultResponse<String>
}



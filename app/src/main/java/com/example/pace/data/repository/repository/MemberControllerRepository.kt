package com.example.pace.data.repository.repository

import com.example.pace.data.model.response.DefaultResponse
import com.example.pace.data.model.response.RawDefaultResponse
import retrofit2.http.DELETE
import retrofit2.http.Header
import retrofit2.http.POST

interface MemberControllerRepository {
    suspend fun logout(
        accessToken: String
    ): DefaultResponse<String>


    suspend fun withdraw(
       accessToken: String
    ): DefaultResponse<String>
}
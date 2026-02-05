package com.example.pace.data.repository.repository

import com.example.pace.data.model.request.KakaoLoginRequest
import com.example.pace.data.model.request.ReissueRequest
import com.example.pace.data.model.response.AuthTokenResponse
import com.example.pace.data.model.response.DefaultResponse
import com.example.pace.data.model.response.KakaoLoginResponse
import com.example.pace.data.model.response.RawDefaultResponse
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

interface AuthControllerRepository {
    suspend fun kakaoLogin(
        kakaoQueryToken: String,
        request: KakaoLoginRequest
    ): DefaultResponse<KakaoLoginResponse>

    suspend fun reissueToken(
        queryToken: String,
        request: ReissueRequest
    ): DefaultResponse<AuthTokenResponse>
}


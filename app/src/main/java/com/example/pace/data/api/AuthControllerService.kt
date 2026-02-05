package com.example.pace.data.api

import com.example.pace.data.model.request.KakaoLoginRequest
import com.example.pace.data.model.request.ReissueRequest
import com.example.pace.data.model.response.AuthTokenResponse
import com.example.pace.data.model.response.KakaoLoginResponse
import com.example.pace.data.model.response.RawDefaultResponse
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

interface AuthControllerService {
    @POST("/api/v1/auth/kakao")
    suspend fun kakaoLogin(
        @Query("kakaoAccessToken") kakaoQueryToken: String,
        @Body request: KakaoLoginRequest
    ): RawDefaultResponse<KakaoLoginResponse>

    @POST("/api/v1/auth/reissue")
    suspend fun reissueToken(
        @Query("refreshToken") queryToken: String,
        @Body request: ReissueRequest
    ): RawDefaultResponse<AuthTokenResponse>
}
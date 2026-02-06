package com.example.pace.data.repository.repositoryImpl

import com.example.pace.data.api.AuthControllerService
import com.example.pace.data.datasource.AuthDataStore
import com.example.pace.data.model.response.DefaultResponse
import com.example.pace.data.model.request.KakaoLoginRequest
import com.example.pace.data.model.request.ReissueRequest
import com.example.pace.data.model.response.AuthTokenResponse
import com.example.pace.data.model.response.KakaoLoginResponse
import com.example.pace.data.repository.repository.AuthControllerRepository
import javax.inject.Inject

class AuthControllerRepositoryImpl @Inject constructor(
    private val api: AuthControllerService,
    private val authDataStore: AuthDataStore
) : AuthControllerRepository {
    override suspend fun kakaoLogin(
        kakaoQueryToken: String,
        request: KakaoLoginRequest
    ): DefaultResponse<KakaoLoginResponse> {
        val response = api.kakaoLogin(kakaoQueryToken, request)

        return if (response.isSuccess) {
            val result = response.result
            if (result != null) {
                authDataStore.saveTokens(result.accessToken, result.refreshToken)
            }
            DefaultResponse.Success(result)
        } else {
            DefaultResponse.Failure(response.code, response.message)
        }
    }

    override suspend fun reissueToken(
        queryToken: String,
        request: ReissueRequest
    ): DefaultResponse<AuthTokenResponse> {
        val response = api.reissueToken(queryToken, request)

        return if (response.isSuccess) {
            val result = response.result
            if (result != null) {

                authDataStore.saveTokens(result.accessToken, result.refreshToken)
            }
            DefaultResponse.Success(result)
        } else {
            DefaultResponse.Failure(response.code, response.message)
        }
    }

}
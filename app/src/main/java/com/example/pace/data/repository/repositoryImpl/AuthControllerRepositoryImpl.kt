package com.example.pace.data.repository.repositoryImpl

import com.example.pace.data.api.AuthControllerService
import com.example.pace.data.datasource.AuthDataStore
import com.example.pace.data.model.response.DefaultResponse
import com.example.pace.data.model.request.KakaoLoginRequest
import com.example.pace.data.model.request.ReissueRequest
import com.example.pace.data.model.response.AuthTokenResponse
import com.example.pace.data.model.response.KakaoLoginResponse
import com.example.pace.data.repository.repository.AuthControllerRepository
import retrofit2.HttpException
import javax.inject.Inject

class AuthControllerRepositoryImpl @Inject constructor(
    private val api: AuthControllerService,
    private val authDataStore: AuthDataStore
) : AuthControllerRepository {
    override suspend fun kakaoLogin(
        kakaoQueryToken: String,
        request: KakaoLoginRequest
    ): DefaultResponse<KakaoLoginResponse> {
        return try {
            val response = api.kakaoLogin(kakaoQueryToken, request)

            if (response.isSuccess) {
                val result = response.result
                if (result != null) {
                    when {
                        !result.tempToken.isNullOrBlank() -> authDataStore.saveTempToken(result.tempToken)
                        !result.accessToken.isNullOrBlank() && !result.refreshToken.isNullOrBlank() ->
                            authDataStore.saveTokens(result.accessToken, result.refreshToken)
                    }
                }
                DefaultResponse.Success(result)
            } else {
                DefaultResponse.Failure(response.code, response.message)
            }
        } catch (e: HttpException) {
            DefaultResponse.Failure("HTTP_${e.code()}", "서버 로그인에 실패했습니다.")
        } catch (e: Exception) {
            DefaultResponse.Failure("AUTH_LOGIN_ERROR", e.message ?: "서버 로그인 중 오류가 발생했습니다.")
        }
    }

    override suspend fun reissueToken(
        queryToken: String,
        request: ReissueRequest
    ): DefaultResponse<AuthTokenResponse> {
        return try {
            val response = api.reissueToken(queryToken, request)

            if (response.isSuccess) {
                val result = response.result
                if (result != null) {
                    authDataStore.saveTokens(result.accessToken, result.refreshToken)
                }
                DefaultResponse.Success(result)
            } else {
                DefaultResponse.Failure(response.code, response.message)
            }
        } catch (e: HttpException) {
            DefaultResponse.Failure("HTTP_${e.code()}", "토큰 재발급에 실패했습니다.")
        } catch (e: Exception) {
            DefaultResponse.Failure("AUTH_REISSUE_ERROR", e.message ?: "토큰 재발급 중 오류가 발생했습니다.")
        }
    }

}

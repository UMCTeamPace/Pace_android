package com.example.pace.data.repository.repositoryImpl2

import com.example.pace.data.api.MemberControllerService
import com.example.pace.data.datasource.AuthDataStore
import com.example.pace.data.model.response.DefaultResponse
import com.example.pace.data.repository.repository.MemberControllerRepository
import javax.inject.Inject

class MemberControllerRepositoryImpl @Inject constructor(
    private val api : MemberControllerService,
    private val authDataStore: AuthDataStore

) : MemberControllerRepository {
    override suspend fun logout(accessToken: String): DefaultResponse<String> {
        val response = api.logout(accessToken)

        return if (response.isSuccess) {
            authDataStore.clearTokens()
            DefaultResponse.Success(response.result)
        } else {
            DefaultResponse.Failure(response.code, response.message)
        }
    }

    override suspend fun withdraw(accessToken: String): DefaultResponse<String> {
        val response = api.withdraw(accessToken)

        return if (response.isSuccess) {

            authDataStore.clearAllData()
            DefaultResponse.Success(response.result)
        } else {
            DefaultResponse.Failure(response.code, response.message)
        }
    }
}
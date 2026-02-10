package com.example.pace.data.repository.repositoryImpl

import com.example.pace.data.api.RouteService
import com.example.pace.data.model.request.RouteRequest
import com.example.pace.data.model.response.RouteApiResponse
import com.example.pace.data.repository.repository.RouteRepository
import javax.inject.Inject

class RouteRepositorylmpl @Inject constructor(
    private val api: RouteService
) : RouteRepository {

    override suspend fun searchRoutes(
        accessToken: String,
        request: RouteRequest
    ): RouteApiResponse {
        val response = api.getRoutes(accessToken, request)

        if (response.isSuccessful && response.body() != null) {
            return response.body()!!
        } else {
            throw Exception("경로 탐색 실패: ${response.code()} ${response.message()}")
        }
    }
}
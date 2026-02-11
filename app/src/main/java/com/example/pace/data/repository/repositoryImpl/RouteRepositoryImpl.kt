package com.example.pace.data.repository.repositoryImpl

import com.example.pace.data.api.RouteService
import com.example.pace.data.model.request.RouteSearchRequest
import com.example.pace.data.model.response.RouteApiResponse
import com.example.pace.data.repository.repository.RouteRepository
import javax.inject.Inject

class RouteRepositoryImpl @Inject constructor(
    private val api: RouteService
) : RouteRepository {

    override suspend fun searchRoutes(
        accessToken: String,
        request: RouteSearchRequest
    ): RouteApiResponse {
        val response = api.getRoutes(
            accessToken = accessToken,
            originLat = request.originLat,
            originLng = request.originLng,
            destLat = request.destLat,
            destLng = request.destLng,
            arrivalTime = request.arrivalTime,
            departureTime = request.departureTime,
            transitType = request.transitType,
            searchWay = request.searchWay
        )

        if (response.isSuccessful && response.body() != null) {
            return response.body()!!
        } else {
            // 에러 로그 확인을 위해 상세 메시지 포함
            throw Exception("경로 탐색 실패: ${response.code()} ${response.errorBody()?.string()}")
        }
    }
}
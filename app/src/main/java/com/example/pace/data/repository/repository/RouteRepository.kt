package com.example.pace.data.repository.repository

import com.example.pace.data.model.request.RouteRequest
import com.example.pace.data.model.response.RouteApiResponse

interface RouteRepository {
    suspend fun searchRoutes(
        accessToken: String,
        request: RouteRequest
    ): RouteApiResponse
}
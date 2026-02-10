package com.example.pace.data.api

import com.example.pace.data.model.request.RouteRequest
import com.example.pace.data.model.response.RouteApiResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header

interface RouteService {
    @GET("/api/v1/routes/search")
    suspend fun getRoutes(
        @Header("Authorization") accessToken: String,
        @Body request: RouteRequest
    ): Response<RouteApiResponse>

}
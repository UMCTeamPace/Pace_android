package com.example.pace.data.api

import com.example.pace.data.model.request.RouteRequest
import com.example.pace.data.model.request.RouteSearchRequest
import com.example.pace.data.model.response.RouteApiResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface RouteService {
    @GET("/api/v1/routes/search")
    suspend fun getRoutes(
        @Header("Authorization") accessToken: String,
        @Query("originLat") originLat: Double,
        @Query("originLng") originLng: Double,
        @Query("destLat") destLat: Double,
        @Query("destLng") destLng: Double,
        @Query("arrivalTime") arrivalTime: String?,
        @Query("departureTime") departureTime: String?,
        @Query("transitType") transitType: String?,
        @Query("searchWay") searchWay: String
    ): Response<RouteApiResponse>

}
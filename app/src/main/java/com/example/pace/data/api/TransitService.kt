package com.example.pace.data.api

import com.example.pace.data.model.request.SubwayTransitRequest
import com.example.pace.data.model.response.RawDefaultResponse
import com.example.pace.data.model.response.SubwayTransitResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface SubwayService {
    @GET("/api/v1/transit/arrivals")
    suspend fun getRealTimeSubwayArrivals(
        @Header("Authorization") token: String,
        @Body request: SubwayTransitRequest
    ): RawDefaultResponse<SubwayTransitResponse>
}

interface BusService{
    @GET("/getArrInfoByRoute")
    suspend fun getRealTimeBusArrivals(
        @Query("ServiceKey") serviceKey: String,
        @Query("stId") stationId: String,
        @Query("busRouteId") busRouteId: String,
        @Query("ord") ord: String
    )
}
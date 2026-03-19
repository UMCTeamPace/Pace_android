package com.example.pace.data.api

import com.example.pace.data.model.response.RawDefaultResponse
import com.example.pace.data.model.response.SubwayStationCodeResponse
import com.example.pace.data.model.response.SubwayTimeTableResponse
import com.example.pace.data.model.response.SubwayTransitResponse
import com.example.pace.data.model.response.SubwayTransitResult
import com.example.pace.data.model.response.TransitDefaultResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

interface SubwayService {
    @GET("/api/v1/transit/arrivals")
    suspend fun getRealTimeSubwayArrivals(
        @Header("Authorization") token: String,
        @Query("startStationName") startStationName: String,
        @Query("endStationName") endStationName: String,
        @Query("lineName") lineName: String
    ): Response<SubwayTransitResponse<SubwayTransitResult>>

    @GET("SearchSTNTimeTableByIDService/1/200/{STATION_CD}/{WEEK_TAG}/{INOUT_TAG}")
    suspend fun getSubwayTimetable(
        @Path("STATION_CD") stationCd: String,
        @Path("WEEK_TAG") weekTag: String,
        @Path("INOUT_TAG") inoutTag: String
    ):Map<String, TransitDefaultResponse<List<SubwayTimeTableResponse>>>
    @GET("SearchInfoBySubwayNameService/1/5/{STATION_NM}")
    suspend fun getSubwayCDByName(
        @Path("STATION_NM") stationNm: String
    ):Map<String, TransitDefaultResponse<List<SubwayStationCodeResponse>>>
}

interface BusService{
    @GET("/getArrInfoByRoute")
    fun getRealTimeBusArrivals(
        @Query("ServiceKey") serviceKey: String,
        @Query("stId") stationId: String,
        @Query("busRouteId") busRouteId: String,
        @Query("ord") ord: String
    )
}
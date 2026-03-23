package com.example.pace.data.api

import com.example.pace.data.model.response.BusParameterResponse
import com.example.pace.data.model.response.BusTransitResponse
import com.example.pace.data.model.response.RawDefaultResponse
import com.example.pace.data.model.response.SubwayTransitResponse
import com.example.pace.data.model.response.SubwayTransitResult
import com.example.pace.data.model.response.TimetableResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface SubwayService {
    // 실시간 지하철 도착 서비스
    @GET("/api/v1/transit/arrivals")
    suspend fun getRealTimeSubwayArrivals(
        @Header("Authorization") token: String,
        @Query("startStationName") startStationName: String,
        @Query("endStationName") endStationName: String,
        @Query("lineName") lineName: String
    ): Response<SubwayTransitResponse<SubwayTransitResult>>

    // 지하철 시간표
    @GET("getTrainSch")
    suspend fun getSubwayTimetable2(
        @Query("serviceKey") serviceKey: String,
        @Query("numOfRows") numOfRows: String,
        @Query("dataType") dataType: String,
        @Query("tmprTmtblYn") isTempOrNot: String,
        @Query("upbdnbSe") upbdnSe: String,     // 상하행
        @Query("wkndSe") wkndSe: String,        // 평일, 주말
        @Query("lineNm") lineNm: String,
        @Query("stnNm") stnNm: String,          // 지하철 역명
    ): Map<String, TimetableResponse>
}

// 실시간 버스 도착 서비스
interface BusService{
    @GET("api/rest/arrive/getArrInfoByRoute")
    suspend fun getRealTimeBusArrivals(
        @Query("ServiceKey") serviceKey: String,
        @Query("stId") stationId: String,
        @Query("busRouteId") busRouteId: String,
        @Query("ord") ord: String
    ):Response<BusTransitResponse>

    @GET("/api/v1/transit/bus/start-station")
    suspend fun getParameters(
        @Header("Authorization") token: String,
        @Query("lineName") lineName: String,
        @Query("startStation") startStation: String,
        @Query("endStation") endStation: String
    ): RawDefaultResponse<BusParameterResponse>
}
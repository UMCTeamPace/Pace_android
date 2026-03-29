package com.example.pace.data.api

import com.example.pace.data.model.response.BusParameterResponse
import com.example.pace.data.model.response.BusTransitResponse
import com.example.pace.data.model.response.RawDefaultResponse
import com.example.pace.data.model.response.StationIdItem
import com.example.pace.data.model.response.StationTimetableItem
import com.example.pace.data.model.response.SubwayTransitResponse
import com.example.pace.data.model.response.SubwayTransitResult
import com.example.pace.data.model.response.TagoResponse
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
    @GET("GetKwrdFndSubwaySttnList")
    suspend fun getSubwayStationId(
        @Query("serviceKey") serviceKey: String,
        @Query("pageNo") pageNo: String,
        @Query("numOfRows") numOfRows: String,
        @Query("dataType") dataType: String,
        @Query("subwayStationName") station: String,
    ): Map<String, TagoResponse<StationIdItem>>

    @GET("GetSubwaySttnAcctoSchdulList")
    suspend fun getSubwayTimetable(
        @Query("serviceKey") serviceKey: String,
        @Query("pageNo") pageNo: String,
        @Query("numOfRows") numOfRows: String,
        @Query("_type") dataType: String,
        @Query("subwayStationId") stationId: String,
        @Query("dailyTypeCode") daily: String,           // 01: 평일, 02: 토요일, 03: 공휴일
        @Query("upDownTypeCode") upDown: String,         // U: 상행, 내선   D: 하행, 외선
    ): Map<String, TagoResponse<StationTimetableItem>>
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
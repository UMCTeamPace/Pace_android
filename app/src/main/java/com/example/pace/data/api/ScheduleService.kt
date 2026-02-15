package com.example.pace.data.api

import com.example.pace.data.model.request.CreateScheduleRequest
import com.example.pace.data.model.request.DeleteScheduleRequest
import com.example.pace.data.model.request.UpdateRouteRequest
import com.example.pace.data.model.request.UpdateScheduleRequest
import com.example.pace.data.model.request.UpdateScheduleRouteRequest
import com.example.pace.data.model.response.CreateScheduleResponse
import com.example.pace.data.model.response.DeleteScheduleRouteResponse
import com.example.pace.data.model.response.RawDefaultResponse
import com.example.pace.data.model.response.ScheduleConversionResponse
import com.example.pace.data.model.response.ScheduleDetailResponse
import com.example.pace.data.model.response.ScheduleListResponse
import com.example.pace.data.model.response.SchedulePagingResponse
import com.example.pace.data.model.response.UpdateScheduleRouteResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface ScheduleService {

    @PUT("/api/v1/schedules/{scheduleId}/route")
    suspend fun updateScheduleRoute(
        @Header("Authorization") accessToken: String,
        @Path("scheduleId") scheduleId: Long,
        @Body request: UpdateScheduleRouteRequest
    ): RawDefaultResponse<UpdateScheduleRouteResponse>

    @DELETE("/api/v1/schedules/{scheduleId}/route")
    suspend fun deleteScheduleRoute(
        @Header("Authorization") accessToken: String,
        @Path("scheduleId") scheduleId: Long
    ): RawDefaultResponse<DeleteScheduleRouteResponse>

    @GET("/api/v1/schedules")
    suspend fun getScheduleList(
        @Header("Authorization") accessToken: String,
        @Query("startDate") startDate: String,
        @Query("endDate") endDate: String? = null,
        @Query("lastDate") lastDate: String? = null,
        @Query("lastId") lastId: Long? = null
    ): RawDefaultResponse<SchedulePagingResponse>

    @GET("/api/v1/schedules")
    suspend fun getScheduleListForRoute(
        @Header("Authorization") accessToken: String,
        @Query("startDate") startDate: String,
        @Query("endDate") endDate: String? = null,
        @Query("lastDate") lastDate: String? = null,
        @Query("lastId") lastId: Long? = null
    ): RawDefaultResponse<CreateScheduleResponse>

    @POST("/api/v1/schedules")
    suspend fun createSchedule(
        @Header("Authorization") accessToken: String,
        @Body request: CreateScheduleRequest
    ): RawDefaultResponse<CreateScheduleResponse>

    @HTTP(method = "DELETE", path = "/api/v1/schedules", hasBody = true)
    suspend fun deleteSchedules(
        @Header("Authorization") accessToken: String,
        @Body request: DeleteScheduleRequest
    ): RawDefaultResponse<String>

    @GET("/api/v1/schedules/{scheduleId}")
    suspend fun getScheduleDetail(
        @Header("Authorization") accessToken: String,
        @Path("scheduleId") scheduleId: Long
    ): RawDefaultResponse<ScheduleDetailResponse>

    @PATCH("/api/v1/schedules/{scheduleId}")
    suspend fun updateSchedule(
        @Header("Authorization") accessToken: String,
        @Path("scheduleId") scheduleId: Long,
        @Query("scope") scope: String = "SINGLE",
        @Body request: UpdateScheduleRequest
    ): RawDefaultResponse<ScheduleDetailResponse>

    @PATCH("/api/v1/schedules/{id}/conversion")
    suspend fun convertRouteToGeneral(
        @Header("Authorization") accessToken: String,
        @Path("id") id: Long
    ): RawDefaultResponse<ScheduleConversionResponse>

}
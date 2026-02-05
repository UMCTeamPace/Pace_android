package com.example.pace.data.repository.repository

import com.example.pace.data.model.request.CreateScheduleRequest
import com.example.pace.data.model.request.DeleteScheduleRequest
import com.example.pace.data.model.request.UpdateScheduleRequest
import com.example.pace.data.model.request.UpdateScheduleRouteRequest
import com.example.pace.data.model.response.CreateScheduleResponse
import com.example.pace.data.model.response.DeleteScheduleRouteResponse
import com.example.pace.data.model.response.RawDefaultResponse
import com.example.pace.data.model.response.ScheduleConversionResponse
import com.example.pace.data.model.response.ScheduleDetailResponse
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

interface ScheduleRepository {


    suspend fun updateScheduleRoute(
      accessToken: String,
        scheduleId: Long,
        request: UpdateScheduleRouteRequest
    ): RawDefaultResponse<UpdateScheduleRouteResponse>


    suspend fun deleteScheduleRoute(
        accessToken: String,
        scheduleId: Long
    ): RawDefaultResponse<DeleteScheduleRouteResponse>

    suspend fun getScheduleList(
        accessToken: String,
        startDate: String,
        endDate: String? = null,
        lastDate: String? = null,
        lastId: Long? = null
    ): RawDefaultResponse<SchedulePagingResponse>


    suspend fun createSchedule(
        accessToken: String,
        request: CreateScheduleRequest
    ): RawDefaultResponse<CreateScheduleResponse>


    suspend fun deleteSchedules(
        accessToken: String,
        request: DeleteScheduleRequest
    ): RawDefaultResponse<String>


    suspend fun getScheduleDetail(
        accessToken: String,
        scheduleId: Long
    ): RawDefaultResponse<ScheduleDetailResponse>


    suspend fun updateSchedule(
        accessToken: String,
        scheduleId: Long,
        scope: String = "SINGLE",
        request: UpdateScheduleRequest
    ): RawDefaultResponse<ScheduleDetailResponse>


    suspend fun convertRouteToGeneral(
        accessToken: String,
        id: Long
    ): RawDefaultResponse<ScheduleConversionResponse>

}
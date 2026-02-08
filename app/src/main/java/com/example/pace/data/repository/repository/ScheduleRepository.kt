package com.example.pace.data.repository.repository

import com.example.pace.data.model.Schedule
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
import kotlinx.coroutines.flow.Flow

interface ScheduleRepository {

    // 원스톤 기능: 로컬 DB 감시
    val allSchedules: Flow<List<Schedule>>
    val calendarEvents: Flow<Unit>

    // 원스톤 기능: 로컬 업데이트 및 동기화
    suspend fun updateSchedule(schedule: Schedule)
    suspend fun refreshSchedules()

    //썽아 기능 (여기서부터)
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
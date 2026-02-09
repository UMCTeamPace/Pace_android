package com.example.pace.data.repository.repository

import com.example.pace.data.model.Schedule
import com.example.pace.data.model.request.*
import com.example.pace.data.model.response.*
import kotlinx.coroutines.flow.Flow

interface ScheduleRepository {
    val allSchedules: Flow<List<Schedule>>
    val calendarEvents: Flow<Unit>

    // 로컬 DB 로직
    suspend fun updateSchedule(schedule: Schedule)
    suspend fun refreshSchedules()
    fun getUsedColors(): Flow<List<String>>
    suspend fun searchSchedules(query: String, colors: Set<String>, includeRoute: Boolean, startDate: String, endDate: String): List<Schedule>

    // 서버 API 로직
    suspend fun getScheduleList(accessToken: String, startDate: String, endDate: String?, lastDate: String?, lastId: Long?): RawDefaultResponse<SchedulePagingResponse>
    suspend fun createSchedule(accessToken: String, request: CreateScheduleRequest): RawDefaultResponse<CreateScheduleResponse>
    suspend fun getScheduleDetail(accessToken: String, scheduleId: Long): RawDefaultResponse<ScheduleDetailResponse>
    suspend fun updateSchedule(accessToken: String, scheduleId: Long, scope: String, request: UpdateScheduleRequest): RawDefaultResponse<ScheduleDetailResponse>
    suspend fun deleteSchedules(accessToken: String, request: DeleteScheduleRequest): RawDefaultResponse<String>
    suspend fun updateScheduleRoute(accessToken: String, scheduleId: Long, request: UpdateScheduleRouteRequest): RawDefaultResponse<UpdateScheduleRouteResponse>
    suspend fun deleteScheduleRoute(accessToken: String, scheduleId: Long): RawDefaultResponse<DeleteScheduleRouteResponse>
    suspend fun convertRouteToGeneral(accessToken: String, id: Long): RawDefaultResponse<ScheduleConversionResponse>
}
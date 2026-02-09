package com.example.pace.data.repository.repository

import com.example.pace.data.model.Schedule
import com.example.pace.data.model.request.*
import com.example.pace.data.model.response.*
import kotlinx.coroutines.flow.Flow

interface ScheduleRepository {
    // --- 로컬/원스톤 로직 ---
    val allSchedules: Flow<List<Schedule>>
    val calendarEvents: Flow<Unit>
    suspend fun updateSchedule(schedule: Schedule)
    suspend fun refreshSchedules()
    fun getUsedColors(): Flow<List<String>> // ViewModel에서 사용
    suspend fun searchSchedules( // ViewModel에서 사용
        query: String,
        colors: Set<String>,
        includeRoute: Boolean,
        startDate: String,
        endDate: String
    ): List<Schedule>

    // --- 서버/썽아 로직 ---
    suspend fun updateScheduleRoute(accessToken: String, scheduleId: Long, request: UpdateScheduleRouteRequest): RawDefaultResponse<UpdateScheduleRouteResponse>
    suspend fun deleteScheduleRoute(accessToken: String, scheduleId: Long): RawDefaultResponse<DeleteScheduleRouteResponse>
    suspend fun getScheduleList(accessToken: String, startDate: String, endDate: String? = null, lastDate: String? = null, lastId: Long? = null): RawDefaultResponse<SchedulePagingResponse>
    suspend fun createSchedule(accessToken: String, request: CreateScheduleRequest): RawDefaultResponse<CreateScheduleResponse>
    suspend fun deleteSchedules(accessToken: String, request: DeleteScheduleRequest): RawDefaultResponse<String>
    suspend fun getScheduleDetail(accessToken: String, scheduleId: Long): RawDefaultResponse<ScheduleDetailResponse>
    suspend fun updateSchedule(accessToken: String, scheduleId: Long, scope: String = "SINGLE", request: UpdateScheduleRequest): RawDefaultResponse<ScheduleDetailResponse>
    suspend fun convertRouteToGeneral(accessToken: String, id: Long): RawDefaultResponse<ScheduleConversionResponse>
}
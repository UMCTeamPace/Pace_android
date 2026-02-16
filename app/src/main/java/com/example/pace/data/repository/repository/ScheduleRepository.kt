package com.example.pace.data.repository.repository

import com.example.pace.data.model.Schedule
import com.example.pace.data.model.request.*
import com.example.pace.data.model.response.*
import kotlinx.coroutines.flow.Flow

interface ScheduleRepository {
    val allSchedules: Flow<List<Schedule>>
    val calendarEvents: Flow<Unit>

    suspend fun updateSchedule(schedule: Schedule)
    suspend fun refreshSchedules()
    suspend fun cleanUpSystemDeletedSchedules() // [추가] 시스템 삭제분 정리

    fun getUsedColors(): Flow<List<String>>
    fun getCalendarName(calendarId: Long): String?

    suspend fun searchSchedules(
        query: String,
        colors: Set<String>,
        includeRoute: Boolean,
        startDate: String,
        endDate: String,
        selectedIds: List<Long> // 추가됨
    ): List<Schedule>

    suspend fun createSchedule(
        accessToken: String?,
        request: CreateScheduleRequest,
        placeId: String? = null,
        calendarId: Long? = null,
        selectedColor: Int? = null
    ): RawDefaultResponse<CreateScheduleResponse>

    // 서버 API 관련
    suspend fun getScheduleList(accessToken: String, startDate: String, endDate: String?, lastDate: String?, lastId: Long?): RawDefaultResponse<SchedulePagingResponse>
    suspend fun getScheduleDetail(accessToken: String, scheduleId: Long): RawDefaultResponse<ScheduleDetailResponse>
    suspend fun updateSchedule(accessToken: String, scheduleId: Long, scope: String, request: UpdateScheduleRequest): RawDefaultResponse<ScheduleDetailResponse>
    suspend fun deleteSchedules(accessToken: String, request: DeleteScheduleRequest): RawDefaultResponse<String>
    suspend fun updateScheduleRoute(accessToken: String, scheduleId: Long, request: UpdateScheduleRouteRequest): RawDefaultResponse<UpdateScheduleRouteResponse>
    suspend fun deleteScheduleRoute(accessToken: String, scheduleId: Long): RawDefaultResponse<DeleteScheduleRouteResponse>
    suspend fun convertRouteToGeneral(accessToken: String, id: Long): RawDefaultResponse<ScheduleConversionResponse>
    suspend fun deleteNormalSchedule(id: Long): RawDefaultResponse<String>
    suspend fun deleteRouteSchedule(id: Long): RawDefaultResponse<String>
    suspend fun getScheduleListForRoute(
        accessToken: String,
        startDate: String,
        endDate: String?
    ): RawDefaultResponse<RouteOnlyScheduleData?>

    suspend fun updateExDate(schedule: Schedule)

}
package com.example.pace.data.repository.repositoryImpl

import com.example.pace.data.api.ScheduleService
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
import com.example.pace.data.repository.repository.ScheduleRepository
import com.example.pace.data.util.safeApiCall
import javax.inject.Inject

class ScheduleRepositoryImpl @Inject constructor(
    private val api : ScheduleService
) : ScheduleRepository {
    override suspend fun updateScheduleRoute(
        accessToken: String,
        scheduleId: Long,
        request: UpdateScheduleRouteRequest
    ): RawDefaultResponse<UpdateScheduleRouteResponse> {
        return safeApiCall { api.updateScheduleRoute(accessToken, scheduleId, request) }
    }

    override suspend fun deleteScheduleRoute(
        accessToken: String,
        scheduleId: Long
    ): RawDefaultResponse<DeleteScheduleRouteResponse> {
        return safeApiCall { api.deleteScheduleRoute(accessToken, scheduleId) }
    }

    override suspend fun getScheduleList(
        accessToken: String,
        startDate: String,
        endDate: String?,
        lastDate: String?,
        lastId: Long?
    ): RawDefaultResponse<SchedulePagingResponse> {
        return safeApiCall { api.getScheduleList(accessToken, startDate, endDate, lastDate, lastId) }
    }

    override suspend fun createSchedule(
        accessToken: String,
        request: CreateScheduleRequest
    ): RawDefaultResponse<CreateScheduleResponse> {
        return safeApiCall { api.createSchedule(accessToken, request) }
    }

    override suspend fun deleteSchedules(
        accessToken: String,
        request: DeleteScheduleRequest
    ): RawDefaultResponse<String> {
        return safeApiCall { api.deleteSchedules(accessToken, request) }
    }

    override suspend fun getScheduleDetail(
        accessToken: String,
        scheduleId: Long
    ): RawDefaultResponse<ScheduleDetailResponse> {
        return safeApiCall { api.getScheduleDetail(accessToken, scheduleId) }
    }

    override suspend fun updateSchedule(
        accessToken: String,
        scheduleId: Long,
        scope: String,
        request: UpdateScheduleRequest
    ): RawDefaultResponse<ScheduleDetailResponse> {
        return safeApiCall { api.updateSchedule(accessToken, scheduleId, scope, request) }
    }

    override suspend fun convertRouteToGeneral(
        accessToken: String,
        id: Long
    ): RawDefaultResponse<ScheduleConversionResponse> {
        return safeApiCall { api.convertRouteToGeneral(accessToken, id) }

    }
}
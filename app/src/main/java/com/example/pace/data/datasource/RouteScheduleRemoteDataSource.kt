package com.example.pace.data.datasource

import com.example.pace.data.api.ScheduleService
import com.example.pace.data.model.request.DeleteScheduleRequest
import com.example.pace.data.model.request.UpdateScheduleRequest
import com.example.pace.data.model.request.UpdateScheduleRouteRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RouteScheduleRemoteDataSource @Inject constructor(
    private val scheduleService: ScheduleService
) {

    suspend fun getScheduleList(
        token: String,
        startDate: String,
        endDate: String? = null,
        lastDate: String? = null,
        lastId: Long? = null
    ) = scheduleService.getScheduleList(token, startDate, endDate, lastDate, lastId)

    suspend fun getScheduleDetail(token: String, scheduleId: Long) =
        scheduleService.getScheduleDetail(token, scheduleId)

    suspend fun updateSchedule(
        token: String,
        scheduleId: Long,
        scope: String = "SINGLE",
        request: UpdateScheduleRequest
    ) = scheduleService.updateSchedule(token, scheduleId, scope, request)

    suspend fun deleteSchedules(token: String, request: DeleteScheduleRequest) =
        scheduleService.deleteSchedules(token, request)

    suspend fun updateScheduleRoute(
        token: String,
        scheduleId: Long,
        request: UpdateScheduleRouteRequest
    ) = scheduleService.updateScheduleRoute(token, scheduleId, request)

    suspend fun deleteScheduleRoute(token: String, scheduleId: Long) =
        scheduleService.deleteScheduleRoute(token, scheduleId)

    suspend fun convertRouteToGeneral(token: String, id: Long) =
        scheduleService.convertRouteToGeneral(token, id)
}

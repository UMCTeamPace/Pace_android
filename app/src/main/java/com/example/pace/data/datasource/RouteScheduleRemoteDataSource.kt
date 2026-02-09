package com.example.pace.data.datasource

import com.example.pace.data.api.ScheduleService
import com.example.pace.data.model.request.CreateScheduleRequest
import com.example.pace.data.model.request.DeleteScheduleRequest
import com.example.pace.data.model.request.UpdateScheduleRequest
import com.example.pace.data.model.request.UpdateScheduleRouteRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton // 앱 전체에서 하나만 존재하도록 설정
class RouteScheduleRemoteDataSource @Inject constructor(
    private val scheduleService: ScheduleService
) {

    // 1. 전체 일정 목록 가져오기 (페이징)
    suspend fun getScheduleList(
        token: String,
        startDate: String,
        endDate: String? = null,
        lastDate: String? = null,
        lastId: Long? = null
    ) = scheduleService.getScheduleList(token, startDate, endDate, lastDate, lastId)

    // 2. 새로운 일정 생성
    suspend fun createSchedule(token: String, request: CreateScheduleRequest) =
        scheduleService.createSchedule(token, request)

    // 3. 일정 상세 정보 조회
    suspend fun getScheduleDetail(token: String, scheduleId: Long) =
        scheduleService.getScheduleDetail(token, scheduleId)

    // 4. 일정 수정
    suspend fun updateSchedule(
        token: String,
        scheduleId: Long,
        scope: String = "SINGLE",
        request: UpdateScheduleRequest
    ) = scheduleService.updateSchedule(token, scheduleId, scope, request)

    // 5. 일정 삭제 (여러 개 선택 삭제 가능)
    suspend fun deleteSchedules(token: String, request: DeleteScheduleRequest) =
        scheduleService.deleteSchedules(token, request)

    // 6. 일정 내 경로 수정
    suspend fun updateScheduleRoute(
        token: String,
        scheduleId: Long,
        request: UpdateScheduleRouteRequest
    ) = scheduleService.updateScheduleRoute(token, scheduleId, request)

    // 7. 일정 내 경로 삭제
    suspend fun deleteScheduleRoute(token: String, scheduleId: Long) =
        scheduleService.deleteScheduleRoute(token, scheduleId)

    // 8. 경로 일정을 일반 일정으로 변환
    suspend fun convertRouteToGeneral(token: String, id: Long) =
        scheduleService.convertRouteToGeneral(token, id)
}
package com.example.pace.data.repository.repositoryImpl

import android.content.Context
import com.example.pace.data.api.ScheduleService
import com.example.pace.data.createCalendarObserver
import com.example.pace.data.datasource.AuthDataStore
import com.example.pace.data.datasource.NormalScheduleRemoteDataSource
import com.example.pace.data.db.ScheduleDao
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
import com.example.pace.data.repository.repository.ScheduleRepository
import com.example.pace.data.util.safeApiCall
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class ScheduleRepositoryImpl @Inject constructor(
    private val api : ScheduleService, // 썽아
    private val scheduleDao: ScheduleDao, //원스톤
    private val normalScheduleDataSource: NormalScheduleRemoteDataSource, // 원스톤
    private val authDataStore: AuthDataStore, // 썽아
    @ApplicationContext private val context: Context
) : ScheduleRepository {

    // 원스톤 로직
    override val allSchedules: Flow<List<Schedule>> = scheduleDao.getAllSchedules().map { rawList ->
        expandSchedules(rawList)
    }

    override val calendarEvents: Flow<Unit> = createCalendarObserver(context)

    override suspend fun updateSchedule(schedule: Schedule) {
        scheduleDao.updateSchedule(schedule)
    }

    /**
     * Refreshes all schedules from all remote data sources and merges them
     * with the local database, preserving local-only data like 'isPinned'.
     */
    override suspend fun refreshSchedules() {
        // --- 1. Fetch from all remote sources ---
        val normalSchedules = normalScheduleDataSource.getSchedules()
        // TODO: Fetch from route data source when ready
        // val routeSchedules = routeScheduleDataSource.getSchedules()

        // For now, we only have normal schedules. In the future, combine lists here.
        val allRemoteSchedules = normalSchedules

        // --- 2. Fetch current local data ---
        val localSchedules = scheduleDao.getAllSchedulesOnce()
        val localScheduleMap = localSchedules.associateBy { it.id }

        // --- 3. Perform Smart Merge ---
        val mergedSchedules = allRemoteSchedules.map { remoteSchedule ->
            val localSchedule = localScheduleMap[remoteSchedule.id]
            if (localSchedule != null) {
                // Preserve local-only data by merging
                remoteSchedule.copy(isPinned = localSchedule.isPinned)
            } else {
                remoteSchedule
            }
        }

        // --- 4. Identify and delete stale schedules ---
        val remoteScheduleIds = allRemoteSchedules.map { it.id }.toSet()
        val schedulesToDelete = localSchedules.filter { it.id !in remoteScheduleIds }

        // --- 5. Update database ---
        scheduleDao.deleteAll(schedulesToDelete)
        scheduleDao.insertAll(mergedSchedules)
    }

    private fun expandSchedules(rawSchedules: List<Schedule>): List<Schedule> {
        val expandedList = mutableListOf<Schedule>()
        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        rawSchedules.forEach { schedule ->
            val startLocalDate = LocalDate.parse(schedule.startDate, dateFormatter)
            val endLocalDate = LocalDate.parse(schedule.endDate, dateFormatter)

            // 1. 기간 일정 전개 (시작일 ~ 종료일 사이의 모든 날짜에 표시)
            if (startLocalDate.isBefore(endLocalDate)) {
                var current = startLocalDate
                while (!current.isAfter(endLocalDate)) {
                    expandedList.add(schedule.copy(startDate = current.format(dateFormatter)))
                    current = current.plusDays(1)
                }
            }
            // 2. 반복 일정 전개 (repeatRule이 있는 경우)
            else if (!schedule.repeatRule.isNullOrEmpty()) {
                // 간단한 매주(WEEKLY) 반복 예시 (필요에 따라 RRULE 파싱 라이브러리 사용 권장)
                if (schedule.repeatRule!!.contains("WEEKLY")) {
                    for (i in 0..24) { // 향후 약 6개월치 전개
                        val repeatedDate = startLocalDate.plusWeeks(i.toLong())
                        expandedList.add(schedule.copy(startDate = repeatedDate.format(dateFormatter)))
                    }
                } else {
                    expandedList.add(schedule)
                }
            }
            // 3. 일반 단일 일정
            else {
                expandedList.add(schedule)
            }
        }
        return expandedList
    }



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
        // 여기에 로그 추가!
        android.util.Log.d("API_TEST", "getScheduleList 호출됨! 시작일: $startDate, 마지막ID: $lastId")

        return safeApiCall {
            val response = api.getScheduleList(accessToken, startDate, endDate, lastDate, lastId)
            android.util.Log.d("API_TEST", "서버 응답 결과: $response") // 응답 성공 시 로그
            response
        }
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
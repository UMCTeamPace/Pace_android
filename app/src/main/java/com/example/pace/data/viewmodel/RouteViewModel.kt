package com.example.pace.data.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pace.data.model.response.RouteResponse
import com.example.pace.data.model.request.RouteSearchRequest
import com.example.pace.data.model.response.RouteOnlyScheduleData
import com.example.pace.data.repository.repository.RouteRepository
import com.example.pace.data.repository.repository.ScheduleRepository
import com.example.pace.data.repository.repositoryImpl.RouteRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class RouteViewModel @Inject constructor(
    private val routeRepository: RouteRepository,
    private val scheduleRepository: ScheduleRepository
) : ViewModel() {
    private val _routeResult = MutableLiveData<List<RouteResponse>>()
    val routeResult: LiveData<List<RouteResponse>> get() = _routeResult

    private val _routeOnlySchedule = MutableLiveData<RouteOnlyScheduleData?>()
    val routeOnlySchedule: LiveData<RouteOnlyScheduleData?> get() = _routeOnlySchedule

    private val _adapterScheduleData = MutableLiveData<RouteResponse?>()
    val adapterScheduleData: LiveData<RouteResponse?> get() = _adapterScheduleData
    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> get() = _isLoading

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> get() = _errorMessage

    fun searchRoutes(accessToken: String, request: RouteSearchRequest) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                Log.d("RouteApi", "API 호출 시도...") // 호출 시작 로그

                // Repository 호출
                val response = routeRepository.searchRoutes(accessToken, request)

                Log.d("RouteApi", "API 호출 성공! 데이터 개수: ${response.routeApiResDtoList?.size ?: 0}")

                _routeResult.value = response.routeApiResDtoList ?: emptyList() // 변수명 routeList 확인

            } catch (e: retrofit2.HttpException) {
                // 1. 서버가 에러 코드(4xx, 5xx)를 보낸 경우
                val errorBody = e.response()?.errorBody()?.string()
                Log.e("RouteApiError", "서버 에러 발생 (HttpException)")
                Log.e("RouteApiError", "Code: ${e.code()}")
                Log.e("RouteApiError", "Message: ${e.message()}")
                Log.e("RouteApiError", "Error Body: $errorBody") // 서버가 보낸 에러 메시지 원본

                _errorMessage.value = "서버 에러: ${e.code()} - $errorBody"

            } catch (e: Exception) {
                // 2. 그 외 에러 (네트워크 끊김, 데이터 파싱 실패 등)
                Log.e("RouteApiError", "내부 시스템 에러 발생")
                Log.e("RouteApiError", "에러 내용: ${e.message}")
                e.printStackTrace() // 에러 위치를 자세히 보여줌

                _errorMessage.value = "에러 발생: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun fetchRouteOnlySchedule(accessToken: String) {
        viewModelScope.launch {
            try {
                // 오늘 날짜 구하기 (yyyy-MM-dd)
                val today = LocalDate.now().toString()

                Log.d("RouteViewModel", "스케줄 조회 시작: $today")

                // 리포지토리 호출 (RawDefaultResponse 반환됨)
                val response = scheduleRepository.getScheduleListForRoute(
                    accessToken = accessToken,
                    startDate = today,
                    endDate = null // null 보내면 endDate는 startDate와 동일하게 처리되거나 API 스펙따라감
                )

                if (response.isSuccess) {
                    // 통신 성공 (200 OK)
                    val data = response.result

                    if (data != null) {
                        Log.d("RouteViewModel", "경로 스케줄 발견! ID: ${data.scheduleId}")
                        // UI에 데이터 전달 -> "경로 안내 하시겠습니까?" 띄우기 가능
                        _routeOnlySchedule.value = data
                    } else {
                        Log.d("RouteViewModel", "⚠통신 성공했으나, 경로만 있는 스케줄이 없음")
                        // 데이터 없음 (null 전달)
                        _routeOnlySchedule.value = null
                    }
                } else {
                    // 통신 실패 (4xx, 5xx) - 에러 코드 확인 가능
                    Log.e("RouteViewModel", "서버 에러: ${response.code} - ${response.message}")
                    _routeOnlySchedule.value = null

                    // 필요하다면 에러 처리를 따로 할 수도 있음
                    if (response.code == "AUTH_401") {
                        // 토큰 만료 처리 등
                    }
                }

            } catch (e: Exception) {
                Log.e("RouteViewModel", "예외 발생: ${e.message}")
                e.printStackTrace()
                _routeOnlySchedule.value = null
            }
        }
    }
    fun updateScheduleRoute(assembledResponse: RouteResponse) {
        val currentData = _routeOnlySchedule.value
        if (currentData != null) {
            // 기존 scheduleId, scheduleInfo는 유지하고 route 정보만 조립된 데이터로 교체
            // 이때 routeDetails 리스트가 RouteResponse의 리스트로 대체됩니다.
            val updatedData = currentData.copy(
                route = currentData.route?.copy(
                    routeDetails = assembledResponse.routeDetails
                )
            )
            _routeOnlySchedule.value = updatedData
            Log.d("RouteViewModel", "✅ 어댑터용 데이터 업데이트 완료")
        }
    }

    fun updateScheduleForAdapter(assembledResponse: RouteResponse) {
        // 원본(_routeOnlySchedule)을 건드리지 않고, 어댑터용 LiveData만 업데이트
        _adapterScheduleData.value = assembledResponse
        Log.d("RouteViewModel", "✅ 어댑터 전용 데이터 업데이트 완료 (무한루프 방지)")
    }
}


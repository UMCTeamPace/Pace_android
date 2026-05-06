package com.example.pace.data.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pace.data.datasource.AuthDataStore
import com.example.pace.data.model.request.RouteSearchRequest
import com.example.pace.data.model.response.RouteOnlyScheduleData
import com.example.pace.data.model.response.RouteResponse
import com.example.pace.data.repository.repository.RouteRepository
import com.example.pace.data.repository.repository.ScheduleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class RouteViewModel @Inject constructor(
    private val routeRepository: RouteRepository,
    private val scheduleRepository: ScheduleRepository,
    private val authDataStore: AuthDataStore
) : ViewModel() {
    private val _routeResult = MutableLiveData<List<RouteResponse>>()
    val routeResult: LiveData<List<RouteResponse>> get() = _routeResult

    private val _routeOnlySchedule = MutableLiveData<RouteOnlyScheduleData?>()
    val routeOnlySchedule: LiveData<RouteOnlyScheduleData?> get() = _routeOnlySchedule

    private val _selectedRouteSchedule = MutableLiveData<RouteOnlyScheduleData?>()
    val selectedRouteSchedule: LiveData<RouteOnlyScheduleData?> get() = _selectedRouteSchedule

    private val _routeScheduleList = MutableLiveData<List<RouteOnlyScheduleData>>()
    val routeScheduleList: LiveData<List<RouteOnlyScheduleData>> get() = _routeScheduleList

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
                Log.d("RouteApi", "API route search start")

                val response = routeRepository.searchRoutes(accessToken, request)
                val routes = response.routeApiResDtoList.orEmpty()

                Log.d("RouteApi", "API route search success: count=${routes.size}")

                if (routes.isNotEmpty()) {
                    _errorMessage.value = ""
                    _routeResult.value = routes
                } else {
                    handleRouteSearchFailure("empty route response")
                }
            } catch (e: retrofit2.HttpException) {
                val errorBody = e.response()?.errorBody()?.string()
                Log.e("RouteApiError", "Route search http error: code=${e.code()}, body=$errorBody")
                handleRouteSearchFailure("http ${e.code()} - $errorBody")
            } catch (e: Exception) {
                Log.e("RouteApiError", "Route search exception: ${e.message}", e)
                handleRouteSearchFailure("exception: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun handleRouteSearchFailure(reason: String) {
        Log.w("RouteApi", "Route search fallback suppressed: $reason")
        _errorMessage.value = reason
        _routeResult.value = emptyList()
    }

    fun selectRouteSchedule(schedule: RouteOnlyScheduleData) {
        _selectedRouteSchedule.value = schedule
    }

    fun fetchRouteOnlySchedule() {
        viewModelScope.launch {
            try {
                val token = authDataStore.getAccessToken() ?: ""
                val fullToken = if (token.isNotEmpty() && !token.startsWith("Bearer ")) "Bearer $token" else token
                if (fullToken.isEmpty()) {
                    _routeOnlySchedule.value = null
                    return@launch
                }
                val today = LocalDate.now().toString()

                Log.d("RouteViewModel", "Fetch route-only schedule: $today")

                val response = scheduleRepository.getScheduleListForRoute(
                    accessToken = fullToken,
                    startDate = today,
                    endDate = today
                )

                if (response.isSuccess) {
                    val data = response.result

                    if (data != null) {
                        Log.d("RouteViewModel", "Route-only schedule found: ${data.scheduleId}")
                        _routeOnlySchedule.value = data
                    } else {
                        Log.d("RouteViewModel", "Route-only schedule not found")
                        _routeOnlySchedule.value = null
                    }
                } else {
                    Log.e("RouteViewModel", "Route-only schedule server error: ${response.code} - ${response.message}")
                    _routeOnlySchedule.value = null
                }
            } catch (e: Exception) {
                Log.e("RouteViewModel", "Route-only schedule exception: ${e.message}", e)
                _routeOnlySchedule.value = null
            }
        }
    }

    fun fetchAllRouteSchedules() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val token = authDataStore.getAccessToken() ?: ""
                val fullToken = if (token.isNotEmpty() && !token.startsWith("Bearer ")) "Bearer $token" else token
                if (fullToken.isEmpty()) {
                    _routeScheduleList.value = emptyList()
                    return@launch
                }
                val today = LocalDate.now().toString()

                val response = scheduleRepository.getAllRouteSchedules(
                    accessToken = fullToken,
                    startDate = today,
                    endDate = null
                )

                if (response.isSuccess) {
                    val serverSchedules = response.result?.filterNotNull() ?: emptyList()
                    _routeScheduleList.value = if (serverSchedules.isNotEmpty()) {
                        serverSchedules
                    } else {
                        scheduleRepository.getLocalRouteSchedules(
                            startDate = today,
                            endDate = today
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("RouteViewModel", "Fetch all route schedules failed: ${e.message}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateScheduleForAdapter(assembledResponse: RouteResponse) {
        _adapterScheduleData.value = assembledResponse
        Log.d("RouteViewModel", "Adapter route data updated")
    }
}

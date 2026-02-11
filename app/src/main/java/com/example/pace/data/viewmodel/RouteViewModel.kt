package com.example.pace.data.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pace.data.model.response.RouteResponse
import com.example.pace.data.model.request.RouteSearchRequest
import com.example.pace.data.repository.repository.RouteRepository
import com.example.pace.data.repository.repositoryImpl.RouteRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RouteViewModel @Inject constructor(
    private val routeRepository: RouteRepository
) : ViewModel() {
    private val _routeResult = MutableLiveData<List<RouteResponse>>()
    val routeResult: LiveData<List<RouteResponse>> get() = _routeResult

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
}


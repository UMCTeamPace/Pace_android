package com.example.pace.data.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.pace.data.api.RetrofitClient
import com.example.pace.data.model.AlertTheme
import com.example.pace.data.model.PrepStep
import com.example.pace.data.model.WeatherStatus
import com.example.pace.data.model.getAlertTheme
import com.example.pace.data.model.getOfflineAlertTheme
import com.example.pace.data.model.response.WeatherResponse
import com.example.pace.data.util.NetworkManager
import com.example.pace.data.util.WeatherMapper
import dagger.hilt.android.internal.Contexts.getApplication
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class AlertViewModel(application: Application) : AndroidViewModel(application) {
    // UI가 관찰할 수 있도록 LiveData 사용
    private val _alertTheme = MutableLiveData<AlertTheme>()
    val alertTheme = MutableLiveData<AlertTheme>()
    val isLoading = MutableLiveData<Boolean>()

    val isOffline = MutableLiveData<Boolean>(false)
    fun loadAlertData(cityName: String, apiKey: String, minutesLeft: Int) {
        val context = getApplication<Application>().applicationContext
        val prepStep = getPreparationStep(minutesLeft)

        if (!NetworkManager.isOnline(context)) {
            // 네트워크가 없어도 준비 단계에 맞는 오프라인 화면을 보여줌
            alertTheme.value = getOfflineAlertTheme(prepStep)
            isOffline.value = true
            return
        }

        // 1. 네트워크 체크
        if (!NetworkManager.isOnline(context)) {
            isOffline.value = true
            return
        }

        isLoading.value = true
        isOffline.value = false

        // 2. 날씨 API 호출
        RetrofitClient.weatherService.getWeather(cityName, apiKey)
            .enqueue(object : Callback<WeatherResponse> {
                override fun onResponse(call: Call<WeatherResponse>, response: Response<WeatherResponse>) {
                    isLoading.value = false
                    if (response.isSuccessful) {
                        val body = response.body()
                        val apiWeather = body?.weather?.get(0)?.main ?: "Clear"
                        val temp = body?.main?.temp ?: 20.0

                        // 3. 데이터 변환 (Mapper & Logic)
                        val weatherStatus = WeatherMapper.mapToWeatherStatus(apiWeather, temp)
                        val prepStep = getPreparationStep(minutesLeft)

                        // 4. UI 테마 결정 및 전달
                        if (body != null) {
                            val location = body.name // 도시 이름 (예: 서울)
                            val weatherDesc = body.weather[0].description // 날씨 설명 (예: 맑음)

                            // 수정된 getAlertTheme 인자에 맞춰 실제 값들을 전달합니다.
                            alertTheme.value = getAlertTheme(
                                step = prepStep,
                                weather = weatherStatus,
                                location = location,
                                temp = temp,
                                weatherDesc = weatherDesc
                            )
                        }
                    } else {
                        isOffline.value = true // 서버 에러 시에도 에러 페이지 노출
                    }
                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    isLoading.value = false
                    isOffline.value = true
                }
            })
    }

    // 시간을 기반으로 준비 단계를 반환하는 내부 함수
    private fun getPreparationStep(minutes: Int): PrepStep {
        return when {
            minutes in 50..60 -> PrepStep.SHOWER
            minutes in 30..49 -> PrepStep.PREPARE // 기획안에 따라 범위 조정
            minutes in 5..29 -> PrepStep.GET_STARTED
            else -> PrepStep.IMMINENT
        }
    }

    // 1. 알람 데이터 초기화 함수 (Activity의 onCreate에서 호출)
    fun initAlarmData(minutes: Int) {
        // 현재 날씨 상태 (실제로는 API나 기기 저장소에서 가져와야 하지만,
        // 테스트를 위해 기본값 SUNNY를 넣거나 전역 변수를 사용하세요)
        val weather = WeatherStatus.SUNNY

        // [단계 결정] 전달받은 분(minutes)에 따라 PrepStep 결정
        val step = when (minutes) {
            in 51..60 -> PrepStep.SHOWER
            in 21..40 -> PrepStep.PREPARE
            in 1..20 -> PrepStep.GET_STARTED
            else -> PrepStep.IMMINENT
        }

        // [테마 생성] 아까 만든 getAlertTheme 함수 호출
        // 날씨 데이터가 아직 없다면 일단 오프라인 테마를 보여주게 방어 로직 추가
        val theme = try {
            getAlertTheme(step, weather, "서울", 23.0, "맑음")
        } catch (e: Exception) {
            getOfflineAlertTheme(step)
        }

        // 2. UI에 배달 (LiveData 업데이트)
        _alertTheme.value = theme
    }
}
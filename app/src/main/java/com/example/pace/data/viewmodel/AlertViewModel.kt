package com.example.pace.data.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
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

    // 1. LiveData 변수 정리 (빨간 줄 해결 핵심)
    private val _alertTheme = MutableLiveData<AlertTheme>()
    val alertTheme: LiveData<AlertTheme> get() = _alertTheme

    val isLoading = MutableLiveData<Boolean>()
    val isOffline = MutableLiveData<Boolean>(false)

    /**
     * 알람 데이터 초기화 함수
     * 초기 로딩 중에 빈 화면이 나오지 않게 기본값을 먼저 보여줍니다.
     */
    fun initAlarmData(minutes: Int) {
        val step = getPreparationStep(minutes)
        // [수정] _alertTheme.value를 사용해야 합니다.
        _alertTheme.value = try {
            getAlertTheme(step, WeatherStatus.SUNNY, "서울", 23.0, "맑음", minutes)
        } catch (e: Exception) {
            getOfflineAlertTheme(step, minutes)
        }
    }

    /**
     * 실시간 날씨 로드 및 테마 업데이트
     */
    fun loadAlertData(cityName: String, apiKey: String, minutesLeft: Int) {
        val context = getApplication<Application>().applicationContext
        val prepStep = getPreparationStep(minutesLeft)

        if (!NetworkManager.isOnline(context)) {
            isOffline.value = true
            _alertTheme.value = getOfflineAlertTheme(prepStep, minutesLeft)
            return
        }

        isLoading.value = true
        isOffline.value = false

        Log.d("Pace_API", "요청 도시: $cityName, API 키 존재여부: ${apiKey.isNotEmpty()}")

        RetrofitClient.weatherService.getWeather(cityName, apiKey)
            .enqueue(object : Callback<WeatherResponse> {
                override fun onResponse(call: Call<WeatherResponse>, response: Response<WeatherResponse>) {
                    isLoading.value = false
                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()!!

                        val apiWeather = body.weather.getOrNull(0)?.main ?: "Clear"
                        val temp = body.main.temp
                        val location = body.name
                        val weatherDesc = body.weather.getOrNull(0)?.description ?: "맑음"

                        val weatherStatus = WeatherMapper.mapToWeatherStatus(apiWeather, temp)
                        val step = getPreparationStep(minutesLeft)

                        // [수정] 외부 노출용 alertTheme가 아닌 내부용 _alertTheme에 값을 넣어야 함
                        _alertTheme.value = getAlertTheme(
                            step = step,
                            weather = weatherStatus,
                            location = location,
                            temp = temp,
                            weatherDesc = weatherDesc,
                            minutesLeft = minutesLeft
                        )
                    } else {
                        _alertTheme.value = getOfflineAlertTheme(prepStep, minutesLeft)
                        android.util.Log.e("Pace_API", "실패 코드: ${response.code()}, 메시지: ${response.message()}")

                    }
                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    isLoading.value = false
                    isOffline.value = true
                    _alertTheme.value = getOfflineAlertTheme(prepStep, minutesLeft)

                }
            })
    }

    private fun getPreparationStep(minutes: Int): PrepStep {
        return when {
            minutes in 50..60 -> PrepStep.SHOWER
            minutes in 30..49 -> PrepStep.PREPARE
            minutes in 5..29 -> PrepStep.GET_STARTED
            else -> PrepStep.IMMINENT
        }
    }
}
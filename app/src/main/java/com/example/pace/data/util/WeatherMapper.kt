package com.example.pace.data.util

import com.example.pace.data.model.WeatherStatus

object WeatherMapper {
    fun mapToWeatherStatus(apiWeather: String, temp: Double): WeatherStatus {
        return when {
            temp >= 33.0 -> WeatherStatus.HEAT_WAVE
            temp <= -10.0 -> WeatherStatus.COLD_WAVE
            apiWeather == "Clear" -> WeatherStatus.SUNNY
            apiWeather == "Rain" || apiWeather == "Drizzle" -> WeatherStatus.RAIN
            apiWeather == "Snow" -> WeatherStatus.SNOW
            else -> WeatherStatus.CLOUDY
        }
    }
}
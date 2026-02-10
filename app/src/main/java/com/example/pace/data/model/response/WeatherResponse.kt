package com.example.pace.data.model.response

data class WeatherResponse(
    val weather: List<Weather>,
    val main: Main,
    val name: String
)

data class Weather(
    val main: String,
    val description: String
)

data class Main(
    val temp: Double
)
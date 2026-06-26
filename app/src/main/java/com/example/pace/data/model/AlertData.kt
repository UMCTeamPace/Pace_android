package com.example.pace.data.model

import com.example.pace.R

enum class PrepStep(val timeRange: IntRange) {
    SHOWER(50..60),
    PREPARE(30..40),
    GET_STARTED(5..20),
    IMMINENT(0..0)
}

enum class WeatherStatus {
    SUNNY, RAIN, SNOW, CLOUDY, HEAT_WAVE, COLD_WAVE
}

data class AlertTheme(
    val imageRes: Int,
    val weatherIconRes: Int?,
    val subMessage: String,
    val mainMessage: String,
    val topWeatherInfo: String,
    val isOfflineMode: Boolean = false
)

fun getAlertTheme(
    step: PrepStep,
    weather: WeatherStatus,
    location: String,
    temp: Double,
    weatherDesc: String,
    minutesLeft: Int
): AlertTheme {
    val weatherInfo = "$location · $weatherDesc · ${temp.toInt()}°"
    val timeMessage = "출발까지 ${minutesLeft}분 남았어요."

    return when (step) {
        PrepStep.SHOWER -> AlertTheme(
            R.drawable.img_shower,
            null,
            "슬슬 준비를 시작하면 좋아요!",
            timeMessage,
            weatherInfo
        )

        PrepStep.PREPARE -> AlertTheme(
            R.drawable.img_prepare,
            R.drawable.img_prepare_sunny_days,
            "슬슬 외출 준비를 하면 좋아요!",
            timeMessage,
            weatherInfo
        )

        PrepStep.GET_STARTED -> when (weather) {
            WeatherStatus.RAIN -> AlertTheme(
                R.drawable.img_started_rainy,
                R.drawable.img_started_rainy_days,
                "나가기 전에 우산 챙기세요!",
                timeMessage,
                weatherInfo
            )

            WeatherStatus.SNOW -> AlertTheme(
                R.drawable.img_started_snowy,
                R.drawable.img_started_snowy_days,
                "나가기 전에 우산 챙기세요!",
                timeMessage,
                weatherInfo
            )

            WeatherStatus.CLOUDY -> AlertTheme(
                R.drawable.img_started_cloudy,
                R.drawable.img_started_cloudy_days,
                "갑작스러운 날씨에 조심하세요!",
                timeMessage,
                weatherInfo
            )

            WeatherStatus.HEAT_WAVE -> AlertTheme(
                R.drawable.img_started_heat,
                R.drawable.img_started_heat_wave,
                "햇빛이 강해요. 모자와 양산 챙기세요!",
                timeMessage,
                weatherInfo
            )

            WeatherStatus.COLD_WAVE -> AlertTheme(
                R.drawable.img_started_cold_wave,
                R.drawable.img_started_cold,
                "체감온도가 낮아요. 목도리와 장갑 챙기세요!",
                timeMessage,
                weatherInfo
            )

            WeatherStatus.SUNNY -> AlertTheme(
                R.drawable.img_get_started,
                R.drawable.img_prepare_sunny_days,
                "화창한 날씨예요. 좋은 하루!",
                timeMessage,
                weatherInfo
            )
        }

        PrepStep.IMMINENT -> AlertTheme(
            R.drawable.img_imminent,
            null,
            "지금 나가면 늦지 않아요!",
            "지금 출발해야 합니다.",
            weatherInfo
        )
    }
}

fun getOfflineAlertTheme(step: PrepStep, minutesLeft: Int): AlertTheme {
    val timeMessage = "출발까지 ${minutesLeft}분 남았어요."
    return when (step) {
        PrepStep.SHOWER -> AlertTheme(
            R.drawable.img_shower,
            null,
            "슬슬 준비를 시작하면 좋아요!",
            timeMessage,
            "오프라인 모드"
        )

        PrepStep.PREPARE -> AlertTheme(
            R.drawable.img_offline_prepare,
            null,
            "슬슬 외출 준비를 마치면 좋아요!",
            timeMessage,
            "오프라인 모드"
        )

        PrepStep.GET_STARTED -> AlertTheme(
            R.drawable.img_started_cloudy,
            null,
            "화창한 날씨예요. 좋은 하루!",
            timeMessage,
            "오프라인 모드"
        )

        PrepStep.IMMINENT -> AlertTheme(
            R.drawable.img_imminent,
            null,
            "지금 나가면 늦지 않아요!",
            "지금 출발해야 합니다.",
            "오프라인 모드"
        )
    }
}

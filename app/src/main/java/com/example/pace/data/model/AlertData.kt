package com.example.pace.data.model

import com.example.pace.R

// 1. 준비 단계 정의
enum class PrepStep(val timeRange: IntRange) {
    SHOWER(50..60),
    PREPARE(30..40),
    GET_STARTED(5..20),
    IMMINENT(0..0)
}

// 2. 날씨 상태 정의
enum class WeatherStatus {
    SUNNY, RAIN, SNOW, CLOUDY, HEAT_WAVE, COLD_WAVE
}

// 3. UI에 노출할 리소스 묶음
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
    weatherDesc: String
): AlertTheme {
    // 상단 칩에 들어갈 텍스트 조합 (예: "서울, 맑음, 23°")
    val weatherInfo = "$location, $weatherDesc, ${temp.toInt()}°"

    return when (step) {
        PrepStep.SHOWER -> {
            AlertTheme(R.drawable.img_shower, null,"슬슬 준비를 시작하면 좋아요!", "출발까지 1시간 남았어요.", weatherInfo)
        }
        PrepStep.PREPARE -> {
            AlertTheme(R.drawable.img_prepare,R.drawable.img_prepare_sunny_days, "슬슬 나갈 준비를 하면 좋아요!", "출발까지 30분 남았어요.", weatherInfo)
        }
        PrepStep.GET_STARTED -> {
            AlertTheme(R.drawable.img_get_started, R.drawable.img_prepare_sunny_days,"화창한 날씨예요 좋은 하루!","출발까지 10분 남았어요.", weatherInfo)
            AlertTheme(R.drawable.img_started_rainy, R.drawable.img_started_rainy_days,"나가기 전에 우산 챙기세요!","출발까지 5분 남았어요.", weatherInfo)
            AlertTheme(R.drawable.img_started_snowy, R.drawable.img_started_snowy_days,"나가기 전에 우산 챙기세요!","출발까지 5분 남았어요.", weatherInfo)
            AlertTheme(R.drawable.img_started_cloudy, R.drawable.img_started_cloudy_days,"갑작스러운 소나기 조심하세요!","출발까지 5분 남았어요.", weatherInfo)
            AlertTheme(R.drawable.img_started_heat, R.drawable.img_started_heat_wave,"햇빛이 강해요.모자나 양산 챙기세요!","출발까지 5분 남았어요.", weatherInfo)
            AlertTheme(R.drawable.img_started_cold_wave, R.drawable.img_started_cold,"체감온도가 낮아요.목도리,장갑 챙기세요!","출발까지 5분 남았어요.", weatherInfo)

        }
        PrepStep.IMMINENT -> {
            //일단 여기는 하단 빨간색으로 되는 부분 생략함
            AlertTheme(R.drawable.img_imminent,null, "지금 안나가면 지각!", "지금 출발해야 합니다", weatherInfo)
        }
    }
}

fun getOfflineAlertTheme(step: PrepStep): AlertTheme {
    return when (step) {
        PrepStep.SHOWER -> AlertTheme(
            R.drawable.img_shower,
            null,
            "슬슬 준비를 시작하면 좋아요!",
            "출발까지 1시간 남았어요.",
            "오프라인 모드"
        )
        PrepStep.PREPARE -> AlertTheme(
            R.drawable.img_offline_prepare,
            null,
            "슬슬 나갈 준비를 마치면 좋아요!",
            "출발까지 30분 남았어요.",
            "오프라인 모드"
        )
        PrepStep.GET_STARTED -> AlertTheme(
            R.drawable.img_started_cloudy,
            null,
            "화창한 날씨예요 좋은 하루!",
            "출발까지 10분 남았어요.",
            "오프라인 모드"
        )
        PrepStep.IMMINENT -> AlertTheme(
            R.drawable.img_imminent,
            null,
            "지금 안나가면 지각!",
            "지금 출발해야 합니다!",
            "오프라인 모드"
        )

    }
}
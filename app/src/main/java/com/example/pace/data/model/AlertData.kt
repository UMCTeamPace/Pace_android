package com.example.pace.data.model

import com.example.pace.R

// 1. 준비 단계 정의
//enum class PrepStep(val timeRange: IntRange) {
//    SHOWER(50..60),
//    PREPARE(30..40),
//    GET_STARTED(5..20),
//    IMMINENT(0..0)
//}
//
//// 2. 날씨 상태 정의
//enum class WeatherStatus {
//    SUNNY, RAIN, SNOW, CLOUDY, HEAT_WAVE, COLD_WAVE
//}
//
//// 3. UI에 노출할 리소스 묶음
//data class AlertTheme(
//    val lottieJson: Int,
//    val message: String,
//    val topWeatherInfo: String
//)
//
//fun getAlertTheme(step: PrepStep, weather: WeatherStatus): AlertTheme {
//    return when (step) {
//        PrepStep.SHOWER -> {
//            // 1단계: 50~60분 (기획안: Shower 단계)
//            // 보통 샤워 단계는 날씨 영향을 덜 받으므로 공통 애니메이션을 쓰거나 날씨별로 나눕니다.
//            //현재는 "서울, 맑음, 23°"라고 고정 텍스트를 넣었지만, 나중에는 실제 API에서 받아온 값(예: ${location}, ${weatherName}, ${temp}°)을 넣도록 수정
//            AlertTheme(R.raw.ani_shower, "슬슬 준비를 시작하면 좋아요!\n출발까지 1시간 남았어요.", "서울, 맑음, 23°")
//        }
//
//        PrepStep.PREPARE -> {
//            // 2단계: 30~40분 (기획안: Prepare 단계)
//            AlertTheme(R.raw.ani_prepare, "이제 본격적으로 나갈 준비를 해볼까요?\n잊으신 물건은 없나요?", "서울, 맑음, 23°")
//        }
//
//        PrepStep.GET_STARTED -> {
//            // 3단계: 5~20분 (기획안: Get Started 단계 - 날씨에 따라 캐릭터 변화)
//            when (weather) {
//                WeatherStatus.SUNNY -> AlertTheme(R.raw.ani_get_started_sunny, "날씨가 맑아요! 가벼운 발걸음으로 나가볼까요?", "서울, 맑음, 23°")
//                WeatherStatus.RAIN -> AlertTheme(R.raw.ani_get_started_rain, "비가 오고 있어요! 우산 챙기셨죠?", "서울, 비, 18°")
//                WeatherStatus.SNOW -> AlertTheme(R.raw.ani_get_started_snow, "눈이 내려요! 길이 미끄러우니 조심하세요.", "서울, 눈, -2°")
//                WeatherStatus.CLOUDY -> AlertTheme(R.raw.ani_get_started_cloudy, "구름이 많네요. 곧 출발할 시간이에요!", "서울, 흐림, 20°")
//                WeatherStatus.HEAT_WAVE -> AlertTheme(R.raw.ani_get_started_hot, "폭염 주의보! 시원한 물 한 병 챙기세요.", "서울, 폭염, 35°")
//                WeatherStatus.COLD_WAVE -> AlertTheme(R.raw.ani_get_started_cold, "한파 주의보! 옷을 든든히 입고 나가세요.", "서울, 한파, -15°")
//            }
//        }
//
//        PrepStep.IMMINENT -> {
//            // 4단계: 0분 (기획안: Schedule imminent 단계)
//            AlertTheme(R.raw.ani_imminent, "지금 출발해야 늦지 않아요!\n어서 서두르세요!", "서울, 맑음, 23°")
//        }
//    }
//}
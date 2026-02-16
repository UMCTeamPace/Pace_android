package com.example.pace.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.ln

object RouteCalculator {
    fun calculateWeight(duration: Int): Float{
        // 1~3분이면 동일
        return if(duration > 0 && duration <= 180) 1.0f
        // 이후에는 로그 스케일로 증가
        else{
            1.0f + ln((duration - 180).toFloat() / 180f + 1f)
        }
    }

    // [추가된 함수] 서버 시간 문자열을 알람용 밀리초(Long)로 변환
    fun convertUtcToMillis(serverDateStr: String?): Long {
        if (serverDateStr.isNullOrEmpty()) return System.currentTimeMillis()

        try {
            // 기존 convertUtcToKst와 동일한 포맷을 사용합니다.
            // 서버 데이터에 소수점(밀리초)이 포함되어 온다면 "yyyy-MM-dd'T'HH:mm:ss.SSS"로 수정하세요.
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            inputFormat.timeZone = TimeZone.getTimeZone("UTC")

            val date: Date = inputFormat.parse(serverDateStr) ?: return System.currentTimeMillis()

            // Date 객체의 time 값이 바로 알람 설정에 필요한 밀리초(UTC 기준 타임스탬프)입니다.
            return date.time

        } catch (e: Exception) {
            e.printStackTrace()
            return System.currentTimeMillis()
        }
    }

    fun convertUtcToKst(serverDateStr: String?): String {
        if (serverDateStr.isNullOrEmpty()) return ""

        try {
            // 1. 서버가 주는 날짜 형식 정의 (예: 2026-02-03T09:00:00)
            // 만약 서버가 초 단위 뒤에 .000 (밀리초)을 보낸다면 "yyyy-MM-dd'T'HH:mm:ss.SSS"로 수정해야 함
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            inputFormat.timeZone = TimeZone.getTimeZone("UTC") // "이 시간은 UTC다"라고 명시

            // 2. 문자열을 Date 객체로 변환
            val date: Date = inputFormat.parse(serverDateStr) ?: return ""

            // 3. 출력할 형식 정의 (예: 09:00)
            val outputFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            outputFormat.timeZone = TimeZone.getTimeZone("Asia/Seoul") // "한국 시간으로 바꿔라" (+9시간)

            return outputFormat.format(date)

        } catch (e: Exception) {
            e.printStackTrace()
            return "" // 에러 나면 빈 문자열 반환 (혹은 원래 문자열 반환)
        }
    }
}
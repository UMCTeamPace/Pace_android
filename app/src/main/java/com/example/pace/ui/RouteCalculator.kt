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
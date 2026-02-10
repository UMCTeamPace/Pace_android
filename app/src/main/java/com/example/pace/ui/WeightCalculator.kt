package com.example.pace.ui

import kotlin.math.ln

object WeightCalculator {
    fun forRouteDetailBrief(duration: Int): Float{
        // 1~3분이면 동일
        return if(duration <= 180) 1.0f
        // 이후에는 로그 스케일로 증가
        else{
            1.0f + ln((duration - 180).toFloat() / 180f + 1f)
        }
    }
}
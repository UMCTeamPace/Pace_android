package com.example.pace.data.util

suspend fun <T> safeApiCall(call: suspend () -> T): T {
    return try {
        call()
    } catch (e: Exception) {
        // 에러 처리 로직 (로깅 등)
        throw e
    }
}
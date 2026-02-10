package com.example.pace.data.util

import android.util.Log
import com.example.pace.data.model.response.RawDefaultResponse
import com.google.gson.Gson
import retrofit2.HttpException

suspend fun <T> safeApiCall(call: suspend () -> RawDefaultResponse<T>): RawDefaultResponse<T> {
    return try {
        call()
    } catch (e: HttpException) {
        // [수정 2] HttpException을 먼저 잡아야 e.response()를 사용할 수 있습니다.
        try {
            val errorJson = e.response()?.errorBody()?.string()

            if (errorJson != null) {
                // 에러 바디(JSON)를 파싱해서 서버가 보낸 code("PLACE_GROUP_400_1")를 가져옵니다.
                val errorResponse = Gson().fromJson(errorJson, RawDefaultResponse::class.java)

                Log.e("SafeApiCall", "Parsed Code: ${errorResponse.code}, Message: ${errorResponse.message}")

                @Suppress("UNCHECKED_CAST")
                errorResponse as RawDefaultResponse<T>
            } else {
                createErrorResponse("UNKNOWN_ERROR", "알 수 없는 서버 에러")
            }
        } catch (parseException: Exception) {
            parseException.printStackTrace()
            createErrorResponse("PARSE_ERROR", "에러 메시지 분석 실패")
        }
    } catch (e: Exception) {
        // 네트워크 에러 등 그 외 예외 처리
        e.printStackTrace()
        createErrorResponse("EXCEPTION", e.message ?: "알 수 없는 오류")
    }
}

private fun <T> createErrorResponse(code: String, message: String): RawDefaultResponse<T> {
    return RawDefaultResponse(
        isSuccess = false,
        code = code,
        message = message,
        result = null
    )
}
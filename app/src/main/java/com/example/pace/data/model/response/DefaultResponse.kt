package com.example.pace.data.model.response

sealed class DefaultResponse<out T> {

    data class Success<out T>(val data: T?) : DefaultResponse<T>()

    data class Failure(
        val code: String,
        val message: String
    ) : DefaultResponse<Nothing>()
}
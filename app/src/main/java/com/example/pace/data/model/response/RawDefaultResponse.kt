package com.example.pace.data.model.response

import com.google.gson.annotations.SerializedName

data class RawDefaultResponse<T>(
    @SerializedName("isSuccess") val isSuccess: Boolean,
    @SerializedName("code") val code: String,
    @SerializedName("message") val message: String,
    @SerializedName("result") val result: T?
)
data class TransitDefaultResponse<T>(
    @SerializedName("list_total_count") val count: Int,
    @SerializedName("RESULT") val result: TransitResult,
    @SerializedName("row") val row: T?
)

data class TransitResult(
    @SerializedName("CODE") val code: String,
    @SerializedName("MESSAGE") val message: String,
)
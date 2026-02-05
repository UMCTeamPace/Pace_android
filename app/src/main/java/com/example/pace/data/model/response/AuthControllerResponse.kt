package com.example.pace.data.model.response

import com.google.gson.annotations.SerializedName

data class KakaoLoginResponse(
    @SerializedName("email") val email: String,
    @SerializedName("nickname") val nickname: String,
    @SerializedName("accessToken") val accessToken: String,
    @SerializedName("refreshToken") val refreshToken: String,
    @SerializedName("tempToken") val tempToken: String?,
    @SerializedName("role") val role: String,
    @SerializedName("isNewUser") val isNewUser: Boolean
)

data class AuthTokenResponse(
    @SerializedName("email") val email: String,
    @SerializedName("nickname") val nickname: String,
    @SerializedName("accessToken") val accessToken: String,
    @SerializedName("refreshToken") val refreshToken: String,
    @SerializedName("tempToken") val tempToken: String?,
    @SerializedName("role") val role: String,
    @SerializedName("isNewUser") val isNewUser: Boolean
)
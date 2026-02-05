package com.example.pace.data.model.request

import com.google.gson.annotations.SerializedName

data class KakaoLoginRequest(
    @SerializedName("accessToken") val kakaoToken: String
)

data class ReissueRequest(
    @SerializedName("refreshToken") val refreshToken: String
)
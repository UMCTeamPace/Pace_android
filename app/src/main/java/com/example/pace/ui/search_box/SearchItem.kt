package com.example.pace.ui.search_box

import com.google.android.libraries.places.api.model.PhotoMetadata
import java.io.Serializable

data class SearchItem(
    val placeId: String,
    val name: String,
    val address: String,
    val distance: String,
    val category: String,
    val photoMetadata: PhotoMetadata? = null,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val openStatus: String = "정보없음"
) : Serializable

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
    val openStatus: String = "정보없음"
) : Serializable

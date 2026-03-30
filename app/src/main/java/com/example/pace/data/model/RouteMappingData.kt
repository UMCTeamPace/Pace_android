package com.example.pace.data.model

import android.content.Context
import android.view.View
import androidx.fragment.app.FragmentManager
import com.example.pace.data.model.response.RouteResponse

data class RouteMappingData(
    val context: Context,
    val bottomSheetView: View,
    val item: RouteResponse,
    val startName: String,
    val destination: String,
    val fragmentManager: FragmentManager
)

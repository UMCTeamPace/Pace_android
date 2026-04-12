package com.example.pace.ui.search_box

import androidx.fragment.app.Fragment
import com.example.pace.data.model.MyPlace
import com.example.pace.data.model.RecentHistoryItem
import com.example.pace.data.model.RecentRoute
import com.example.pace.data.model.response.RouteResponse
import com.google.android.gms.maps.model.LatLng

interface SearchHost {
    enum class BookmarkTarget {
        HOME,
        WORK
    }

    fun onRecommendItemClick(item: SearchItem)
    fun onHistoryItemClick(item: RecentHistoryItem)
    fun onRecentRouteClick(route: RecentRoute)
    fun onRouteDetailRequested(item: RouteResponse)
    fun onRouteSelected(item: RouteResponse)
    fun onEnterBookmarkMode()
    fun onSelectOnMapSelected()
    fun onMyPlaceClick(myPlace: MyPlace)
    fun onBookmarkSearchRequested(target: BookmarkTarget)
    fun onLocationSelected(name: String, placeId: String, isStart: Boolean)
    fun onScheduleLocationSelected(name: String, placeId: String)
    fun onUpdateMapFromDetail(name: String, placeId: String, lat: Double, lng: Double)
    fun onSetBottomSheetFixed(isFixed: Boolean)
    fun calculateDistance(dest: LatLng?): String
    fun onUpdateAddressFromMapCenter(latLng: LatLng)
    fun onSavedPlaceClick(placeId: String)
}

fun Fragment.findSearchHost(): SearchHost? {
    var current: Fragment? = this
    while (current != null) {
        if (current is SearchHost) return current
        current = current.parentFragment
    }
    return activity as? SearchHost
}

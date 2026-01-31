package com.example.pace.ui.search_box

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.pace.R
import com.example.pace.ui.main.MainActivity
import com.example.pace.ui.main.route.RouteFragment
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions

class MapFragment : Fragment(), OnMapReadyCallback {
    private var googleMap: GoogleMap? = null
    private val currentMarkers = mutableListOf<Marker>()
    var onMapTouched: (() -> Unit)? = null
    var onMarkerClicked: ((SearchItem) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_map, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mapFragment = childFragmentManager
            .findFragmentById(R.id.google_map_container) as SupportMapFragment?
        // 2. 지도 로딩 시작 (비동기)
        mapFragment?.getMapAsync(this)

        // 내 위치 버튼 클릭됐을 때
        view.findViewById<ImageButton>(R.id.btn_go_my_location).setOnClickListener {
            checkLocationPermission(isAnimate = true)
        }
    }

    // 3. 지도가 다 로딩되면 이 함수가 자동으로 호출됩니다.
    override fun onMapReady(map: GoogleMap) {
        this.googleMap = map
        map.uiSettings.isMapToolbarEnabled = false
        map.uiSettings.isMyLocationButtonEnabled = false

        map.setOnCameraIdleListener {
            val center = map.cameraPosition.target
            (parentFragment as? RouteFragment)?.updateAddressFromMapCenter(center)
        }

        map.setOnMapClickListener {
            onMapTouched?.invoke()
        }

        map.setOnCameraMoveStartedListener { reason ->
            if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                onMapTouched?.invoke()
            }
        }

        checkLocationPermission(isAnimate = false)
    }

    fun showMultipleMarkers(items: List<SearchItem>){
        val map = googleMap ?: return

        currentMarkers.forEach { it.remove() }
        currentMarkers.clear()

        if (items.isEmpty()) return

        val boundsBuilder = LatLngBounds.builder()
        var validCount = 0

        for (item in items) {
            if (item.lat != 0.0 && item.lng != 0.0) {
                val position = LatLng(item.lat, item.lng)

                val markerOptions = MarkerOptions()
                    .position(position)
                    .title(item.name)
                    .icon(bitmapDescriptorFromVector(requireContext(), R.drawable.ic_search_location_pin))

                val marker = map.addMarker(markerOptions)
                if (marker != null) currentMarkers.add(marker)

                // 범위를 늘림
                boundsBuilder.include(position)
                validCount++
            }

            if (validCount > 0) {
                val bounds = boundsBuilder.build()
                val padding = 200
                try {
                    map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding))
                } catch (e: Exception) {
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(items[0].lat, items[0].lng), 14f))
                }
            }
        }
    }

    fun moveCameraToSinglePosition(lat: Double, lng: Double) {
        val map = googleMap ?: return
        val position = LatLng(lat, lng)
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(position, 16f))
    }

    private fun bitmapDescriptorFromVector(context: Context, vectorResId: Int): BitmapDescriptor? {
        val vectorDrawable = ContextCompat.getDrawable(context, vectorResId) ?: return null
        vectorDrawable.setBounds(0, 0, vectorDrawable.intrinsicWidth, vectorDrawable.intrinsicHeight)
        val bitmap = Bitmap.createBitmap(vectorDrawable.intrinsicWidth, vectorDrawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        vectorDrawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    // 바텀 패딩 조절 (디테일 뷰 등에서 중심점 맞출 때 사용)
    fun setMapPadding(bottomPadding: Int) {
        googleMap?.setPadding(0, 0, 0, bottomPadding)
    }

    private fun checkLocationPermission(isAnimate: Boolean) {
        // 1. 권한이 있는지 확인
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            // 권한 있으면 -> 파란 점 켜고 이동
            enableMyLocationUI()
            moveToCurrentLocation(isAnimate)
        } else {
            (requireActivity() as? MainActivity)?.checkPermissionAndStart()
        }
    }

    //지도에 파란점 띄우기
    private fun enableMyLocationUI() {
        try {
            googleMap?.isMyLocationEnabled = true
        } catch (e: SecurityException) {
            return
        }
    }

    // 위치를 찾아서 카메라 옮기기
    private fun moveToCurrentLocation(isAnimate: Boolean) {
        val mainActivity = requireActivity() as? MainActivity
        val location = mainActivity?.myLocation

        if (location != null) {
            val targetLocation = LatLng(location.latitude, location.longitude)

            if (isAnimate) {
                // 버튼 클릭
                googleMap?.animateCamera(CameraUpdateFactory.newLatLng(targetLocation))
            } else {
                // 초기 로딩
                googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(targetLocation, 15f))
            }
        } else {
            mainActivity?.startLocationUpdates()

            val defaultLocation = LatLng(37.5665, 126.9780)
            googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 15f))
        }
    }

    fun clearMarkers() {
        currentMarkers.forEach { it.remove() }
        currentMarkers.clear()
    }

    fun updateButtonTranslation(offset: Float) {
        view?.findViewById<View>(R.id.btn_go_my_location)?.translationY = -offset
    }
}
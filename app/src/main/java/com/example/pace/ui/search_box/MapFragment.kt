package com.example.pace.ui.search_box

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.pace.R
import com.example.pace.data.model.response.RouteResponse
import com.example.pace.ui.main.MainActivity
import com.example.pace.ui.main.route.RouteFragment
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.maps.android.PolyUtil

class MapFragment : Fragment(), OnMapReadyCallback {
    private var googleMap: GoogleMap? = null
    private val currentMarkers = mutableListOf<Marker>()
    var onMapTouched: (() -> Unit)? = null

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

    fun clearRoute() {
        googleMap?.clear()
        setMapPadding(0)
    }

    fun drawRouteOnMap(routeItem: RouteResponse, finalStart: LatLng?, finalEnd: LatLng?) {
        val details = routeItem.routeDetails

        if (details.isNullOrEmpty()) {
            android.widget.Toast.makeText(requireContext(), "표시할 경로 데이터가 없습니다.", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        googleMap?.clear() // 기존 마커 및 선 초기화
        val boundsBuilder = LatLngBounds.Builder()

        // 2. [추가] 실제 출발지 마커 및 첫 번째 데이터 지점까지 연결
        if (finalStart != null) {
//            googleMap?.addMarker(MarkerOptions().position(finalStart).title("출발지"))
            boundsBuilder.include(finalStart)

            val firstDetail = details.first()
            val firstPoint = LatLng(firstDetail.startLat, firstDetail.startLng)

            // 실제 내 위치와 첫 번째 경로 시작점이 다를 경우 직선(점선) 연결
            if (finalStart != firstPoint && firstDetail.startLat != 0.0) {
                googleMap?.addPolyline(PolylineOptions()
                    .add(finalStart, firstPoint)
                    .color(Color.GRAY)
                    .width(10f)
                    .zIndex(10f)
                    .pattern(listOf(Dash(20f), Gap(10f))))
            }
        }

        // 3. 경로 리스트 순회 (도보 + 대중교통 모두 그리기)
        for (i in details.indices) {
            val detail = details[i]

            // 데이터 내의 모든 points를 디코딩 (도보 구간 포함)
            val decodedPoints = PolyUtil.decode(detail.points)
            if (decodedPoints.isEmpty()) continue

            if (detail.transitDetail != null) {
                // A. 대중교통 구간: 노선 색상으로 굵게 그리기
                val lineColor = try {
                    Color.parseColor(detail.transitDetail.lineColor ?: "#0000FF")
                } catch (e: Exception) {
                    Color.BLUE // 파싱 실패 시 기본 파랑
                }

                googleMap?.addPolyline(PolylineOptions()
                    .addAll(decodedPoints)
                    .color(lineColor)
                    .width(15f)
                    .zIndex(5f))
            } else {
                // B. 도보 구간 (transitDetail == null): 회색 점선으로 정밀하게 그리기
                googleMap?.addPolyline(PolylineOptions()
                    .addAll(decodedPoints)
                    .color(Color.GRAY)
                    .width(10f)
                    .zIndex(3f)
                    .pattern(listOf(Dash(20f), Gap(10f))))
            }

            // 경로에 포함된 모든 좌표를 카메라 범위에 포함
            decodedPoints.forEach { boundsBuilder.include(it) }

            // C. [연결] 현재 세그먼트의 끝점과 다음 세그먼트의 시작점 사이 공백 연결
            if (i < details.size - 1) {
                val currentEnd = LatLng(detail.endLat, detail.endLng)
                val nextStart = LatLng(details[i + 1].startLat, details[i + 1].startLng)

                if (currentEnd != nextStart && nextStart.latitude != 0.0) {
                    googleMap?.addPolyline(PolylineOptions()
                        .add(currentEnd, nextStart)
                        .color(Color.GRAY)
                        .width(10f)
                        .pattern(listOf(Dash(20f), Gap(10f))))
                }
            }
        }

        if (finalEnd != null) {
            val markerOptions = MarkerOptions()
                .position(finalEnd)
                .zIndex(20f)

            val icon = getResizedBitmapDescriptor(requireContext(), R.drawable.ic_my_location_pin, 48, 48)
            if (icon != null) {
                markerOptions.icon(icon)
            }

            googleMap?.addMarker(markerOptions)
            boundsBuilder.include(finalEnd)

            val lastDetail = details.last()
            val lastPoint = LatLng(lastDetail.endLat, lastDetail.endLng)

            if (lastPoint != finalEnd && lastDetail.endLat != 0.0) {
                googleMap?.addPolyline(PolylineOptions()
                    .add(lastPoint, finalEnd)
                    .color(Color.GRAY)
                    .width(10f)
                    .zIndex(10f)
                    .pattern(listOf(Dash(20f), Gap(10f))))
            }
        }

        // 5. 모든 경로가 보이도록 카메라 이동
        try {
            val bounds = boundsBuilder.build()
            googleMap?.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 150))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getResizedBitmapDescriptor(context: Context, vectorResId: Int, widthDp: Int, heightDp: Int): com.google.android.gms.maps.model.BitmapDescriptor? {
        try {
            val vectorDrawable = androidx.core.content.ContextCompat.getDrawable(context, vectorResId)
                ?: return null

            // DP를 픽셀(PX)로 변환
            val density = context.resources.displayMetrics.density
            val widthPx = (widthDp * density).toInt()
            val heightPx = (heightDp * density).toInt()

            // 0보다 작으면 기본값 방어 코드
            val w = if (widthPx > 0) widthPx else 100
            val h = if (heightPx > 0) heightPx else 100

            // 해당 크기로 비트맵 생성
            vectorDrawable.setBounds(0, 0, w, h)
            val bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            vectorDrawable.draw(canvas)

            return com.google.android.gms.maps.model.BitmapDescriptorFactory.fromBitmap(bitmap)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    fun showMultipleMarkers(items: List<SearchItem>, onMarkerClick: (SearchItem) -> Unit){
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
                if (marker != null) {
                    marker.tag = item
                    currentMarkers.add(marker)
                }

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

            map.setOnMarkerClickListener { clickedMarker ->
                val item = clickedMarker.tag as? SearchItem
                if (item != null) {
                    hideOtherMarkers(clickedMarker)
                    onMarkerClick(item)
                }

                true
            }
        }
    }

    fun showOnlySelectedMarker(selectedItem: SearchItem) {
        currentMarkers.forEach { marker ->
            val item = marker.tag as? SearchItem
            marker.isVisible = (item?.placeId == selectedItem.placeId)
        }
    }

    private fun hideOtherMarkers(selectedMarker: Marker) {
        currentMarkers.forEach { marker ->
            marker.isVisible = (marker == selectedMarker)
        }
    }

    fun restoreAllMarkers() {
        currentMarkers.forEach { it.isVisible = true }
    }

    fun moveCameraToSinglePosition(lat: Double, lng: Double) {
        val map = googleMap ?: return
        val position = LatLng(lat, lng)
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(position, 17.5f))
    }

    private fun bitmapDescriptorFromVector(context: Context, vectorResId: Int): com.google.android.gms.maps.model.BitmapDescriptor? {
        try {
            val vectorDrawable = androidx.core.content.ContextCompat.getDrawable(context, vectorResId)
                ?: return null // 리소스를 못 찾으면 null 반환

            // 크기가 0보다 작으면(정보가 없으면) 기본값 100으로 설정하여 튕김 방지
            val w = if (vectorDrawable.intrinsicWidth > 0) vectorDrawable.intrinsicWidth else 100
            val h = if (vectorDrawable.intrinsicHeight > 0) vectorDrawable.intrinsicHeight else 100

            vectorDrawable.setBounds(0, 0, w, h)
            val bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            vectorDrawable.draw(canvas)

            return com.google.android.gms.maps.model.BitmapDescriptorFactory.fromBitmap(bitmap)
        } catch (e: Exception) {
            e.printStackTrace() // 로그에 에러 출력
            return null // 에러 나면 아이콘 없이 진행
        }
    }

    fun initMapSelectionMode() {
        clearMarkers()

        setMapPadding(0)

        checkLocationPermission(isAnimate = true)
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

    fun setMyLocationButtonVisibility(isVisible: Boolean) {
        view?.findViewById<View>(R.id.btn_go_my_location)?.visibility =
            if (isVisible) View.VISIBLE else View.GONE
    }
    fun clearMarkers() {
        currentMarkers.forEach { it.remove() }
        currentMarkers.clear()
    }

    fun updateButtonTranslation(offset: Float) {
        view?.findViewById<View>(R.id.btn_go_my_location)?.translationY = -offset
    }
}
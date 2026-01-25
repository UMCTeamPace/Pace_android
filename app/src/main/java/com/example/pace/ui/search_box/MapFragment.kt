package com.example.pace.ui.search_box

import android.Manifest
import android.content.pm.PackageManager
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
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng

class MapFragment : Fragment(), OnMapReadyCallback {
    private var googleMap: GoogleMap? = null

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
        map.uiSettings.isMyLocationButtonEnabled = false

        checkLocationPermission(isAnimate = false)
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
            // 권한 없으면 -> 메인 액티비티한테 "팝업 좀 띄워줘" 요청
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
                // [버튼 클릭 시]
                googleMap?.animateCamera(CameraUpdateFactory.newLatLng(targetLocation))
            } else {
                // [초기 로딩 시]
                googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(targetLocation, 15f))
            }
        } else {
            mainActivity?.startLocationUpdates()

            val defaultLocation = LatLng(37.5665, 126.9780)
            googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 15f))
        }
    }

    fun updateButtonTranslation(offset: Float) {
        view?.findViewById<View>(R.id.btn_go_my_location)?.translationY = -offset
    }
}
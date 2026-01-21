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
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng

class MapFragment : Fragment(), OnMapReadyCallback {
    private var googleMap: GoogleMap? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // 권한 허용되면 내 위치로 이동
                enableMyLocation()
            }else {
                Toast.makeText(requireContext(), "위치 권한을 허용해야 내 위치를 찾을 수 있습니다.", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_map, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        val mapFragment = childFragmentManager
            .findFragmentById(R.id.google_map_container) as SupportMapFragment?

        // 2. 지도 로딩 시작 (비동기)
        mapFragment?.getMapAsync(this)

        view.findViewById<ImageButton>(R.id.btn_go_my_location).setOnClickListener {
            // 권한 체크 후 내 위치로 이동하는 함수 호출
            checkLocationPermission()
        }
    }

    // 3. 지도가 다 로딩되면 이 함수가 자동으로 호출됩니다.
    override fun onMapReady(map: GoogleMap) {
        this.googleMap = map
        map.uiSettings.isMyLocationButtonEnabled = false
        // 2. 맵이 준비되면 권한 체크 후 내 위치 활성화
        checkLocationPermission()
    }

    private fun checkLocationPermission() {
        // 이미 권한이 있는지 확인
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            enableMyLocation()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun enableMyLocation() {
        // 1. 구글맵에 '내 위치 버튼(파란 점)' 활성화 (빨간 줄 떠도 무시하거나 Alt+Enter로 Permission check 추가)
        try {
            googleMap?.isMyLocationEnabled = true
        } catch (e: SecurityException) {
            return
        }

        // 2. 현재 위치 좌표를 얻어와서 카메라 이동
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val currentLatLng = LatLng(location.latitude, location.longitude)
                    googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
                } else {
                    // 위치를 못 가져오면 기본값(서울 시청)으로 이동
                    val defaultLocation = LatLng(37.5665, 126.9780)
                    googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 15f))
                }
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }
}
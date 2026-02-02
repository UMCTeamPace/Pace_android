package com.example.pace.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.MenuItem
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.pace.R
import com.example.pace.databinding.ActivityMainBinding
import com.example.pace.ui.main.calendar.CalendarFragment
import com.example.pace.ui.main.home.HomeFragment
import com.example.pace.ui.main.route.RouteFragment
import com.example.pace.ui.search_box.*
import com.example.pace.ui.settings.SettingsActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.bottomsheet.BottomSheetBehavior
import androidx.activity.viewModels
import com.example.pace.PaceApplication
import com.example.pace.data.db.ScheduleDatabase
import com.example.pace.data.datasource.NormalScheduleRemoteDataSource
import com.example.pace.data.repository.ScheduleRepository
import com.example.pace.ui.main.calendar.ScheduleViewModel
import com.example.pace.ui.main.calendar.ScheduleViewModelFactory

class MainActivity : AppCompatActivity() {
    lateinit var binding: ActivityMainBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // ViewModel injection
    private val repository by lazy { (application as PaceApplication).repository }
    private val viewModel: ScheduleViewModel by viewModels {
        ScheduleViewModelFactory((application as PaceApplication).repository)
    }

    fun getSharedViewModel(): ScheduleViewModel = viewModel

    // 내 위치 저장
    var myLocation: android.location.Location? = null

    // 실시간 위치 갱신 콜백
    private lateinit var locationCallback: LocationCallback

    // 위치 권한 요청 런처
    val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startLocationUpdates() // 허용하면 위치 갱신 시작
        }
    }

    //달력 권한 요청 런처
    private val calendarPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        Log.d("MainActivity", "onCreate3: checkCalendarPermissions()")
        val readGranted = permissions[Manifest.permission.READ_CALENDAR] ?: false
        val writeGranted = permissions[Manifest.permission.WRITE_CALENDAR] ?: false
        if (!readGranted || !writeGranted) {
            // Handle the case where permissions are not granted, maybe show a toast or a dialog.
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkCalendarPermissions()
        Log.d("MainActivity", "onCreate1: checkCalendarPermissions()")

        // 1. 초기화 (위치, Places API, 바텀시트)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                myLocation = locationResult.locations.lastOrNull()
            }
        }

        // 2. 초기 화면 설정 (Home)
        supportFragmentManager.beginTransaction().replace(R.id.main_fcv, HomeFragment()).commit()

        // 초기 툴바 상태 설정 (Home 기준)
        binding.mainLogoIv.visibility = View.VISIBLE
        binding.mainSettingsIv.visibility = View.VISIBLE
        binding.scheduleTitleTv.visibility = View.GONE
        binding.scheduleEditIv.visibility = View.GONE
        binding.scheduleAddIv.visibility = View.GONE
        binding.scheduleSearchIv.visibility = View.GONE
        binding.mainBackIv.visibility = View.GONE
        binding.mainSearchLl.visibility = View.GONE

        // 3. 바텀 네비게이션 리스너 설정
        binding.mainBnv.itemIconTintList = null
        binding.mainBnv.setOnItemSelectedListener { item ->
            changeFragment(item)
        }

        // 4. 설정 버튼 이동
        binding.mainSettingsIv.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun checkCalendarPermissions() {
        Log.d("MainActivity", "onCreate2: checkCalendarPermissions()")
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            calendarPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_CALENDAR,
                    Manifest.permission.WRITE_CALENDAR
                )
            )
        }
    }

    // ★ [위치] 화면이 보일 때 업데이트 재개
    override fun onResume() {
        super.onResume()
        // Resume location updates
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates()
        }
        // Refresh schedule data
        Log.d("D", "뷰모델 리프레쉬 시점")
        viewModel.refreshSchedules()
    }

    // ★ [위치] 화면이 안 보일 때 배터리 절약을 위해 중지
    override fun onPause() {
        super.onPause()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    // ★ [위치] 권한 체크 및 업데이트 시작 요청 (MapFragment 등에서 호출)
    fun checkPermissionAndStart() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates()
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // ★ [위치] 실제 업데이트 시작 함수
    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        // 10초마다, 혹은 10m 이동 시 갱신
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setMinUpdateDistanceMeters(2f)
            .build()
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }


    // 탭 전환 로직
    private fun changeFragment(item: MenuItem): Boolean {
        binding.searchEt.clearFocus()

        when (item.itemId) {
            R.id.home -> {
                supportFragmentManager.beginTransaction().replace(
                    R.id.main_fcv,
                    HomeFragment()
                ).commit()
                binding.mainLogoIv.visibility = View.VISIBLE
                binding.mainSettingsIv.visibility = View.VISIBLE
                binding.scheduleTitleTv.visibility = View.GONE
                binding.scheduleEditIv.visibility = View.GONE
                binding.scheduleSearchIv.visibility = View.GONE
                binding.scheduleAddIv.visibility = View.GONE
                binding.mainBackIv.visibility = View.GONE
                binding.mainSearchLl.visibility = View.GONE
                return true
            }

            R.id.calendar -> {
                supportFragmentManager.beginTransaction().replace(
                    R.id.main_fcv,
                    CalendarFragment()
                ).commit()
                binding.mainLogoIv.visibility = android.view.View.GONE
                binding.mainSettingsIv.visibility = android.view.View.GONE
                binding.scheduleEditIv.visibility = android.view.View.VISIBLE
                binding.scheduleTitleTv.visibility = android.view.View.VISIBLE
                binding.scheduleSearchIv.visibility = android.view.View.VISIBLE
                binding.scheduleAddIv.visibility = View.VISIBLE
                binding.mainBackIv.visibility = android.view.View.GONE
                binding.mainSearchLl.visibility = android.view.View.GONE
                return true
            }

            R.id.route -> {
                supportFragmentManager.beginTransaction().replace(
                    R.id.main_fcv,
                    RouteFragment()
                ).commit()
                binding.mainLogoIv.visibility = android.view.View.GONE
                binding.mainSettingsIv.visibility = android.view.View.GONE
                binding.scheduleTitleTv.visibility = android.view.View.GONE
                binding.scheduleEditIv.visibility = android.view.View.GONE
                binding.scheduleSearchIv.visibility = android.view.View.GONE
                binding.scheduleAddIv.visibility = View.GONE
                binding.mainBackIv.visibility = android.view.View.GONE
                binding.mainSearchLl.visibility = android.view.View.VISIBLE
                return true
            }
            else -> return false
        }
    }
}
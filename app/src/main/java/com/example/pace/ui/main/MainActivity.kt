package com.example.pace.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.MenuItem
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.example.pace.R
import com.example.pace.databinding.ActivityMainBinding
import com.example.pace.ui.main.calendar.CalendarFragment
import com.example.pace.ui.main.calendar.SearchFragment
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.pace.AlertActivity
import com.example.pace.PaceApplication
import com.example.pace.data.datasource.AuthDataStore
import com.example.pace.data.db.ScheduleDatabase
import com.example.pace.data.datasource.NormalScheduleRemoteDataSource
import com.example.pace.data.repository.repository.SettingsRepository
import com.example.pace.data.util.syncMemberSettingsIfNeeded
import com.example.pace.data.viewmodel.ScheduleViewModel
import com.example.pace.data.repository.repository.ScheduleRepository
import com.example.pace.data.viewmodel.TransitViewModel
import dagger.hilt.android.AndroidEntryPoint // 추가
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @javax.inject.Inject
    lateinit var authDataStore: AuthDataStore

    @javax.inject.Inject
    lateinit var settingsRepository: SettingsRepository

    lateinit var binding: ActivityMainBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val viewModel: ScheduleViewModel by viewModels()
    private val transitViewModel: TransitViewModel by viewModels()
    // ViewModel injection


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
        if (readGranted && writeGranted) {
            // 권한이 허용된 "직후"에 데이터를 새로고침하여 튕김 방지 및 데이터 표시
            Log.d("MainActivity", "권한 허용됨: 데이터 리프레쉬 시작")
            viewModel.refreshSchedules()
        } else {
            // 필수 권한이 없으면 앱 이용이 어려우므로 토스트를 띄우거나 온보딩으로 재유도 가능
            Log.d("MainActivity", "달력 권한 거부됨")
        }
    }

    private lateinit var spf: SharedPreferences
    private var currentBottomMenuItem = R.id.home

    companion object {
        private const val TAG_HOME = "main_home"
        private const val TAG_CALENDAR = "main_calendar"
        private const val TAG_ROUTE = "main_route"
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createNotificationChannel()

        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        spf = getSharedPreferences("HOME_CALENDAR", MODE_PRIVATE)

//        // 테스트를 위해 바로 AlertActivity 실행!
//        val intent = Intent(this, AlertActivity::class.java)
//        intent.putExtra("MINUTES_LEFT", 15) // 테스트하고 싶은 시간(분)을 넣어보세요
//        startActivity(intent)

        // 1. 초기화 (위치, Places API, 바텀시트)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        spf.edit().remove("SELECTED_DATE").apply()

        checkCalendarPermissions()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                myLocation = locationResult.locations.lastOrNull()
            }
        }

        // 2. 초기 화면 설정 (Home)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .add(R.id.main_fcv, HomeFragment(), TAG_HOME)
                .commit()
            currentBottomMenuItem = R.id.home
        }

        // 초기 툴바 상태 설정 (Home 기준)
        binding.mainLogoIv.visibility = View.VISIBLE
        binding.mainSettingsIv.visibility = View.VISIBLE
        binding.scheduleTitleTv.visibility = View.GONE
        binding.scheduleActionContainer.visibility = View.GONE
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
        binding.mainBnv.setOnItemReselectedListener { }

        // 4. 설정 버튼 이동
        binding.mainSettingsIv.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        handleIntent(intent)
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

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            syncMemberSettingsIfNeeded(
                authDataStore = authDataStore,
                settingsRepository = settingsRepository,
                source = "MainActivity",
                force = false
            )
        }

        // Resume location updates
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates()
        }
        // [수정] 캘린더 권한이 있을 때만 새로고침 호출
        val isCalendarAllowed = androidx.core.content.ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.READ_CALENDAR
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (isCalendarAllowed) {
            android.util.Log.d("MainActivity", "onResume: 권한 확인됨, 데이터 리프레쉬 실행")
            viewModel.refreshSchedules()
        } else {
            android.util.Log.d("MainActivity", "onResume: 여전히 권한 없음, 스킵")
        }

    }

    override fun onPause() {
        super.onPause()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    fun checkPermissionAndStart() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates()
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        // 2초마다, 혹은 2m 이동 시 갱신
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000)
            .setMinUpdateDistanceMeters(2f)
            .build()
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    private fun handleIntent(intent: Intent?) {
        val actionMode = intent?.getStringExtra("ACTION_MODE")

        if (actionMode == "SCHEDULE" || actionMode == "SCHEDULE_ROUTE" || actionMode == "ROUTE_RESEARCH") {
            showRouteTab(resetIfNeeded = false)
            (supportFragmentManager.findFragmentByTag(TAG_ROUTE) as? RouteFragment)
                ?.consumeActionModeIntent(intent)
        }
    }


    // 탭 전환 로직
    private fun changeFragment(item: MenuItem): Boolean {
        binding.searchEt.clearFocus()

        if (item.itemId == currentBottomMenuItem) {
            return true
        }

        when (item.itemId) {
            R.id.home -> {
                switchFragment(TAG_HOME) { HomeFragment() }
                supportFragmentManager.executePendingTransactions()
                (supportFragmentManager.findFragmentByTag(TAG_HOME) as? HomeFragment)?.resetToToday()
                currentBottomMenuItem = R.id.home
                binding.mainLogoIv.visibility = View.VISIBLE
                binding.mainSettingsIv.visibility = View.VISIBLE
                binding.scheduleTitleTv.visibility = View.GONE
                binding.scheduleActionContainer.visibility = View.GONE
                binding.scheduleEditIv.visibility = View.GONE
                binding.scheduleSearchIv.visibility = View.GONE
                binding.scheduleAddIv.visibility = View.GONE
                binding.mainBackIv.visibility = View.GONE
                binding.mainSearchLl.visibility = View.GONE
                return true
            }

            R.id.calendar -> {
                val skipCalendarReset = supportFragmentManager.findFragmentById(R.id.main_fcv) is SearchFragment
                switchFragment(TAG_CALENDAR) { CalendarFragment() }
                supportFragmentManager.executePendingTransactions()
                if (!skipCalendarReset) {
                    (supportFragmentManager.findFragmentByTag(TAG_CALENDAR) as? CalendarFragment)?.resetToTodayState()
                }
                currentBottomMenuItem = R.id.calendar
                binding.mainLogoIv.visibility = android.view.View.GONE
                binding.mainSettingsIv.visibility = android.view.View.GONE
                binding.scheduleActionContainer.visibility = View.VISIBLE
                binding.scheduleEditIv.visibility = android.view.View.GONE
                binding.scheduleTitleTv.visibility = android.view.View.VISIBLE
                binding.scheduleSearchIv.visibility = android.view.View.VISIBLE
                binding.scheduleAddIv.visibility = View.VISIBLE
                binding.mainBackIv.visibility = android.view.View.GONE
                binding.mainSearchLl.visibility = android.view.View.GONE
                return true
            }

            R.id.route -> {
                showRouteTab(resetIfNeeded = true)
                return true
            }
            else -> return false
        }
    }

    private fun showRouteTab(resetIfNeeded: Boolean) {
        switchFragment(TAG_ROUTE) { RouteFragment() }
        supportFragmentManager.executePendingTransactions()
        val routeFragment = supportFragmentManager.findFragmentByTag(TAG_ROUTE) as? RouteFragment
        if (resetIfNeeded && routeFragment?.isInScheduleSelectionMode() != true) {
            routeFragment?.resetToCurrentLocationState()
        }
        currentBottomMenuItem = R.id.route
        binding.mainBnv.menu.findItem(R.id.route)?.isChecked = true
        binding.mainLogoIv.visibility = android.view.View.GONE
        binding.mainSettingsIv.visibility = android.view.View.GONE
        binding.scheduleTitleTv.visibility = android.view.View.GONE
        binding.scheduleActionContainer.visibility = View.GONE
        binding.scheduleEditIv.visibility = android.view.View.GONE
        binding.scheduleSearchIv.visibility = android.view.View.GONE
        binding.scheduleAddIv.visibility = android.view.View.GONE
        binding.mainBackIv.visibility = android.view.View.GONE
        binding.mainSearchLl.visibility = android.view.View.VISIBLE
    }
    // 같은 프래그먼트 선택 시 새로고침
    private fun switchFragment(tag: String, createFragment: () -> Fragment) {
        val fragmentManager = supportFragmentManager
        val targetFragment = fragmentManager.findFragmentByTag(tag) ?: createFragment()
        val currentFragment = fragmentManager.fragments.firstOrNull { fragment ->
            fragment.id == R.id.main_fcv && fragment.isAdded && !fragment.isHidden
        }

        if (currentFragment === targetFragment) return

        if (targetFragment is CalendarFragment && targetFragment.isAdded) {
            targetFragment.showCalendarTab()
        }

        fragmentManager.beginTransaction().apply {
            currentFragment?.let { hide(it) }

            if (targetFragment.isAdded) {
                show(targetFragment)
            } else {
                add(R.id.main_fcv, targetFragment, tag)
            }
        }.commit()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "pace_alert_channel" // 리시버와 반드시 똑같아야 함
            val channel = NotificationChannel(
                channelId,
                "Pace 알람",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "외출 준비 알람 전용 채널입니다."
                // 잠금화면에서도 보이게 설정
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null)
                enableVibration(false)
            }

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}

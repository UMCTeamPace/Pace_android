package com.example.pace.ui.main

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.pace.R
import com.example.pace.databinding.ActivityMainBinding
import com.example.pace.ui.main.calendar.CalendarFragment
import com.example.pace.ui.main.home.HomeFragment
import com.example.pace.ui.main.route.RouteFragment
import com.example.pace.ui.settings.SettingsActivity
import com.example.pace.ui.search_box.SearchHistoryFragment
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.net.PlacesClient
import com.example.pace.BuildConfig
import com.example.pace.ui.search_box.SearchItem
import com.example.pace.ui.search_box.SearchRecommendFragment
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class MainActivity : AppCompatActivity() {
    lateinit var binding: ActivityMainBinding

    // 장소 검색이랑 위도경도 클라이언트
    private lateinit var placesClient: PlacesClient
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val historyFragment = SearchHistoryFragment()
    private val recommendFragment = SearchRecommendFragment()

    // 디바운싱
    private var searchJob: Job? = null

    // 구글 API 요금 절약을 위한 세션 토큰
    // (검색 시작 ~ 결과 클릭까지를 1회 과금으로 묶어줌)
    private var sessionToken: AutocompleteSessionToken? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 위치 서비스 및 Places API 초기화
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        initPlacesClient()


        // 프래그먼트 이동
        supportFragmentManager.beginTransaction().replace(R.id.main_fcv, HomeFragment()).commit()
        binding.mainBnv.itemIconTintList = null
        binding.mainBnv.setOnItemSelectedListener { item ->
            changeFragment(item)
        }

        // 처음 툴바 보이는거 설정
        binding.mainLogoIv.visibility = android.view.View.VISIBLE
        binding.mainTitleTv.visibility = android.view.View.GONE
        binding.mainSettingsIv.visibility = android.view.View.VISIBLE
        binding.mainEditIv.visibility = android.view.View.VISIBLE
        binding.mainSearchIv.visibility = android.view.View.GONE
        binding.mainBackIv.visibility = android.view.View.GONE
        binding.mainSearchLl.visibility = android.view.View.GONE

        // 설정 이동
        binding.mainSettingsIv.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // 검색하다가 뒤로가기 누르면 키보드 없애고, 또 눌렀을 때 routeFragment로 이동
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (supportFragmentManager.backStackEntryCount > 0) {
                    binding.searchEt.clearFocus()
                    hideKeyboard()
                    supportFragmentManager.popBackStack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })

        // 검색창 눌렸을 때 프래그먼트 이동
        binding.searchEt.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                // 백스택 없다면 SearchHistroyFragment 띄우기
                if (supportFragmentManager.backStackEntryCount == 0) {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.main_fcv, SearchHistoryFragment())
                        .addToBackStack("SEARCH_MODE")
                        .commit()

                    binding.mainBnv.visibility = android.view.View.GONE
                    binding.mainBackIv.visibility = android.view.View.VISIBLE
                }

            }
        }

        // 뒤로가기 버튼
        binding.mainBackIv.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // 시스템 뒤로가기
        supportFragmentManager.addOnBackStackChangedListener {
            if (supportFragmentManager.backStackEntryCount == 0) {
                binding.mainBnv.visibility = android.view.View.VISIBLE

                binding.searchEt.clearFocus()
                binding.searchEt.setText("")
                hideKeyboard()

                binding.mainBackIv.visibility = android.view.View.GONE
            }
        }

        // 텍스트 창에 아이콘 바꾸기
        binding.searchEt.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: android.text.Editable?) {
                if (s.isNullOrEmpty()) {
                    binding.btnSearch.setImageResource(R.drawable.ic_search)
                } else {
                    binding.btnSearch.setImageResource(R.drawable.ic_close)
                }
            }
        })

        binding.btnSearch.setOnClickListener {
            if (binding.searchEt.text.isNotEmpty()) {
                binding.searchEt.setText("")
            }
        }

        setupSearchTextWatcher()
    }

    private fun setupSearchTextWatcher() {
        binding.searchEt.addTextChangedListener(object : TextWatcher{
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?){
                val query = s.toString().trim()

                // 검색 예약 취소
                searchJob?.cancel()

                // 돋보기 or X ui 변경
                if (query.isEmpty()) {
                    binding.btnSearch.setImageResource(R.drawable.ic_search)

                    if (supportFragmentManager.backStackEntryCount > 0) {
                        showSearchFragment(historyFragment)
                    }
                    return
                } else {
                    binding.btnSearch.setImageResource(R.drawable.ic_close)

                    if (supportFragmentManager.backStackEntryCount > 0) {
                        showSearchFragment(recommendFragment)
                    }
                }

                // 디바인싱 사용하며 API 호출
                searchJob = lifecycleScope.launch {
                    delay(500L)
                    searchPlaces(query)
                }
            }
        })
    }

    private fun showSearchFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_fcv, fragment)
            .commitAllowingStateLoss()
    }


    private fun initPlacesClient() {
        // API 키 초기화
        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, BuildConfig.GOOGLE_API_KEY)
        }
        // 클라이언트 객체 생성
        placesClient = Places.createClient(this)
    }

    // 실제 검색 함수
    private fun searchPlaces(query: String) {
        // 토큰이 없으면 새로 발급 (검색 시작!)
        if (sessionToken == null) {
            sessionToken = AutocompleteSessionToken.newInstance()
        }

        // 위치 권한 확인
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // 권한 없으면 서울시청 기준
            requestSearch(query, LatLng(37.5665, 126.9780))
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            val origin = if (location != null) {
                LatLng(location.latitude, location.longitude)
            } else {
                LatLng(37.5665, 126.9780)
            }
            requestSearch(query, origin)
        }
    }

    @Suppress("DEPRECATION")
    private fun requestSearch(query: String, origin: LatLng) {
        val southWest = LatLng(origin.latitude - 0.05, origin.longitude - 0.05)
        val northEast = LatLng(origin.latitude + 0.05, origin.longitude + 0.05)
        val bounds = com.google.android.libraries.places.api.model.RectangularBounds.newInstance(southWest, northEast)
        val request = FindAutocompletePredictionsRequest.builder()
            .setSessionToken(sessionToken)
            .setQuery(query)
            .setCountries("KR")
            .setOrigin(origin)
            .setLocationBias(bounds)
//            .setTypesFilter(listOf("establishment"))
            .build()

        placesClient.findAutocompletePredictions(request)
            .addOnSuccessListener { response ->
                val resultList = ArrayList<SearchItem>()

                for (prediction in response.autocompletePredictions) {
                    val name = prediction.getPrimaryText(null).toString()
                    var rawAddress = prediction.getSecondaryText(null).toString()
                    val address = rawAddress
                        .replace("대한민국 ", "")
                        .replace("서울특별시", "서울")
                        .trim()

                    val distMeters = prediction.distanceMeters
                    val distStr = if (distMeters != null) String.format("%.1fkm", distMeters / 1000.0) else ""

                    val rawTypes = prediction.placeTypes.map { it.toString().lowercase() }
                    val category = convertTypeToKorean(rawTypes)

                    resultList.add(SearchItem(prediction.placeId, name, address, distStr, category))
                }

                // 결과 화면에 데이터 전달
                if (recommendFragment.isAdded && recommendFragment.isVisible) {
                    recommendFragment.updateList(resultList)
                }
            }
            .addOnFailureListener { exception ->
                exception.printStackTrace()
            }
    }

    // 텍스트 창 바깥 클릭됐을 때 키보드 숨기고 포커스 해제
    override fun dispatchTouchEvent(ev: android.view.MotionEvent?): Boolean {
        if (ev?.action == android.view.MotionEvent.ACTION_DOWN) {
            val v = currentFocus
            if (v is android.widget.EditText) {
                val searchBoxRect = android.graphics.Rect()
                binding.mainSearchLl.getGlobalVisibleRect(searchBoxRect)

                // 터치한 위치가 텍스트 창 밖인지 확인
                if (!searchBoxRect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                    v.clearFocus()
                    hideKeyboard()
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun changeFragment(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.home -> {
                supportFragmentManager.beginTransaction().replace(
                    R.id.main_fcv,
                    HomeFragment()
                ).commit()
                binding.mainLogoIv.visibility = android.view.View.VISIBLE
                binding.mainTitleTv.visibility = android.view.View.GONE
                binding.mainSettingsIv.visibility = android.view.View.VISIBLE
                binding.mainEditIv.visibility = android.view.View.VISIBLE
                binding.mainSearchIv.visibility = android.view.View.GONE
                binding.mainBackIv.visibility = android.view.View.GONE
                binding.mainSearchLl.visibility = android.view.View.GONE
                return true
            }

            R.id.calendar -> {
                supportFragmentManager.beginTransaction().replace(
                    R.id.main_fcv,
                    CalendarFragment()
                ).commit()
                binding.mainLogoIv.visibility = android.view.View.GONE
                binding.mainTitleTv.visibility = android.view.View.VISIBLE
                binding.mainSettingsIv.visibility = android.view.View.GONE
                binding.mainEditIv.visibility = android.view.View.GONE
                binding.mainSearchIv.visibility = android.view.View.VISIBLE
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
                binding.mainTitleTv.visibility = android.view.View.GONE
                binding.mainSettingsIv.visibility = android.view.View.GONE
                binding.mainEditIv.visibility = android.view.View.GONE
                binding.mainSearchIv.visibility = android.view.View.GONE
                binding.mainBackIv.visibility = android.view.View.GONE
                binding.mainSearchLl.visibility = android.view.View.VISIBLE
                return true
            }

            else -> return false
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.searchEt.windowToken, 0)
    }

    private fun convertTypeToKorean(types: List<String>): String {
        if (types.isEmpty()) return "장소" // 정보 없으면 기본값

        // 구글은 타입을 여러 개 주는데(예: [RESTAURANT, FOOD, ESTABLISHMENT]),
        // 그중에 우리가 원하는 핵심 키워드가 있는지 찾아봅니다.

        return when {
            types.contains("subway_station") -> "지하철역"
            types.contains("transit_station") -> "교통/역"
            types.contains("bus_station") -> "버스정류장"
            types.contains("train_station") -> "기차역"

            types.contains("restaurant") || types.contains("food") -> "음식점"
            types.contains("cafe") -> "카페"
            types.contains("bakery") -> "제과점"
            types.contains("bar") -> "술집"

            types.contains("store") || types.contains("shopping_mall") -> "상점/쇼핑"
            types.contains("clothing_store") -> "의류"
            types.contains("convenience_store") -> "편의점"

            types.contains("lodging") || types.contains("hotel") -> "숙박"

            types.contains("hospital") -> "병원"
            types.contains("pharmacy") -> "약국"

            types.contains("school") -> "학교"
            types.contains("university") -> "대학교"

            types.contains("gym") || types.contains("health") -> "운동/헬스"

            types.contains("bank") || types.contains("atm") -> "은행/ATM"

            types.contains("park") -> "공원"

            else -> "기타장소"
        }
    }
}
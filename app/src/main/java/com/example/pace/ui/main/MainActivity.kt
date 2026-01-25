package com.example.pace.ui.main

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.pace.BuildConfig
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
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.material.bottomsheet.BottomSheetBehavior
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.ArrayList

class MainActivity : AppCompatActivity() {
    lateinit var binding: ActivityMainBinding

    // 장소 검색 클라이언트 및 위치 서비스
    private lateinit var placesClient: PlacesClient
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    // 내 위치 저장
    var myLocation: android.location.Location? = null

    // 실시간 위치 갱신 콜백
    private lateinit var locationCallback: LocationCallback

    // 프래그먼트 인스턴스 유지
    private val historyFragment = SearchHistoryFragment()
    private val recommendFragment = SearchRecommendFragment()

    // 바텀시트 Behavior
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>

    private var isDetailFromRecommend = false

    // 디바운싱 작업
    private var searchJob: Job? = null

    // 구글 API 세션 토큰 (과금 최적화)
    private var sessionToken: AutocompleteSessionToken? = null

    // 위치 권한 요청 런처
    val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startLocationUpdates() // 허용하면 위치 갱신 시작
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. 초기화 (위치, Places API, 바텀시트)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                myLocation = locationResult.locations.lastOrNull()
            }
        }
        initPlacesClient()
        initBottomSheet()

        // 2. 초기 화면 설정 (Home)
        supportFragmentManager.beginTransaction().replace(R.id.main_fcv, HomeFragment()).commit()

        // 초기 툴바 상태 설정 (Home 기준)
        binding.mainLogoIv.visibility = View.VISIBLE
        binding.mainTitleTv.visibility = View.GONE
        binding.mainSettingsIv.visibility = View.VISIBLE
        binding.mainEditIv.visibility = View.VISIBLE
        binding.mainSearchIv.visibility = View.GONE
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

        // 5. 시스템 뒤로가기 로직 등록
        setupOnBackPressed()

        // 6. 상단 커스텀 뒤로가기 버튼 로직
        binding.mainBackIv.setOnClickListener {
            handleCustomBackClick()
        }

        // 7. 검색창 포커스 리스너 (검색 모드 진입)
        binding.searchEt.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                enterSearchMode()
            }
        }

        // 8. 텍스트 변경 리스너 (X 버튼, 자동완성 호출)
        setupSearchTextWatcher()

        // 9. 키보드 '완료' 버튼 리스너 (최종 검색)
        setupEditorActionListener()

        // 10. 검색창 X 버튼 (입력 초기화)
        binding.btnSearch.setOnClickListener {
            if (binding.searchEt.text.isNotEmpty()) {
                binding.searchEt.setText("")
            }
        }
    }

    // ★ [위치] 화면이 보일 때 업데이트 재개
    override fun onResume() {
        super.onResume()
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates()
        }
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

    // ===============================================================
    // ★ 1. 뒤로가기 / 네비게이션 로직
    // ===============================================================

    /**
     * 시스템 뒤로가기 버튼 눌렀을 때의 단계별 로직
     */
    private fun setupOnBackPressed() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // [1] 상세 화면(Detail)이 떠 있다면 -> 리스트로 복구
                val currentSheetFragment = supportFragmentManager.findFragmentById(R.id.bottom_sheet_container)
                if (currentSheetFragment is LocationDetailFragment) {
                    supportFragmentManager.popBackStack() // 일단 상세화면 닫기

                    // ★ [핵심 수정] 어디서 왔니?
                    if (isDetailFromRecommend) {
                        // case A: 추천 검색어에서 왔음 -> "다시 검색하러 가자"
                        bottomSheetBehavior.isHideable = true
                        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

                        // 텍스트가 남아있으면 Recommend가, 없으면 History가 자동으로 뜸
                        enterSearchMode()
                    } else {
                        // case B: 리스트 목록에서 왔음 -> "리스트 목록 보여주자"
                        bottomSheetBehavior.isFitToContents = false
                        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
                    }
                    return
                }

                // [2] 바텀시트가 화면에 보이는가? (함수로 확인)
                if (isBottomSheetVisible()) {
                    // 2-1. 접혀있는 상태라면 -> 검색 모드로 복귀
                    if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
                        bottomSheetBehavior.isHideable = true
                        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

                        binding.searchEt.setText("")
                        enterSearchMode()
                    }
                    // 2-2. 펼쳐져 있거나 중간 상태라면 -> 접기
                    else {
                        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                    }
                    return
                }

                // [3] 검색 모드(History/Recommend) 상태일 때 -> 지도 화면으로
                if (isSearchMode()) {
                    exitSearchMode()
                    return
                }

                // [4] 그 외 -> 앱 종료
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        })
    }

    /**
     * 상단 커스텀 뒤로가기(←) 버튼 눌렀을 때의 로직
     */
    private fun handleCustomBackClick() {
        val isKeyboardUp = binding.searchEt.hasFocus() // 포커스로 키보드 상태 추정

        // [CASE 1] 결과 화면(지도+바텀시트)에서 눌렀을 때 -> "재검색 하러 가기"
        if (isBottomSheetVisible()) {
            bottomSheetBehavior.isHideable = true
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

            // 상세화면이었다면 스택 정리
            val currentSheetFragment = supportFragmentManager.findFragmentById(R.id.bottom_sheet_container)
            if (currentSheetFragment is LocationDetailFragment) {
                supportFragmentManager.popBackStack()
            }

            // 검색창 비우고 검색 모드 재진입
            binding.searchEt.setText("")
            enterSearchMode()
            return
        }

        // [CASE 2] 검색 모드(History/Recommend)일 때
        if (isSearchMode()) {
            if (isKeyboardUp) {
                // 키보드가 떠있으면 -> 키보드만 내리고 포커스 해제
                hideKeyboard()
                binding.searchEt.clearFocus()
            } else {
                // 키보드가 없으면 -> 검색 취소하고 지도(Route)로 돌아감
                exitSearchMode()
            }
        }
    }

    // ===============================================================
    // ★ 2. 상태 전환 헬퍼 함수들
    // ===============================================================

    // 검색 모드 진입 (History/Recommend 분기 처리 + 키보드 올리기)
    private fun enterSearchMode() {
        // UI 설정
        binding.mainBackIv.visibility = View.VISIBLE
        binding.mainBnv.visibility = View.GONE

        // 시트 숨김 보장
        if (::bottomSheetBehavior.isInitialized) {
            bottomSheetBehavior.isHideable = true
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }

        // 상세화면이 있다면 닫기
        val currentSheetFragment = supportFragmentManager.findFragmentById(R.id.bottom_sheet_container)
        if (currentSheetFragment is LocationDetailFragment) {
            supportFragmentManager.popBackStack()
        }

        // ★ [수정됨] 텍스트 유무에 따라 보여줄 프래그먼트 결정
        val query = binding.searchEt.text.toString().trim()
        if (query.isNotEmpty()) {
            // 글자가 있으면 추천 검색어 화면
            showSearchFragment(recommendFragment)
        } else {
            // 비어 있으면 검색 기록 화면
            showSearchFragment(historyFragment)
        }

        // 키보드 올리기
        binding.searchEt.requestFocus()
        showKeyBoard()
    }

    // 검색 모드 탈출 (초기 Route 화면으로 복귀)
    private fun exitSearchMode() {
        hideKeyboard()
        binding.searchEt.clearFocus()
        binding.searchEt.setText("") // 텍스트 초기화

        // 검색 관련 프래그먼트 숨기기
        val transaction = supportFragmentManager.beginTransaction()
        if (historyFragment.isAdded) transaction.hide(historyFragment)
        if (recommendFragment.isAdded) transaction.hide(recommendFragment)

        // 지도 프래그먼트 다시 띄우기
        for (fragment in supportFragmentManager.fragments) {
            if (fragment != historyFragment && fragment != recommendFragment) {
                transaction.show(fragment)
            }
        }

        transaction.commitAllowingStateLoss()

        // UI 원상복구
        binding.mainBackIv.visibility = View.GONE
        binding.mainBnv.visibility = View.VISIBLE
    }

    // ===============================================================
    // ★ 3. 기능 구현 (검색, 바텀시트)
    // ===============================================================

    // 추천 검색어 클릭 시 호출될 함수 (SearchRecommendFragment와 연동 필요)
    fun onRecommendItemClick(item: SearchItem) {
        hideKeyboard()
        binding.searchEt.clearFocus()

        // 1. 지도 화면(RouteFragment)이 바탕에 있어야 함
        val currentFragment = supportFragmentManager.findFragmentById(R.id.main_fcv)
        if (currentFragment !is RouteFragment) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_fcv, RouteFragment())
                .commit()
        }

        // 2. 검색 모드 UI 끄기
        val transaction = supportFragmentManager.beginTransaction()
        if (historyFragment.isAdded) transaction.hide(historyFragment)
        if (recommendFragment.isAdded) transaction.hide(recommendFragment)
        transaction.commitAllowingStateLoss()

        isDetailFromRecommend = true

        // 3. 곧바로 상세 화면 띄우기 (또는 showBottomSheet로 리스트 띄우기 선택 가능)
        showLocationDetail(item)

        // 상세 화면 띄울 때는 상단 뒤로가기 버튼 보이게 설정
        binding.mainBackIv.visibility = View.VISIBLE
    }

    private fun showSearchFragment(fragment: Fragment) {
        val transaction = supportFragmentManager.beginTransaction()

        for (f in supportFragmentManager.fragments) {
            // 메인 컨테이너에 있고, 검색 관련 프래그먼트가 아니라면 -> 모두 숨김
            if (f.id == R.id.main_fcv && f != historyFragment && f != recommendFragment) {
                transaction.hide(f)
            }
        }

        // History vs Recommend 교차 숨기기
        if (fragment == historyFragment) {
            if (recommendFragment.isAdded) transaction.hide(recommendFragment)
        } else {
            if (historyFragment.isAdded) transaction.hide(historyFragment)
        }

        // 타겟 보이기
        if (!fragment.isAdded) {
            transaction.add(R.id.main_fcv, fragment, "SEARCH_FRAGMENT_TAG")
        } else {
            transaction.show(fragment)
        }
        transaction.commitNowAllowingStateLoss()
    }

    // 자동완성 검색 함수 (위치 권한 체크 + API 호출)
    private fun searchPlaces(query: String) {
        // 1. 세션 토큰이 없으면 새로 생성 (과금 최적화용)
        if (sessionToken == null) {
            sessionToken = AutocompleteSessionToken.newInstance()
        }

        // 2. 실제 API 호출을 수행하는 내부 함수
        fun performSearch(origin: LatLng) {
            val southWest = LatLng(origin.latitude - 0.05, origin.longitude - 0.05)
            val northEast = LatLng(origin.latitude + 0.05, origin.longitude + 0.05)
            val bounds = com.google.android.libraries.places.api.model.RectangularBounds.newInstance(southWest, northEast)

            val request = FindAutocompletePredictionsRequest.builder()
                .setSessionToken(sessionToken)
                .setQuery(query)
                .setCountries("KR")
                .setOrigin(origin)
                .setLocationBias(bounds)
                .build()

            placesClient.findAutocompletePredictions(request)
                .addOnSuccessListener { response ->
                    val resultList = ArrayList<SearchItem>()

                    for (prediction in response.autocompletePredictions) {
                        val name = prediction.getPrimaryText(null).toString()
                        val rawAddress = prediction.getSecondaryText(null).toString()

                        // 주소 문자열 다듬기
                        val address = rawAddress
                            .replace("대한민국 ", "")
                            .replace("서울특별시", "서울")
                            .trim()

                        // 거리 계산 (미터 -> km)
                        val distMeters = prediction.distanceMeters
                        val distStr = if (distMeters != null) String.format("%.1fkm", distMeters / 1000.0) else ""

                        // 카테고리 (한글로 변환)
                        val rawTypes = prediction.placeTypes.map { it.toString().lowercase() }
                        val category = convertTypeToKorean(rawTypes)

                        // 리스트에 추가
                        resultList.add(SearchItem(prediction.placeId, name, address, distStr, category))
                    }

                    // 추천 프래그먼트가 화면에 있을 때만 리스트 업데이트
                    if (recommendFragment.isAdded && !recommendFragment.isHidden) {
                        recommendFragment.updateList(resultList)
                    }
                }
                .addOnFailureListener { exception ->
                    exception.printStackTrace()
                }
        }

        // 3. 위치 권한 확인 및 현재 위치 가져오기
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // 권한 없으면 서울시청 기준으로 검색
            performSearch(LatLng(37.5665, 126.9780))
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            val origin = if (location != null) {
                LatLng(location.latitude, location.longitude)
            } else {
                LatLng(37.5665, 126.9780)
            }
            performSearch(origin)
        }
    }

    // 최종 검색 (엔터 키 입력 시)
    private fun setupEditorActionListener() {
        binding.searchEt.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val query = binding.searchEt.text.toString().trim()
                if (query.isNotEmpty()) {
                    hideKeyboard()
                    binding.searchEt.clearFocus()
                    searchFinalResults(query)
                }
                true
            } else {
                false
            }
        }
    }

    // 최종 결과 검색 및 바텀시트 표시
    @Suppress("DEPRECATION")
    private fun searchFinalResults(query: String) {
        // UI 정리 (검색창 프래그먼트 숨김)
        val transaction = supportFragmentManager.beginTransaction()
        if (historyFragment.isAdded) transaction.hide(historyFragment)
        if (recommendFragment.isAdded) transaction.hide(recommendFragment)
        transaction.commitAllowingStateLoss()

        binding.mainBackIv.visibility = View.VISIBLE

        val placeFields = listOf(
            Place.Field.ID, Place.Field.NAME, Place.Field.ADDRESS,
            Place.Field.LAT_LNG, Place.Field.TYPES, Place.Field.OPENING_HOURS,
            Place.Field.UTC_OFFSET, Place.Field.BUSINESS_STATUS, Place.Field.PHOTO_METADATAS
        )

        val request = com.google.android.libraries.places.api.net.SearchByTextRequest.builder(query, placeFields)
            .setMaxResultCount(5)
            .build()

        placesClient.searchByText(request)
            .addOnSuccessListener { response ->
                val resultList = ArrayList<SearchItem>()
                for (place in response.places) {
                    val name = place.name ?: ""
                    val status = getPlaceStatus(place)
                    val address = place.address ?: ""
                    val types = place.types?.map { it.toString().lowercase() } ?: emptyList()
                    val category = convertTypeToKorean(types)
                    val distStr = calculateDistance(place.latLng)
                    val photoMetadata = place.photoMetadatas?.firstOrNull()

                    resultList.add(SearchItem(
                        placeId = place.id ?: "",
                        name = name,
                        openStatus = status,
                        address = address,
                        distance = distStr,
                        category = category,
                        photoMetadata = photoMetadata
                    ))
                }
                showBottomSheet(resultList)
            }
            .addOnFailureListener { e -> e.printStackTrace() }
    }

    private fun showBottomSheet(items: List<SearchItem>) {
        // 1. 지도 화면으로 전환 (필요시)
        val currentFragment = supportFragmentManager.findFragmentById(R.id.main_fcv)
        if (currentFragment !is RouteFragment) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_fcv, RouteFragment())
                .commit()
        }
        binding.mainBackIv.visibility = View.VISIBLE

        // 2. 바텀시트 프래그먼트 찾아서 데이터 넣기
        val sheetFragment = supportFragmentManager.findFragmentByTag(LocationBottomSheetFragment.TAG) as? LocationBottomSheetFragment
        sheetFragment?.updateData(items)

        // 3. 바텀시트 띄우기 (HALF)
        bottomSheetBehavior.isHideable = false
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
    }

    private fun showLocationDetail(item: SearchItem) {
        val detailFragment = LocationDetailFragment.newInstance(item)

        supportFragmentManager.beginTransaction()
            .replace(R.id.bottom_sheet_container, detailFragment)
            .addToBackStack("DETAIL")
            .commit()

        bottomSheetBehavior.apply {
            isFitToContents = false

            halfExpandedRatio = 0.5f
            state = BottomSheetBehavior.STATE_HALF_EXPANDED
        }

    }

    private fun initBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheetContainer)
        bottomSheetBehavior.apply {
            isFitToContents = false
            halfExpandedRatio = 0.5f
            peekHeight = (130 * resources.displayMetrics.density).toInt()
            isHideable = true
            state = BottomSheetBehavior.STATE_HIDDEN
        }

        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_EXPANDED,
                    BottomSheetBehavior.STATE_HALF_EXPANDED,
                    BottomSheetBehavior.STATE_COLLAPSED -> {
                        binding.mainBnv.visibility = View.GONE
                    }
                    BottomSheetBehavior.STATE_HIDDEN -> {
                        // 검색 모드가 아닐 때만 네비게이션 보이기
                        if (!isSearchMode()) {
                            binding.mainBnv.visibility = View.VISIBLE
                        }
                    }
                }
            }
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                fun findMapFragment(fragments: List<Fragment>): MapFragment? {
                    for (fragment in fragments) {
                        if (fragment is MapFragment) return fragment
                        val childMatch = findMapFragment(fragment.childFragmentManager.fragments)
                        if (childMatch != null) return childMatch
                    }
                    return null
                }

                val mapFragment = findMapFragment(supportFragmentManager.fragments)

                val parentHeight = (bottomSheet.parent as View).height
                val sheetHeightFromBottom = parentHeight - bottomSheet.top

                val correction = (60 * resources.displayMetrics.density).toInt()
                val finalOffset = (sheetHeightFromBottom - correction).coerceAtLeast(0)

                mapFragment?.updateButtonTranslation(finalOffset.toFloat())
            }
        })

        val listFragment = LocationBottomSheetFragment()
        listFragment.onItemClick = { selectedItem ->
            isDetailFromRecommend = false
            showLocationDetail(selectedItem)
        }

        supportFragmentManager.beginTransaction()
            .replace(R.id.bottom_sheet_container, listFragment, LocationBottomSheetFragment.TAG)
            .commit()
    }

    // 탭 전환 로직
    private fun changeFragment(item: MenuItem): Boolean {
        // [중요] 탭 이동 시 검색 상태 초기화
        hideKeyboard()
        binding.searchEt.clearFocus()

        if (isSearchMode()) {
            val transaction = supportFragmentManager.beginTransaction()
            if (historyFragment.isAdded) transaction.hide(historyFragment)
            if (recommendFragment.isAdded) transaction.hide(recommendFragment)
            transaction.commitAllowingStateLoss()
        }

        if (item.itemId != R.id.route && ::bottomSheetBehavior.isInitialized) {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }

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

    // 터치 처리 (키보드 내리기)
    override fun dispatchTouchEvent(ev: android.view.MotionEvent?): Boolean {
        if (ev?.action == android.view.MotionEvent.ACTION_DOWN) {
            val v = currentFocus
            if (v is android.widget.EditText) {
                val searchBoxRect = android.graphics.Rect()
                binding.mainSearchLl.getGlobalVisibleRect(searchBoxRect)
                if (!searchBoxRect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                    v.clearFocus()
                    hideKeyboard()
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    // ===============================================================
    // ★ 유틸리티 함수
    // ===============================================================

    private fun isSearchMode(): Boolean {
        return (historyFragment.isAdded && !historyFragment.isHidden) ||
                (recommendFragment.isAdded && !recommendFragment.isHidden)
    }

    private fun isBottomSheetVisible(): Boolean {
        return ::bottomSheetBehavior.isInitialized &&
                bottomSheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN
    }

    private fun showKeyBoard(){
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(binding.searchEt, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.searchEt.windowToken, 0)
    }

    private fun initPlacesClient() {
        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, BuildConfig.GOOGLE_API_KEY)
        }
        placesClient = Places.createClient(this)
    }

    private fun setupSearchTextWatcher() {
        binding.searchEt.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                searchJob?.cancel()

                if (query.isEmpty()) {
                    binding.btnSearch.setImageResource(R.drawable.ic_search)
                    // 검색어 지우면 History
                    if (isSearchMode()) showSearchFragment(historyFragment)
                } else {
                    binding.btnSearch.setImageResource(R.drawable.ic_close)
                    // 검색어 있으면 Recommend
                    if (isSearchMode()) showSearchFragment(recommendFragment)
                }

                searchJob = lifecycleScope.launch {
                    delay(500L)
                    if (query.isNotEmpty()) searchPlaces(query)
                }
            }
        })
    }

    private fun convertTypeToKorean(types: List<String>): String {
        if (types.isEmpty()) return "장소"
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

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun calculateDistance(dest: LatLng?): String {
        if (myLocation == null || dest == null) return ""
        val destLoc = android.location.Location("dest").apply {
            latitude = dest.latitude
            longitude = dest.longitude
        }
        val distance = myLocation!!.distanceTo(destLoc)
        return if (distance >= 1000) String.format("%.1fkm", distance / 1000.0) else "${distance.toInt()}m"
    }

    @Suppress("DEPRECATION")
    private fun getPlaceStatus(place: Place): String {
        if (place.businessStatus == Place.BusinessStatus.CLOSED_PERMANENTLY ||
            place.businessStatus == Place.BusinessStatus.CLOSED_TEMPORARILY) {
            return "운영 중단"
        }
        if (place.openingHours == null || place.utcOffsetMinutes == null) {
            return "정보 없음"
        }
        return when (place.isOpen(System.currentTimeMillis())) {
            true -> "영업 중"
            false -> "영업 종료"
            else -> "정보 없음"
        }
    }
}
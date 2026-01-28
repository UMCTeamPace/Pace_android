package com.example.pace.ui.main.route

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.pace.BuildConfig
import com.example.pace.R
import com.example.pace.databinding.FragmentRouteBinding
import com.example.pace.ui.main.MainActivity
import com.example.pace.ui.search_box.*
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.net.SearchNearbyRequest
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

class RouteFragment : Fragment() {
    private var _binding: FragmentRouteBinding? = null
    private val binding get() = _binding!!

    private val mainActivity: MainActivity? get() = activity as? MainActivity
    private val mainBinding get() = (activity as? MainActivity)?.binding

    private lateinit var placesClient: PlacesClient
    private val fusedLocationClient by lazy { LocationServices.getFusedLocationProviderClient(requireActivity()) }

    private val historyFragment = SearchHistoryFragment()
    private val recommendFragment = SearchRecommendFragment()
    private lateinit var backPressedCallback: OnBackPressedCallback

    enum class EntryMode {
        MAIN,
        CALENDAR,
        ROUTE_PLAN
    }
    private var currentEntryMode = EntryMode.MAIN

    private var selectedStartPlace: Pair<String, String>? = null
    private var selectedEndPlace: Pair<String, String>? = null
    private var selectedCalendarPlace: Pair<String, String>? = null

    private var isDetailFromRecommend = false
    private var isSelectingStart = true

    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private var searchJob: Job? = null
    private var sessionToken: AutocompleteSessionToken? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRouteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initPlacesClient()
        initBottomSheet()

        // 맵 프래그먼트 로드 (childFragmentManager 사용)
        childFragmentManager.beginTransaction()
            .replace(R.id.route_map_fcv, MapFragment())
            .commitAllowingStateLoss()

        setupMainActivityListeners()
        setupOnBackPressed()
        setupRouteHeaderListeners()
        setupMapSelectListeners()
        setupMyLocationButton()
        binding.btnGoMyLocation.bringToFront()
    }

    private fun setupMainActivityListeners() {
        mainBinding?.searchEt?.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) enterSearchMode()
        }

        setupSearchTextWatcher()

        mainBinding?.searchEt?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val query = mainBinding?.searchEt?.text.toString().trim()
                if (query.isNotEmpty()) {
                    hideKeyboard()
                    mainBinding?.searchEt?.clearFocus()
                    searchFinalResults(query)
                }
                true
            } else false
        }

        mainBinding?.mainBackIv?.setOnClickListener {
            val detailFrag = childFragmentManager.findFragmentByTag("DETAIL")
            if (detailFrag != null && detailFrag.isVisible) {
                // 상세 페이지라면 시스템 뒤로가기와 동일하게 작동
                handleCustomBackClick()
            } else if (isBottomSheetVisible()) {
                // 리스트라면 검색 모드로 복귀
                enterSearchMode()
            } else {
                // 검색 모드라면 키보드 유무 상관없이 즉시 초기화 및 종료
                exitSearchMode()
            }
        }

        mainBinding?.btnSearch?.setOnClickListener {
            if (mainBinding?.searchEt?.text?.isNotEmpty() == true) mainBinding?.searchEt?.setText("")
        }
    }

    // 출발/도착 눌렀을 때 (디테일에서)
    fun onLocationSelected(itemName: String, placeId: String, isStart: Boolean) {
        val detailFrag = childFragmentManager.findFragmentByTag("DETAIL")
        if (detailFrag != null) {
            childFragmentManager.popBackStackImmediate("DETAIL", androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }

        isDetailFromRecommend = false

        if (isStart) {
            selectedStartPlace = Pair(itemName, placeId)
            binding.layoutRouteInputHeader.etRouteStart.setText(itemName)
        } else {
            selectedEndPlace = Pair(itemName, placeId)
            binding.layoutRouteInputHeader.etRouteEnd.setText(itemName)
        }

        if (::bottomSheetBehavior.isInitialized) {
            bottomSheetBehavior.isHideable = true
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            bottomSheetBehavior.peekHeight = 0
        }

        // 2. 경로 탐색 모드 헤더
        currentEntryMode = EntryMode.ROUTE_PLAN
        binding.layoutRouteInputHeader.root.visibility = View.VISIBLE
        binding.layoutRouteInputHeader.root.bringToFront()
        mainBinding?.mainToolbar?.visibility = View.GONE
        mainBinding?.searchEt?.setText("")

        showSearchRouteFragment()
    }

    private fun setupMapSelectListeners() {
        // 확인 버튼 클릭 시
        binding.btnMapSelectConfirm.setOnClickListener {
            val tempName = selectedCalendarPlace?.first ?: binding.tvMapSelectName.text.toString()
            val tempId = selectedCalendarPlace?.second ?: ""

            onLocationSelected(tempName, tempId, isSelectingStart)

            binding.layoutMapSelectOverlay.visibility = View.GONE

            selectedCalendarPlace = null
        }

         binding.layoutMapSelectHeader.btnMapSelectBack.setOnClickListener {
            binding.layoutMapSelectOverlay.visibility = View.GONE
            enterSearchMode()
         }
    }

    fun onSelectOnMapSelected() {
        val transaction = childFragmentManager.beginTransaction()

        if (historyFragment.isAdded) transaction.hide(historyFragment)
        if (recommendFragment.isAdded) transaction.hide(recommendFragment)

        val currentResultFrag = childFragmentManager.findFragmentByTag("ROUTE_RESULT")
        if (currentResultFrag != null && currentResultFrag.isAdded) {
            transaction.hide(currentResultFrag)
        }

        transaction.commitAllowingStateLoss()
        // 1. 기존 검색 UI 숨기기
        binding.routeSearchFcv.visibility = View.GONE
        mainBinding?.mainToolbar?.visibility = View.GONE
        mainBinding?.mainBnv?.visibility = View.GONE

        if (::bottomSheetBehavior.isInitialized) {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }

        binding.layoutMapSelectOverlay.visibility = View.VISIBLE
        binding.layoutMapSelectOverlay.bringToFront()

        val mapFrag = childFragmentManager.findFragmentById(R.id.route_map_fcv) as? MapFragment

        val supportMapFrag = mapFrag?.childFragmentManager
            ?.findFragmentById(R.id.google_map_container) as? SupportMapFragment

        supportMapFrag?.getMapAsync { googleMap ->
            val center = googleMap.cameraPosition.target
            updateAddressFromMapCenter(center)
        }
    }

    private fun enterSearchMode() {
        binding.layoutRouteInputHeader.root.visibility = View.GONE

        mainBinding?.mainToolbar?.visibility = View.VISIBLE
        mainBinding?.mainBackIv?.visibility = View.VISIBLE

        if (::bottomSheetBehavior.isInitialized) {
            bottomSheetBehavior.isHideable = true
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

            childFragmentManager.popBackStackImmediate("DETAIL", androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
            isDetailFromRecommend = false

            bottomSheetBehavior.isHideable = true
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }

        val routeResultFrag = childFragmentManager.findFragmentByTag("ROUTE_RESULT")
        if (routeResultFrag != null) {
            childFragmentManager.beginTransaction()
                .remove(routeResultFrag)
                .commitAllowingStateLoss()
        }

        binding.routeSearchFcv.visibility = View.VISIBLE
        binding.routeSearchFcv.bringToFront()

        val query = mainBinding?.searchEt?.text.toString().trim()
        val targetFragment = if (query.isNotEmpty()) recommendFragment else historyFragment

        if (targetFragment is SearchHistoryFragment) {
            val isRoutePlan = (currentEntryMode == EntryMode.ROUTE_PLAN)
            targetFragment.setRouteOptionsVisible(isRoutePlan)
        }

        showSearchFragment(targetFragment)

        mainBinding?.searchEt?.requestFocus()
        showKeyBoard()
    }

    private fun exitSearchMode() {
        hideKeyboard()
        mainBinding?.searchEt?.clearFocus()
        mainBinding?.searchEt?.setText("")

        searchJob?.cancel()

        selectedStartPlace = null
        selectedEndPlace = null
        selectedCalendarPlace = null
        currentEntryMode = EntryMode.MAIN
        isDetailFromRecommend = false
        sessionToken = null
        isSelectingStart = true

        binding.layoutRouteInputHeader.etRouteStart.setText("")
        binding.layoutRouteInputHeader.etRouteEnd.setText("")

        val transaction = childFragmentManager.beginTransaction()
        if (historyFragment.isAdded) transaction.hide(historyFragment)
        if (recommendFragment.isAdded) transaction.hide(recommendFragment)
        transaction.commitAllowingStateLoss()

        mainBinding?.mainSearchLl?.visibility = View.VISIBLE
        binding.routeSearchFcv.visibility = View.GONE
        mainBinding?.mainBackIv?.visibility = View.GONE
        mainBinding?.mainToolbar?.visibility = View.VISIBLE
        mainBinding?.mainBnv?.visibility = View.VISIBLE
        binding.routeSearchFcv.visibility = View.GONE
        binding.layoutRouteInputHeader.root.visibility = View.GONE

        if (::bottomSheetBehavior.isInitialized) {
            bottomSheetBehavior.isHideable = true
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }
    }

    private fun showSearchFragment(fragment: Fragment) {
        if (_binding == null) return
        val transaction = childFragmentManager.beginTransaction()

        if (fragment == historyFragment) {
            if (recommendFragment.isAdded) transaction.hide(recommendFragment)
        } else {
            if (historyFragment.isAdded) transaction.hide(historyFragment)
        }

        if (!fragment.isAdded) {
            transaction.add(R.id.route_search_fcv, fragment)
        } else {
            transaction.show(fragment)
        }
        binding.routeSearchFcv.visibility = View.VISIBLE
        transaction.commitAllowingStateLoss()
    }

    private fun setupSearchTextWatcher() {
        mainBinding?.searchEt?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (_binding == null || !isAdded || isDetached) return

                val query = s.toString().trim()
                searchJob?.cancel()

                mainBinding?.let { mBinding ->
                    mBinding.btnSearch.setImageResource(
                        if (query.isEmpty()) R.drawable.ic_search else R.drawable.ic_close
                    )
                    if (isSearchMode()) {
                        showSearchFragment(if (query.isNotEmpty()) recommendFragment else historyFragment)
                    }
                }

                searchJob = lifecycleScope.launch {
                    delay(500L)
                    if (_binding != null && isAdded && query.isNotEmpty()) {
                        searchPlaces(query)
                    }
                }
            }
        })
    }

    fun onRecommendItemClick(item: SearchItem) {
        hideKeyboard()
        mainBinding?.searchEt?.clearFocus()

        exitSearchMode()

        isDetailFromRecommend = true

        showLocationDetail(item)

        mainBinding?.mainBackIv?.visibility = View.VISIBLE
    }

    private fun showSearchRouteFragment() {
        val transaction = childFragmentManager.beginTransaction()

        if (selectedStartPlace != null && selectedEndPlace != null) {

            if (historyFragment.isAdded) transaction.hide(historyFragment)
            if (recommendFragment.isAdded) transaction.hide(recommendFragment)

            val routeResultFrag = RouteResultFragment()
            transaction.replace(R.id.route_search_fcv, routeResultFrag, "ROUTE_RESULT")

        } else {
            val existingRouteFrag = childFragmentManager.findFragmentByTag("ROUTE_RESULT")
            if (existingRouteFrag != null) {
                transaction.remove(existingRouteFrag)
            }

            if (!historyFragment.isAdded) {
                transaction.add(R.id.route_search_fcv, historyFragment, "HISTORY")
            } else {
                transaction.show(historyFragment)
            }

            if (recommendFragment.isAdded) transaction.hide(recommendFragment)
        }

        // 트랜잭션 실행
        transaction.commitAllowingStateLoss()

        binding.routeSearchFcv.visibility = View.VISIBLE
        hideKeyboard()

        if (::bottomSheetBehavior.isInitialized) {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }
    }

    private fun setupRouteHeaderListeners() {
        binding.layoutRouteInputHeader.etRouteStart.setOnClickListener {
            isSelectingStart = true
            enterSearchMode()
        }

        binding.layoutRouteInputHeader.etRouteEnd.setOnClickListener {
            isSelectingStart = false
            enterSearchMode()
        }
    }
    private fun setupOnBackPressed() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // 상단 버튼 클릭과 동일한 로직 실행
                handleCustomBackClick()
            }
        })
    }

    private fun handleCustomBackClick() {
        // 1. 키보드
        if (isKeyboardVisible()) {
            hideKeyboard()
            return
        }

        // 2. 지도 선택 오버레이
        if (binding.layoutMapSelectOverlay.visibility == View.VISIBLE) {
            binding.layoutMapSelectOverlay.visibility = View.GONE
            selectedCalendarPlace = null
            enterSearchMode()
            return
        }

        // 3. 상세 정보(Detail) 바텀시트
        val detailFrag = childFragmentManager.findFragmentByTag("DETAIL")
        if (detailFrag != null && detailFrag.isVisible) {
            childFragmentManager.popBackStack()
            if (isDetailFromRecommend) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                enterSearchMode()
            } else {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
            }
            return
        }

        // 4. 검색 결과 리스트 바텀시트
        if (isBottomSheetVisible()) {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            enterSearchMode()
            return
        }

        // 5. & 6. 통합 처리
        // 검색 중(하나만 입력)이거나, 결과 화면(둘 다 입력)이거나
        // 어쨌든 "초기 화면이 아닌 상태"라면 -> 초기 화면으로 복귀
        if (isSearchMode() || (selectedStartPlace != null && selectedEndPlace != null)) {
            exitSearchMode() // 여기서 싹 다 지우고 초기화
            return
        }

        // 7. 앱 종료
        backPressedCallback.isEnabled = false
        requireActivity().onBackPressedDispatcher.onBackPressed()
        backPressedCallback.isEnabled = true
    }

    @Suppress("DEPRECATION")
    fun updateAddressFromMapCenter(latLng: LatLng) {
        if (binding.layoutMapSelectOverlay.visibility != View.VISIBLE) return

        val geocoder = android.location.Geocoder(requireContext(), java.util.Locale.KOREAN)

        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
                val fullAddress = addresses?.firstOrNull()?.getAddressLine(0)?.replace("대한민국 ", "") ?: ""

                launch(kotlinx.coroutines.Dispatchers.Main) {
                    val placeFields = listOf(Place.Field.NAME, Place.Field.TYPES, Place.Field.ADDRESS, Place.Field.ID)

                    val circle = com.google.android.libraries.places.api.model.CircularBounds.newInstance(latLng, 50.0)

                    val searchNearbyRequest = SearchNearbyRequest.builder(circle, placeFields)
                        .setMaxResultCount(1)
                        .build()

                    placesClient.searchNearby(searchNearbyRequest)
                        .addOnSuccessListener { response ->
                            val place = response.places.firstOrNull()

                            val name = place?.name ?: addresses?.firstOrNull()?.featureName ?: "지정된 위치"
                            val id = place?.id ?: ""

                            selectedCalendarPlace = Pair(name, id)

                            if (place != null) {
                                val category = convertTypeToKorean(place.types?.map { it.toString().lowercase() } ?: emptyList())
                                binding.tvMapSelectName.text = place.name
                                binding.tvMapSelectInfo.text = "$category · ${calculateDistance(latLng)} · ${place.address?.replace("대한민국 ", "")}"
                            } else {
                                binding.tvMapSelectName.text = addresses?.firstOrNull()?.featureName ?: "지정된 위치"
                                binding.tvMapSelectInfo.text = "지정된 위치 · ${calculateDistance(latLng)} · $fullAddress"
                            }
                        }
                        .addOnFailureListener {
                            it.printStackTrace()
                        }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun searchPlaces(query: String) {
        if (!::placesClient.isInitialized || query.isBlank()) return
        if (sessionToken == null) sessionToken = AutocompleteSessionToken.newInstance()

        fun performSearch(origin: LatLng) {
            if (!isAdded || isDetached || _binding == null) return

            val bounds = com.google.android.libraries.places.api.model.RectangularBounds.newInstance(
                LatLng(origin.latitude - 0.05, origin.longitude - 0.05),
                LatLng(origin.latitude + 0.05, origin.longitude + 0.05)
            )
            val request = FindAutocompletePredictionsRequest.builder()
                .setSessionToken(sessionToken).setQuery(query).setCountries("KR")
                .setOrigin(origin).setLocationBias(bounds).build()

            placesClient.findAutocompletePredictions(request).addOnSuccessListener { response ->
                if (isAdded && _binding != null && recommendFragment.isAdded) {
                    val resultList = ArrayList<SearchItem>()
                    for (prediction in response.autocompletePredictions) {
                        val distStr = prediction.distanceMeters?.let { String.format("%.1fkm", it / 1000.0) } ?: ""
                        resultList.add(SearchItem(prediction.placeId, prediction.getPrimaryText(null).toString(),
                            prediction.getSecondaryText(null).toString().replace("대한민국 ", ""), distStr,
                            convertTypeToKorean(prediction.placeTypes.map { it.toString().lowercase() })))
                    }
                    recommendFragment.updateList(resultList)
                }
            }
        }

        val context = context ?: return
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            performSearch(LatLng(37.5665, 126.9780))
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (isAdded) performSearch(location?.let { LatLng(it.latitude, it.longitude) } ?: LatLng(37.5665, 126.9780))
        }
    }

    @Suppress("DEPRECATION")
    private fun searchFinalResults(query: String) {
        val transaction = childFragmentManager.beginTransaction()
        if (historyFragment.isAdded) transaction.hide(historyFragment)
        if (recommendFragment.isAdded) transaction.hide(recommendFragment)
        transaction.commitAllowingStateLoss()
        binding.routeSearchFcv.visibility = View.GONE

        mainBinding?.mainBackIv?.visibility = View.VISIBLE
        val placeFields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.ADDRESS, Place.Field.LAT_LNG, Place.Field.TYPES, Place.Field.OPENING_HOURS, Place.Field.UTC_OFFSET, Place.Field.BUSINESS_STATUS, Place.Field.PHOTO_METADATAS)
        placesClient.searchByText(com.google.android.libraries.places.api.net.SearchByTextRequest.builder(query, placeFields).setMaxResultCount(5).build())
            .addOnSuccessListener { response ->
                val resultList = response.places.map { place ->
                    SearchItem(
                        placeId = place.id ?: "",
                        name = place.name ?: "",
                        address = place.address ?: "",
                        distance = calculateDistance(place.latLng),
                        category = convertTypeToKorean(place.types?.map { it.toString().lowercase() } ?: emptyList()),
                        openStatus = getPlaceStatus(place), // 이 함수의 반환값이 String인지 확인
                        photoMetadata = place.photoMetadatas?.firstOrNull() // SearchItem 정의와 타입 일치 확인
                    )
                }
                showBottomSheet(resultList)
            }
    }

    private fun showBottomSheet(items: List<SearchItem>) {
        val sheetFragment = childFragmentManager.findFragmentByTag(LocationBottomSheetFragment.TAG) as? LocationBottomSheetFragment
        sheetFragment?.updateData(items)
        bottomSheetBehavior.isHideable = false
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
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
                mainBinding?.mainBnv?.visibility = if (newState == BottomSheetBehavior.STATE_HIDDEN && !isSearchMode()) View.VISIBLE else View.GONE
            }
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                val mapFragment = childFragmentManager.findFragmentById(R.id.route_map_fcv) as? MapFragment
                val parentHeight = (bottomSheet.parent as View).height
                val currentSheetHeight = parentHeight - bottomSheet.top
                val offset = currentSheetHeight.toFloat()
                mapFragment?.updateButtonTranslation(offset)
            }
        })

        childFragmentManager.beginTransaction()
            .replace(R.id.bottom_sheet_container, LocationBottomSheetFragment().apply {
                onItemClick = { isDetailFromRecommend = false; showLocationDetail(it) }
            }, LocationBottomSheetFragment.TAG).commit()
    }

    private fun showLocationDetail(item: SearchItem) {
        val isCalendarMode = currentEntryMode == EntryMode.CALENDAR

        val detailFragment = LocationDetailFragment.newInstance(item, isCalendarMode)

        childFragmentManager.beginTransaction()
            .replace(R.id.bottom_sheet_container, detailFragment, "DETAIL")
            .addToBackStack("DETAIL")
            .commit()
        bottomSheetBehavior.apply { isFitToContents = false; state = BottomSheetBehavior.STATE_HALF_EXPANDED }
    }

    private fun setupMyLocationButton() {
        binding.btnGoMyLocation.setOnClickListener {
            // 1. 권한 체크
            if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
                mainActivity?.checkPermissionAndStart()
                return@setOnClickListener
            }

            // 2. 위치 가져오기
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location == null) {
                    mainActivity?.startLocationUpdates()
                    return@addOnSuccessListener
                }

                val targetLocation = LatLng(location.latitude, location.longitude)

                // 3. MapFragment 내부의 구글 맵 객체에 직접 접근
                // route_map_fcv에 replace된 것은 MapFragment입니다.
                val mapFrag = childFragmentManager.findFragmentById(R.id.route_map_fcv) as? MapFragment

                // MapFragment의 childFragmentManager에서 진짜 SupportMapFragment를 찾음
                val supportMapFrag = mapFrag?.childFragmentManager?.findFragmentById(R.id.google_map_container) as? SupportMapFragment

                supportMapFrag?.getMapAsync { googleMap ->
                    googleMap.animateCamera(com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(targetLocation, 15f))
                }
            }
        }
    }

    // 유틸리티 함수들
    private fun isSearchMode() = (historyFragment.isAdded && !historyFragment.isHidden) || (recommendFragment.isAdded && !recommendFragment.isHidden)
    private fun isBottomSheetVisible() = ::bottomSheetBehavior.isInitialized && bottomSheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN
    private fun showKeyBoard() = (requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(mainBinding?.searchEt, InputMethodManager.SHOW_IMPLICIT)
    private fun hideKeyboard() = (requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(mainBinding?.searchEt?.windowToken, 0)

    private fun isKeyboardVisible(): Boolean {
        // 1. 루트 뷰의 인셋 정보 가져오기
        val insets = ViewCompat.getRootWindowInsets(requireActivity().window.decorView)
        // 2. IME(입력기, 즉 키보드) 영역이 보이는지 확인
        return insets?.isVisible(WindowInsetsCompat.Type.ime()) ?: false
    }
    private fun initPlacesClient() { if (!Places.isInitialized()) Places.initialize(requireContext(), BuildConfig.GOOGLE_API_KEY); placesClient = Places.createClient(requireContext()); sessionToken = AutocompleteSessionToken.newInstance() }

    private fun convertTypeToKorean(types: List<String>): String {
        return when {
            types.contains("subway_station") -> "지하철역"
            types.contains("restaurant") || types.contains("food") -> "음식점"
            types.contains("cafe") -> "카페"
            types.contains("park") -> "공원"
            else -> "기타장소"
        }
    }

    private fun calculateDistance(dest: LatLng?): String {
        val myLoc = mainActivity?.myLocation ?: return ""
        val destLoc = android.location.Location("dest").apply { latitude = dest?.latitude ?: 0.0; longitude = dest?.longitude ?: 0.0 }
        val dist = myLoc.distanceTo(destLoc)
        return if (dist >= 1000) String.format("%.1fkm", dist / 1000.0) else "${dist.toInt()}m"
    }

    private fun getPlaceStatus(place: Place): String {
        if (place.businessStatus == Place.BusinessStatus.CLOSED_PERMANENTLY) return "운영 중단"
        return if (place.isOpen(System.currentTimeMillis()) == true) "영업 중" else "영업 종료"
    }

    override fun onPause() {
        super.onPause()
        (activity as? MainActivity)?.binding?.searchEt?.setOnFocusChangeListener(null)
    }

    override fun onDestroyView() {
        (activity as? MainActivity)?.binding?.let { activityBinding ->
            activityBinding.searchEt.setOnFocusChangeListener(null)
            activityBinding.searchEt.setOnEditorActionListener(null)

            activityBinding.btnSearch.setOnClickListener(null)
            activityBinding.mainBackIv.setOnClickListener(null)
        }

        searchJob?.cancel()

        super.onDestroyView()
        _binding = null
    }
}
package com.example.pace.ui.main.route

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.NumberPicker
import android.widget.RadioGroup
import androidx.activity.OnBackPressedCallback
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.pace.BuildConfig
import com.example.pace.PaceApplication
import com.example.pace.R
import com.example.pace.data.model.RouteResponse
import com.example.pace.data.db.SearchDatabase
import com.example.pace.data.model.RecentHistoryItem
import com.example.pace.data.model.RecentPlace
import com.example.pace.data.model.RecentRoute
import com.example.pace.data.repository.SearchRepository
import com.example.pace.data.util.RouteConstants
import com.example.pace.data.viewmodel.SearchViewModel
import com.example.pace.data.viewmodel.SearchViewModelFactory
import com.example.pace.databinding.FragmentRouteBinding
import com.example.pace.ui.main.MainActivity
import com.example.pace.ui.search_box.*
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.net.SearchNearbyRequest
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.SearchByTextRequest
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.ArrayList
import java.util.Locale

class RouteFragment : Fragment() {
    private var _binding: FragmentRouteBinding? = null
    private val binding get() = _binding!!

    private val mainActivity: MainActivity? get() = activity as? MainActivity
    private val mainBinding get() = (activity as? MainActivity)?.binding

    private val searchViewModel: SearchViewModel by viewModels {
        SearchViewModelFactory((requireActivity().application as PaceApplication).searchRepository)
    }

    private lateinit var placesClient: PlacesClient

    private val historyFragment = SearchHistoryFragment()
    private val recommendFragment = SearchRecommendFragment()
    private lateinit var backPressedCallback: OnBackPressedCallback

    enum class EntryMode {
        MAIN,
        ROUTE_PLAN,
        SCHEDULE,
        SCHEDULE_ROUTE
    }
    private var currentEntryMode = EntryMode.MAIN

    private var selectedStartPlace: Pair<String, String>? = null
    private var selectedEndPlace: Pair<String, String>? = null
    private var selectedCalendarPlace: Pair<String, String>? = null
    private var earlyArriveTime: Int = 0
    private var currentSortOption: RouteSortOption = RouteSortOption.BEST

    private var isDetailFromRecommend = false
    private var isSelectingStart = true

    private var currentRankPreference = SearchByTextRequest.RankPreference.RELEVANCE // 검색 필터
    private var lastQuery: String = ""

    //백엔드 경로 탐색을 위해 여기다가 placeId를 좌표로 api 검색해서 주기
    private var startLatLng: LatLng? = null
    private var endLatLng: LatLng? = null
    private var scheduleColor: String = "#DC354B"
    private var scheduleName: String = ""
    private var scheduleTime: String = "00:00"
    private var searchTime: String = ""
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

        searchViewModel.deleteExpiredData()

        initPlacesClient()
        initBottomSheet()

        binding.layoutRouteInputHeader.layoutFilterOptions.visibility = View.GONE

        val mapFragment = MapFragment()

        mapFragment.onMapTouched = {
            if (::bottomSheetBehavior.isInitialized
                && bottomSheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN
                && bottomSheetBehavior.state != BottomSheetBehavior.STATE_COLLAPSED) {

                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

                mapFragment.setMapPadding(bottomSheetBehavior.peekHeight)
            }
        }

        // 맵 프래그먼트 로드 (childFragmentManager 사용)
        childFragmentManager.beginTransaction()
            .replace(R.id.route_map_fcv, mapFragment)
            .commitAllowingStateLoss()

        setupMainActivityListeners()
        setupOnBackPressed()
        setupRouteHeaderListeners()
        setupMapSelectListeners()
        setupMyLocationButton()
        setupRouteDetailListeners()

        val activityIntent = requireActivity().intent
        val actionMode = activityIntent?.getStringExtra("ACTION_MODE")
        if (actionMode == "SCHEDULE") {
            startScheduleMode()
            activityIntent.removeExtra("ACTION_MODE")
        } else if(actionMode == "SCHEDULE_ROUTE") {
            startScheduleRouteMode()
            activityIntent.removeExtra("ACTION_MODE")
        }
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

                    saveRecentSearch(query)

                    currentRankPreference = SearchByTextRequest.RankPreference.RELEVANCE
                    val sheet = childFragmentManager.findFragmentByTag(LocationBottomSheetFragment.TAG) as? LocationBottomSheetFragment
                    sheet?.resetFilter()
                    searchFinalResults(query)
                }
                true
            } else false
        }

        mainBinding?.mainBackIv?.setOnClickListener {
            handleCustomBackClick()
        }

        mainBinding?.btnSearch?.setOnClickListener {
            if (mainBinding?.searchEt?.text?.isNotEmpty() == true) mainBinding?.searchEt?.setText("")
        }
    }

    fun startScheduleMode() {
        if (_binding == null || !isAdded || view == null) return

        currentEntryMode = EntryMode.SCHEDULE

        enterSearchMode()
    }

    fun startScheduleRouteMode() {
        if (_binding == null || !isAdded || view == null) return

        currentEntryMode = EntryMode.SCHEDULE_ROUTE
        val intent = requireActivity().intent
        val nameExtra = intent.getStringExtra("SCHEDULE_NAME")
        scheduleName = if(nameExtra.isNullOrBlank()) "일정명" else nameExtra
        scheduleColor = intent.getStringExtra("SCHEDULE_COLOR") ?: "#DC354B"
        scheduleTime = intent.getStringExtra("SCHEDULE_TIME") ?: "00:00"
        earlyArriveTime = intent.getIntExtra("EARLY_ARRIVE_TIME", 10)
        val sortNum = intent.getIntExtra("SORT_OPTION", 0)
        val sortString = when (sortNum) {
            RouteConstants.SORT_OPTION_BEST -> "최적 경로순"
            RouteConstants.SORT_OPTION_TIME -> "최소 시간순"
            RouteConstants.SORT_OPTION_TRANSFER -> "최소 환승순"
            RouteConstants.SORT_OPTION_WALK -> "최소 도보순"
            else -> "최적 경로순"
        }
        currentSortOption = RouteSortOption.values().find { it.uiText == sortString }
            ?: RouteSortOption.BEST
        searchTime = intent.getStringExtra("SEARCH_TIME") ?: ""

        binding.layoutMapSelectOverlay.root.visibility = View.GONE
        if (::bottomSheetBehavior.isInitialized) {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }
        hideKeyboard()

        mainBinding?.mainBnv?.visibility = View.GONE
        mainBinding?.mainToolbar?.visibility = View.GONE
        mainBinding?.mainBackIv?.visibility = View.GONE

        binding.routeSearchFcv.visibility = View.VISIBLE
        binding.layoutRouteInputHeader.root.visibility = View.VISIBLE
        binding.layoutRouteInputHeader.root.bringToFront()

        showSearchRouteFragment()
    }

    // 출발/도착 눌렀을 때 (디테일에서)
    fun onLocationSelected(itemName: String, placeId: String, isStart: Boolean) {
        val detailFrag = childFragmentManager.findFragmentByTag("DETAIL")
        if (detailFrag != null) {
            childFragmentManager.popBackStackImmediate("DETAIL", androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }

        isDetailFromRecommend = false
        historyFragment.setRouteOptionsVisible(false)
        mainBinding?.mainBnv?.visibility = View.GONE

        if (isStart) {
            selectedStartPlace = Pair(itemName, placeId)
            binding.layoutRouteInputHeader.tvRouteStart.setText(itemName)
            updateClearButtonVisibility()
        } else {
            selectedEndPlace = Pair(itemName, placeId)
            binding.layoutRouteInputHeader.tvRouteEnd.setText(itemName)
            updateClearButtonVisibility()
        }

        if (::bottomSheetBehavior.isInitialized) {
            bottomSheetBehavior.isHideable = true
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            bottomSheetBehavior.peekHeight = 0
        }

        // 2. 경로 탐색 모드 헤더
        if(currentEntryMode == EntryMode.MAIN){
            currentEntryMode = EntryMode.ROUTE_PLAN
        }
        binding.layoutRouteInputHeader.root.visibility = View.VISIBLE
        binding.layoutRouteInputHeader.root.bringToFront()
        mainBinding?.mainToolbar?.visibility = View.GONE
        mainBinding?.searchEt?.setText("")

        showSearchRouteFragment()
    }

    fun onScheduleLocationSelected(name: String, placeId: String) {
        val resultIntent = android.content.Intent().apply {
            putExtra("placeName", name)
            putExtra("placeId", placeId)
        }

        // 2. 결과 설정 (RESULT_OK)
        requireActivity().setResult(android.app.Activity.RESULT_OK, resultIntent)

        binding.routeMapFcv.visibility = View.GONE
        requireActivity().finish()
    }

    private fun setupMapSelectListeners() {
        // 확인 버튼 클릭 시
        binding.layoutMapSelectOverlay.btnMapSelectConfirm.setOnClickListener {
            val tempName = selectedCalendarPlace?.first ?: binding.layoutMapSelectOverlay.tvMapSelectName.text.toString()
            val tempId = selectedCalendarPlace?.second ?: ""

            onLocationSelected(tempName, tempId, isSelectingStart)

            binding.layoutMapSelectOverlay.root.visibility = View.GONE
            val mapFrag = childFragmentManager.findFragmentById(R.id.route_map_fcv) as? MapFragment
            mapFrag?.setMyLocationButtonVisibility(true)

            selectedCalendarPlace = null
        }

        binding.layoutMapSelectOverlay.layoutMapSelectHeader.btnMapSelectBack.setOnClickListener {
            binding.layoutMapSelectOverlay.root.visibility = View.GONE
            val mapFrag = childFragmentManager.findFragmentById(R.id.route_map_fcv) as? MapFragment
            mapFrag?.setMyLocationButtonVisibility(true)
            enterSearchMode()
        }
    }

    private fun setupRouteDetailListeners() {
        binding.layoutRouteDetailOverlay.btnRouteDetailBackDetail.setOnClickListener {
            handleCustomBackClick()
        }
    }

    fun onSelectOnMapSelected() {
        hideKeyboard()
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

        binding.layoutMapSelectOverlay.root.visibility = View.VISIBLE
        binding.layoutMapSelectOverlay.root.bringToFront()

        val mapFrag = childFragmentManager.findFragmentById(R.id.route_map_fcv) as? MapFragment
        mapFrag?.initMapSelectionMode()

        val supportMapFrag = mapFrag?.childFragmentManager
            ?.findFragmentById(R.id.google_map_container) as? SupportMapFragment

        supportMapFrag?.getMapAsync { googleMap ->
            val center = googleMap.cameraPosition.target
            updateAddressFromMapCenter(center)
        }
    }

    fun showRouteDetailOverlay(item: RouteResponse) {
        if(currentEntryMode == EntryMode.SCHEDULE_ROUTE || currentEntryMode == EntryMode.ROUTE_PLAN){
            binding.routeSearchFcv.visibility = View.GONE

            binding.layoutRouteInputHeader.root.visibility = View.GONE

            mainBinding?.mainBnv?.visibility = View.GONE
            mainBinding?.mainToolbar?.visibility = View.GONE

            if (::bottomSheetBehavior.isInitialized) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            }

            binding.layoutRouteDetailOverlay.root.visibility = View.VISIBLE
            binding.layoutRouteDetailOverlay.root.bringToFront()
            try {
                val colorInt = Color.parseColor(scheduleColor)

                binding.layoutRouteDetailOverlay.viewColorDotRouteDetail.backgroundTintList = ColorStateList.valueOf(colorInt)

            } catch (e: Exception) {
                e.printStackTrace()
                binding.layoutRouteDetailOverlay.viewColorDotRouteDetail.backgroundTintList = ColorStateList.valueOf(Color.RED)
            }
            binding.layoutRouteDetailOverlay.tvScheduleRouteDetailName.text = scheduleName
            binding.layoutRouteDetailOverlay.tvScheduleRouteDetailTime.text = scheduleTime

            val bottomSheetView = binding.layoutRouteDetailOverlay.root.findViewById<View>(R.id.sheet_route_detail)
            val behavior = BottomSheetBehavior.from(bottomSheetView)

            behavior.isHideable = false
            behavior.state = BottomSheetBehavior.STATE_COLLAPSED
            behavior.peekHeight = (250 * resources.displayMetrics.density).toInt() // 지도 보일 정도 높이

            RouteDetailHelper.setupData(requireContext(),bottomSheetView, item, selectedEndPlace?.first ?: "")


        }

    }

    fun onRouteSelectedFinal(item: RouteResponse){
        if(currentEntryMode == EntryMode.SCHEDULE_ROUTE){
            val resultIntent = android.content.Intent().apply {
                putExtra("startPlaceName", selectedStartPlace?.first)
                putExtra("startPlaceId", selectedStartPlace?.second)
                putExtra("endPlaceName", selectedEndPlace?.first)
                putExtra("endPlaceId", selectedEndPlace?.second)
                putExtra("earlyArriveTime", earlyArriveTime)
                val sortNum = when (currentSortOption) {
                    RouteSortOption.BEST -> RouteConstants.SORT_OPTION_BEST
                    RouteSortOption.TIME -> RouteConstants.SORT_OPTION_TIME
                    RouteSortOption.TRANSFER -> RouteConstants.SORT_OPTION_TRANSFER
                    RouteSortOption.WALK -> RouteConstants.SORT_OPTION_WALK
                    else -> RouteConstants.SORT_OPTION_BEST
                }
                putExtra("sortOption", sortNum)
                putExtra("routeData", Gson().toJson(item))
            }

            requireActivity().setResult(android.app.Activity.RESULT_OK, resultIntent)
            requireActivity().finish()
        }else {
            // 일반 경로 탐색이면 일정 activity 띄우기;;
        }
    }

    private fun enterSearchMode() {
        binding.layoutRouteInputHeader.layoutFilterOptions.visibility = View.GONE
        binding.layoutRouteInputHeader.root.visibility = View.GONE

        mainBinding?.mainToolbar?.visibility = View.VISIBLE
        mainBinding?.mainBackIv?.visibility = View.VISIBLE
        mainBinding?.mainBnv?.visibility = View.GONE

        if (::bottomSheetBehavior.isInitialized) {
            bottomSheetBehavior.isHideable = true
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

            childFragmentManager.popBackStackImmediate("DETAIL", androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
            isDetailFromRecommend = false
        }

        val routeResultFrag = childFragmentManager.findFragmentByTag("ROUTE_RESULT")
        if (routeResultFrag != null) {
            childFragmentManager.beginTransaction()
                .hide(routeResultFrag)
                .commitAllowingStateLoss()
        }

        binding.routeSearchFcv.visibility = View.VISIBLE
        binding.routeSearchFcv.bringToFront()

        val query = mainBinding?.searchEt?.text.toString().trim()
        val targetFragment = if (query.isNotEmpty()) recommendFragment else historyFragment

        if (targetFragment is SearchHistoryFragment) {
            val isRoutePlan = (currentEntryMode == EntryMode.ROUTE_PLAN || currentEntryMode==EntryMode.SCHEDULE_ROUTE)
            targetFragment.setRouteOptionsVisible(isRoutePlan)

            targetFragment.onRouteOptionClick = { isMyLocation ->
                if (isMyLocation) {
                    selectCurrentLocation()
                }
            }

            targetFragment.updateChipsForScheduleMode(isRouteHeaderVisible = false, isScheduleMode = currentEntryMode==EntryMode.SCHEDULE)
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
        if(currentEntryMode == EntryMode.ROUTE_PLAN){
            currentEntryMode = EntryMode.MAIN
        }
        isDetailFromRecommend = false
        sessionToken = null
        startLatLng = null
        endLatLng = null

        binding.layoutRouteInputHeader.tvRouteStart.setText("")
        binding.layoutRouteInputHeader.tvRouteEnd.setText("")

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

        val mapFrag = childFragmentManager.findFragmentById(R.id.route_map_fcv) as? MapFragment
        mapFrag?.clearMarkers()
        mapFrag?.setMapPadding(0)

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
        if (fragment is LocationBottomSheetFragment) {
            fragment.onSortTypeSelected = { newRankPreference ->
                this.currentRankPreference = newRankPreference

                if (this.lastQuery.isNotEmpty()) {
                    searchPlaces(this.lastQuery)
                }
            }
        }

        binding.routeSearchFcv.visibility = View.VISIBLE
        transaction.commitAllowingStateLoss()
    }

private fun selectCurrentLocation() {
    if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        mainActivity?.checkPermissionAndStart()
        return
    }

    val location = mainActivity?.myLocation

    if (location == null) {
        mainActivity?.startLocationUpdates()
        return
    }

    val latLng = LatLng(location.latitude, location.longitude)
    val geocoder = android.location.Geocoder(requireContext(), Locale.KOREAN)

    lifecycleScope.launch(Dispatchers.IO) {
        try {
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
            val fullAddress = addresses?.firstOrNull()?.getAddressLine(0)?.replace("대한민국 ", "") ?: "주소 미상"

            withContext(Dispatchers.Main) {
                applyCurrentLocationSelection(fullAddress, latLng)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                applyCurrentLocationSelection("현재 위치", latLng)
            }
        }
    }
}

    private fun applyCurrentLocationSelection(address: String, latLng: LatLng) {
        if (isSelectingStart) {
            selectedStartPlace = Pair(address, "내 위치")
            startLatLng = latLng
            binding.layoutRouteInputHeader.tvRouteStart.text = address
            updateClearButtonVisibility()
        } else {
            selectedEndPlace = Pair(address, "내 위치")
            endLatLng = latLng
            binding.layoutRouteInputHeader.tvRouteEnd.text = address
            updateClearButtonVisibility()
        }

        hideKeyboard()
        mainBinding?.searchEt?.clearFocus()
        mainBinding?.searchEt?.setText("")

        val transaction = childFragmentManager.beginTransaction()
        if (historyFragment.isAdded) transaction.hide(historyFragment)
        if (recommendFragment.isAdded) transaction.hide(recommendFragment)
        transaction.commitAllowingStateLoss()

        binding.routeSearchFcv.visibility = View.GONE

        if (currentEntryMode == EntryMode.MAIN) {
                currentEntryMode = EntryMode.ROUTE_PLAN
        }
        binding.layoutRouteInputHeader.root.visibility = View.VISIBLE
        mainBinding?.mainToolbar?.visibility = View.GONE

        showSearchRouteFragment()
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

        val mapFrag = childFragmentManager.findFragmentById(R.id.route_map_fcv) as? MapFragment
        mapFrag?.clearMarkers()

        if(currentEntryMode == EntryMode.ROUTE_PLAN || currentEntryMode == EntryMode.SCHEDULE_ROUTE){
            onLocationSelected(item.name, item.placeId, isSelectingStart)

            val transaction = childFragmentManager.beginTransaction()
            if (historyFragment.isAdded) transaction.hide(historyFragment)
            if (recommendFragment.isAdded) transaction.hide(recommendFragment)
            transaction.commitAllowingStateLoss()

            binding.routeSearchFcv.visibility = View.GONE

            mainBinding?.searchEt?.setText("")

            return
        }

        exitSearchMode()

        isDetailFromRecommend = true

        showLocationDetail(item)

        mainBinding?.mainBackIv?.visibility = View.VISIBLE
    }
    fun updateMapFromDetail(name: String, placeId: String, lat: Double, lng: Double) {
        val mapFrag = childFragmentManager.findFragmentById(R.id.route_map_fcv) as? MapFragment ?: return

        if (isDetailFromRecommend) {
            val tempItem = SearchItem(
                placeId = placeId,
                name = "선택된 장소",
                address = "",
                distance = "",
                category = "",
                lat = lat,
                lng = lng
            )
            mapFrag.showMultipleMarkers(listOf(tempItem)){}
        }

        mapFrag.moveCameraToSinglePosition(lat, lng)
    }

    private fun showSearchRouteFragment() {
        val transaction = childFragmentManager.beginTransaction()

        mainBinding?.mainBnv?.visibility = View.GONE
        mainBinding?.mainToolbar?.visibility = View.GONE

        val existingRouteFrag = childFragmentManager.findFragmentByTag("ROUTE_RESULT")

        if (selectedStartPlace != null && selectedEndPlace != null) {
            saveCurrentRoute()

            if (historyFragment.isAdded) transaction.hide(historyFragment)
            if (recommendFragment.isAdded) transaction.hide(recommendFragment)

            if (existingRouteFrag != null) {
                transaction.show(existingRouteFrag)
            } else {
                val newRouteFrag = RouteResultFragment()
                transaction.add(R.id.route_search_fcv, newRouteFrag, "ROUTE_RESULT")
            }

            binding.layoutRouteInputHeader.layoutFilterOptions.visibility = View.VISIBLE

            if (currentEntryMode == EntryMode.SCHEDULE_ROUTE) {
                binding.layoutRouteInputHeader.tvTimeFilter.text = "${earlyArriveTime}분 전 도착"
                binding.layoutRouteInputHeader.tvSortFilter.text = currentSortOption.uiText
            }

        } else {
            binding.layoutRouteInputHeader.layoutFilterOptions.visibility = View.GONE

//            val existingRouteFrag = childFragmentManager.findFragmentByTag("ROUTE_RESULT")
            if (existingRouteFrag != null) {
                transaction.hide(existingRouteFrag)
            }

            if (!historyFragment.isAdded) {
                transaction.add(R.id.route_search_fcv, historyFragment, "HISTORY")
            } else {
                transaction.show(historyFragment)
            }

            historyFragment.updateChipsForScheduleMode(isRouteHeaderVisible = true, isScheduleMode = currentEntryMode==EntryMode.SCHEDULE)

            if (recommendFragment.isAdded) transaction.hide(recommendFragment)
        }

        transaction.commitAllowingStateLoss()

        binding.routeSearchFcv.visibility = View.VISIBLE
        binding.layoutRouteInputHeader.root.visibility = View.VISIBLE
        hideKeyboard()

        if (::bottomSheetBehavior.isInitialized) {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }
    }

    private fun saveCurrentRoute() {
        val start = selectedStartPlace ?: return
        val end = selectedEndPlace ?: return

        val newRoute = RecentRoute(
            startPlaceName = start.first,
            startPlaceId = start.second,
            endPlaceName = end.first,
            endPlaceId = end.second
        )

        // 3. DB 저장 및 청소
        lifecycleScope.launch(Dispatchers.IO) {
            val database = SearchDatabase.getDatabase(requireContext())
            database.recentRouteDao().insertRecentRoute(newRoute)

            // 30일 지난 데이터 삭제
            searchViewModel.deleteExpiredData()
        }

    }

    private fun setupRouteHeaderListeners() {
        binding.layoutRouteInputHeader.tvRouteStart.setOnClickListener {
            isSelectingStart = true
            enterSearchMode()
        }

        binding.layoutRouteInputHeader.tvRouteEnd.setOnClickListener {
            isSelectingStart = false
            enterSearchMode()
        }

        binding.layoutRouteInputHeader.btnStartClear.setOnClickListener {
            selectedStartPlace = null
            startLatLng = null
            binding.layoutRouteInputHeader.tvRouteStart.text = ""
            updateClearButtonVisibility()

            showSearchRouteFragment()
        }

        binding.layoutRouteInputHeader.btnEndClear.setOnClickListener {
            selectedEndPlace = null
            endLatLng = null
            binding.layoutRouteInputHeader.tvRouteEnd.text = ""
            updateClearButtonVisibility()

            showSearchRouteFragment()
        }

        binding.layoutRouteInputHeader.btnSwapLocation.setOnClickListener {
            val tempPlace = selectedStartPlace
            selectedStartPlace = selectedEndPlace
            selectedEndPlace = tempPlace

            val tempLatLng = startLatLng
            startLatLng = endLatLng
            endLatLng = tempLatLng

            binding.layoutRouteInputHeader.tvRouteStart.text = selectedStartPlace?.first ?: ""
            binding.layoutRouteInputHeader.tvRouteEnd.text = selectedEndPlace?.first ?: ""

            updateClearButtonVisibility()

            showSearchRouteFragment()
        }

        binding.layoutRouteInputHeader.btnRouteBack.setOnClickListener {

            if(currentEntryMode == EntryMode.SCHEDULE_ROUTE){
                binding.routeMapFcv.visibility = View.GONE
                activity?.finish()
            }
            else{
                exitSearchMode()
            }
        }

        binding.layoutRouteInputHeader.tvTimeFilter.setOnClickListener {
            if (currentEntryMode == EntryMode.SCHEDULE_ROUTE) {
                showScheduleRouteDialog()
            } else {
//                showRoutePlanDialog()
            }
        }

        binding.layoutRouteInputHeader.tvSortFilter.setOnClickListener {
            showSortOptionBottomSheet()
        }
    }

    private fun updateClearButtonVisibility() {
        val startText = binding.layoutRouteInputHeader.tvRouteStart.text
        binding.layoutRouteInputHeader.btnStartClear.visibility =
            if (startText.isNotEmpty()) View.VISIBLE else View.GONE

        val endText = binding.layoutRouteInputHeader.tvRouteEnd.text
        binding.layoutRouteInputHeader.btnEndClear.visibility =
            if (endText.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun setupOnBackPressed() {
        backPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleCustomBackClick()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backPressedCallback)
    }

    private fun handleCustomBackClick(){
        if (isKeyboardVisible()) {
            hideKeyboard()
            return
        }
        if (currentEntryMode == EntryMode.SCHEDULE || currentEntryMode == EntryMode.SCHEDULE_ROUTE) {
            handleScheduleBackClick()
        } else {
            handleMainBackClick()
        }
    }

    private fun handleMainBackClick() {
        if (binding.layoutRouteInputHeader.root.visibility == View.VISIBLE) {
            exitSearchMode()
            return // 앱 종료 방지
        }

        if (binding.layoutRouteDetailOverlay.root.visibility == View.VISIBLE) {
            binding.layoutRouteDetailOverlay.root.visibility = View.GONE

            binding.routeSearchFcv.visibility = View.VISIBLE
            binding.layoutRouteInputHeader.root.visibility = View.VISIBLE

            mainBinding?.mainBnv?.visibility = View.GONE
            mainBinding?.mainToolbar?.visibility = View.GONE
            return
        }

        // 2. 지도 선택 오버레이
        if (binding.layoutMapSelectOverlay.root.visibility == View.VISIBLE) {
            binding.layoutMapSelectOverlay.root.visibility = View.GONE
            selectedCalendarPlace = null
            enterSearchMode()
            return
        }

        // 3. 상세 정보(Detail) 바텀시트
        val detailFrag = childFragmentManager.findFragmentByTag("DETAIL")
        if (detailFrag != null && detailFrag.isVisible) {
            childFragmentManager.popBackStack()
            bottomSheetBehavior.isDraggable = true
            if (isDetailFromRecommend) {
                bottomSheetBehavior.isHideable = true
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                enterSearchMode()
            } else {
                bottomSheetBehavior.isHideable = false
                val density = resources.displayMetrics.density
                bottomSheetBehavior.peekHeight = (130 * density).toInt()
                bottomSheetBehavior.expandedOffset = 0
                bottomSheetBehavior.isFitToContents = false
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED

                setMapPaddingToBottomSheetHeight()
            }
            val mapFrag = childFragmentManager.findFragmentById(R.id.route_map_fcv) as? MapFragment
            mapFrag?.restoreAllMarkers()
            return
        }

        // 4. 검색 결과 리스트 바텀시트
        if (isBottomSheetVisible()) {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            enterSearchMode()
            return
        }

        if (isSearchMode()) {

            if (currentEntryMode == EntryMode.ROUTE_PLAN) {

                if (selectedStartPlace == null && selectedEndPlace == null) {
                    exitSearchMode()
                }
                else {
                    hideKeyboard()
                    mainBinding?.searchEt?.clearFocus()
                    mainBinding?.searchEt?.setText("")

                    showSearchRouteFragment()

                    binding.layoutRouteInputHeader.root.visibility = View.VISIBLE
                    mainBinding?.mainToolbar?.visibility = View.GONE

                    historyFragment.setRouteOptionsVisible(false)
                }
                return
            }

            exitSearchMode()
            return
        }

        // 7. 앱 종료
        if (::backPressedCallback.isInitialized) {
            backPressedCallback.isEnabled = false
            requireActivity().onBackPressedDispatcher.onBackPressed()
            backPressedCallback.isEnabled = true
        } else {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun handleScheduleBackClick(){
        if (binding.layoutRouteDetailOverlay.root.visibility == View.VISIBLE) {
            binding.layoutRouteDetailOverlay.root.visibility = View.GONE

            binding.routeSearchFcv.visibility = View.VISIBLE
            binding.layoutRouteInputHeader.root.visibility = View.VISIBLE

            return
        }

        if (binding.layoutMapSelectOverlay.root.visibility == View.VISIBLE) {
            binding.layoutMapSelectOverlay.root.visibility = View.GONE
            selectedCalendarPlace = null
            enterSearchMode()
            return
        }

        // 3. 상세 정보(Detail) 바텀시트
        val detailFrag = childFragmentManager.findFragmentByTag("DETAIL")
        if (detailFrag != null && detailFrag.isVisible) {
            childFragmentManager.popBackStack()
            bottomSheetBehavior.isDraggable = true
            if (isDetailFromRecommend) {
                bottomSheetBehavior.isHideable = true
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                enterSearchMode()
            } else {
                bottomSheetBehavior.isHideable = false
                val density = resources.displayMetrics.density
                bottomSheetBehavior.peekHeight = (130 * density).toInt()
                bottomSheetBehavior.expandedOffset = 0
                bottomSheetBehavior.isFitToContents = false
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED

                setMapPaddingToBottomSheetHeight()
            }
            val mapFrag = childFragmentManager.findFragmentById(R.id.route_map_fcv) as? MapFragment
            mapFrag?.restoreAllMarkers()
            return
        }

        if (isBottomSheetVisible()) {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            enterSearchMode()
            return
        }

        val isRouteHeaderVisible = binding.layoutRouteInputHeader.root.visibility == View.VISIBLE

        if (isRouteHeaderVisible) {
            binding.routeMapFcv.visibility = View.GONE
            activity?.finish()
            return
        }

        if (isSearchMode()) {

            if (currentEntryMode == EntryMode.SCHEDULE_ROUTE) {
                hideKeyboard()
                mainBinding?.searchEt?.clearFocus()
                mainBinding?.searchEt?.setText("")

                mainBinding?.mainToolbar?.visibility = View.GONE
                binding.layoutRouteInputHeader.root.visibility = View.VISIBLE

                binding.layoutRouteInputHeader.tvRouteStart.text = selectedStartPlace?.first ?: ""
                binding.layoutRouteInputHeader.tvRouteEnd.text = selectedEndPlace?.first ?: ""
                updateClearButtonVisibility()

                showSearchRouteFragment()

                return

//                if (selectedStartPlace == null && selectedEndPlace == null) {
//                    exitSearchMode()
//                }
//                else {
//                    hideKeyboard()
//                    mainBinding?.searchEt?.clearFocus()
//                    mainBinding?.searchEt?.setText("")
//
//                    showSearchRouteFragment()
//
//                    binding.layoutRouteInputHeader.root.visibility = View.VISIBLE
//                    mainBinding?.mainToolbar?.visibility = View.GONE
//
//                    historyFragment.setRouteOptionsVisible(false)
//                }
//                return
            }

            exitSearchMode()
            binding.routeMapFcv.visibility = View.GONE
            activity?.finish()
            return
        }

        binding.routeMapFcv.visibility = View.GONE
        activity?.finish()
    }

    @Suppress("DEPRECATION")
    fun updateAddressFromMapCenter(latLng: LatLng) {
        if (binding.layoutMapSelectOverlay.root.visibility != View.VISIBLE) return

        val geocoder = android.location.Geocoder(requireContext(), java.util.Locale.KOREAN)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
                val fullAddress = addresses?.firstOrNull()?.getAddressLine(0)?.replace("대한민국 ", "") ?: ""

                launch(Dispatchers.Main) {
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
                                binding.layoutMapSelectOverlay.tvMapSelectName.text = place.name
                                binding.layoutMapSelectOverlay.tvMapSelectInfo.text = "$category · ${calculateDistance(latLng)} · ${place.address?.replace("대한민국 ", "")}"
                            } else {
                                binding.layoutMapSelectOverlay.tvMapSelectName.text = addresses?.firstOrNull()?.featureName ?: "지정된 위치"
                                binding.layoutMapSelectOverlay.tvMapSelectInfo.text = "지정된 위치 · ${calculateDistance(latLng)} · $fullAddress"
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

    private fun showScheduleRouteDialog(){
        val view = layoutInflater.inflate(R.layout.dialog_schedule_route_filter, null)

        val bottomSheetDialog = BottomSheetDialog(requireContext())
        bottomSheetDialog.setContentView(view)

        val npMinute = view.findViewById<NumberPicker>(R.id.np_minute)
        val btnCancel = view.findViewById<Button>(R.id.btn_cancel_schedule_route_filter)
        val btnSave = view.findViewById<Button>(R.id.btn_save_schedule_route_filter)

        npMinute.minValue = 0
        npMinute.maxValue = 60
        npMinute.value = earlyArriveTime
        npMinute.wrapSelectorWheel = true

        btnCancel.setOnClickListener {
            bottomSheetDialog.dismiss()
        }

        btnSave.setOnClickListener {
            val selectedMinute = npMinute.value
            earlyArriveTime = selectedMinute

            binding.layoutRouteInputHeader.tvTimeFilter.text = "${selectedMinute}분 전 도착"

            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.show()
    }

    private fun showSortOptionBottomSheet(){
        val view = layoutInflater.inflate(R.layout.dialog_route_sort_filter, null)

        val bottomSheetDialog = BottomSheetDialog(requireContext())
        bottomSheetDialog.setContentView(view)

        val rgSort = view.findViewById<RadioGroup>(R.id.rg_sort_options_sort_filter)
        val btnCancel = view.findViewById<Button>(R.id.btn_cancel_sort_filter)
        val btnSave = view.findViewById<Button>(R.id.btn_save_sort_filter)

        val idToCheck = when(currentSortOption) {
            RouteSortOption.BEST -> R.id.rb_best_route
            RouteSortOption.TIME -> R.id.rb_min_time
            RouteSortOption.TRANSFER -> R.id.rb_min_transfer
            RouteSortOption.WALK -> R.id.rb_min_walk
        }
        rgSort.check(idToCheck)

        btnCancel.setOnClickListener {
            bottomSheetDialog.dismiss()
        }

        btnSave.setOnClickListener {
            val selectedOption = when (rgSort.checkedRadioButtonId) {
                R.id.rb_best_route -> RouteSortOption.BEST
                R.id.rb_min_time -> RouteSortOption.TIME
                R.id.rb_min_transfer -> RouteSortOption.TRANSFER
                R.id.rb_min_walk -> RouteSortOption.WALK
                else -> RouteSortOption.BEST
            }

            currentSortOption = selectedOption
            binding.layoutRouteInputHeader.tvSortFilter.text = selectedOption.uiText

            // 실제 정렬 로직 추가

            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.show()
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

        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            performSearch(LatLng(37.5665, 126.9780))
            return
        }

        val myLocation = mainActivity?.myLocation
        val origin = if (myLocation != null) LatLng(myLocation.latitude, myLocation.longitude) else LatLng(37.5665, 126.9780)

        performSearch(origin)
    }

    @Suppress("DEPRECATION")
    private fun searchFinalResults(query: String) {
        this.lastQuery = query
        val transaction = childFragmentManager.beginTransaction()
        if (historyFragment.isAdded) transaction.hide(historyFragment)
        if (recommendFragment.isAdded) transaction.hide(recommendFragment)
        if (recommendFragment.isAdded) transaction.hide(recommendFragment)
        transaction.commitAllowingStateLoss()

        binding.routeSearchFcv.visibility = View.GONE
        mainBinding?.mainBackIv?.visibility = View.VISIBLE

        fun requestSearch(centerLatLng: LatLng) {
            val placeFields = listOf(
                Place.Field.ID, Place.Field.NAME, Place.Field.ADDRESS,
                Place.Field.LAT_LNG, Place.Field.TYPES, Place.Field.OPENING_HOURS,
                Place.Field.UTC_OFFSET, Place.Field.BUSINESS_STATUS, Place.Field.PHOTO_METADATAS
            )

            val builder = SearchByTextRequest.builder(query, placeFields)
                .setMaxResultCount(5)
                .setRankPreference(currentRankPreference)

            if (currentRankPreference == SearchByTextRequest.RankPreference.DISTANCE) {
                val locationBias = com.google.android.libraries.places.api.model.CircularBounds.newInstance(centerLatLng, 10000.0) // 10km 반경
                builder.setLocationBias(locationBias)
            }

            placesClient.searchByText(builder.build())
                .addOnSuccessListener { response ->
                    val resultList = response.places.map { place ->
                        SearchItem(
                            placeId = place.id ?: "",
                            name = place.name ?: "",
                            address = place.address ?: "",
                            distance = calculateDistance(place.latLng),
                            category = convertTypeToKorean(place.types?.map { it.toString().lowercase() } ?: emptyList()),
                            openStatus = getPlaceStatus(place),
                            photoMetadata = place.photoMetadatas?.firstOrNull(),
                            lat = place.latLng?.latitude ?: 0.0,
                            lng = place.latLng?.longitude ?: 0.0
                        )
                    }
                    showBottomSheet(resultList)

                    val mapFrag = childFragmentManager.findFragmentById(R.id.route_map_fcv) as? MapFragment
                    setMapPaddingToBottomSheetHeight()
                    binding.routeMapFcv.visibility = View.VISIBLE
                    mapFrag?.showMultipleMarkers(resultList) { clickedItem ->
                        isDetailFromRecommend = false
                        showLocationDetail(clickedItem)
                    }
                }
                .addOnFailureListener {
                    it.printStackTrace()
                }
        }

        val savedLocation = mainActivity?.myLocation
        if (savedLocation != null) {
            requestSearch(LatLng(savedLocation.latitude, savedLocation.longitude))
        } else {
            requestSearch(LatLng(37.5665, 126.9780))
        }
    }

    private fun showBottomSheet(items: List<SearchItem>) {
        var sheetFragment = childFragmentManager.findFragmentByTag(LocationBottomSheetFragment.TAG) as? LocationBottomSheetFragment

        // 상세 화면에서 돌아왔을 때
        if (sheetFragment == null) {
            sheetFragment = LocationBottomSheetFragment().apply {
                onItemClick = {
                    isDetailFromRecommend = false
                    showLocationDetail(it)
                }
                onSortTypeSelected = { newRankPreference ->
                    this@RouteFragment.currentRankPreference = newRankPreference
                    if (this@RouteFragment.lastQuery.isNotEmpty()) {
                        searchFinalResults(this@RouteFragment.lastQuery)
                    }
                }
            }
            childFragmentManager.beginTransaction()
                .replace(R.id.bottom_sheet_container, sheetFragment, LocationBottomSheetFragment.TAG)
                .commitNowAllowingStateLoss()
        }

        // 3. 데이터 업데이트
        // LocationBottomSheetFragment 내부에 currentItems 변수가 있어서 뷰 생성 전이라도 데이터가 저장됨
        sheetFragment.updateData(items)

        // 4. 바텀시트 설정
        bottomSheetBehavior.apply {
            isHideable = false

            val density = resources.displayMetrics.density
            peekHeight = (130 * density).toInt()

            expandedOffset = 0
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

        val mapFrag = childFragmentManager.findFragmentById(R.id.route_map_fcv) as? MapFragment
        mapFrag?.onMapTouched = {
            if (bottomSheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN
                && bottomSheetBehavior.state != BottomSheetBehavior.STATE_COLLAPSED) {

                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                mapFrag!!.setMapPadding(bottomSheetBehavior.peekHeight)
            }
        }

        childFragmentManager.beginTransaction()
            .replace(R.id.bottom_sheet_container, LocationBottomSheetFragment().apply {
                onItemClick = {
                    isDetailFromRecommend = false
                    showLocationDetail(it)
                }

                onSortTypeSelected = { newRankPreference ->
                    currentRankPreference = newRankPreference

                    if (lastQuery.isNotEmpty()) {
                        searchFinalResults(lastQuery)
                    }
                }
            }, LocationBottomSheetFragment.TAG).commit()
    }

    fun setBottomSheetFixed(isFixed: Boolean) {
        if (!::bottomSheetBehavior.isInitialized) return

        if (isFixed) {
            bottomSheetBehavior.apply {
                isDraggable = false
                state = BottomSheetBehavior.STATE_COLLAPSED
                peekHeight = (130 * resources.displayMetrics.density).toInt()
            }
        } else {
            bottomSheetBehavior.apply {
                isDraggable = true
                state = BottomSheetBehavior.STATE_HALF_EXPANDED
            }
        }
    }

    private fun showLocationDetail(item: SearchItem) {
        saveRecentPlace(item)
        val isSchedule = currentEntryMode == EntryMode.SCHEDULE

        val detailFragment = LocationDetailFragment.newInstance(item, isSchedule)

        childFragmentManager.beginTransaction()
            .replace(R.id.bottom_sheet_container, detailFragment, "DETAIL")
            .addToBackStack("DETAIL")
            .commit()
        bottomSheetBehavior.apply { isFitToContents = false; state = BottomSheetBehavior.STATE_HALF_EXPANDED }

        bottomSheetBehavior.apply {
            val density = resources.displayMetrics.density
            peekHeight = (130 * density).toInt()
            isFitToContents = false
            halfExpandedRatio = 0.5f
            expandedOffset = (resources.displayMetrics.heightPixels * 0.5).toInt()
            isHideable = false
            state = BottomSheetBehavior.STATE_HALF_EXPANDED
        }

        setMapPaddingToBottomSheetHeight()

        val mapFrag = childFragmentManager.findFragmentById(R.id.route_map_fcv) as? MapFragment
        mapFrag?.showOnlySelectedMarker(item)

        if (item.lat != 0.0 && item.lng != 0.0) {
            mapFrag?.moveCameraToSinglePosition(item.lat, item.lng)
        }
    }

    fun handleHistoryItemClick(item: RecentHistoryItem){
        when (item.type) {
            RecentHistoryItem.TYPE_SEARCH_TEXT -> {
                mainBinding?.searchEt?.setText(item.mainText)
                hideKeyboard()
                mainBinding?.searchEt?.clearFocus()
                saveRecentSearch(item.mainText)
                currentRankPreference = SearchByTextRequest.RankPreference.RELEVANCE
                searchFinalResults(item.mainText)
            }

            RecentHistoryItem.TYPE_PLACE -> {
                val place = item.placeEntity ?: return

                saveRecentPlace(SearchItem(
                    placeId = place.placeId,
                    name = place.name,
                    address = place.address,
                    category = place.category,
                    lat = place.lat,
                    lng = place.lng,
                    distance = ""
                ))

                when (currentEntryMode) {
                    EntryMode.ROUTE_PLAN, EntryMode.SCHEDULE_ROUTE -> {
                        val isHeaderVisible = binding.layoutRouteInputHeader.root.visibility == View.VISIBLE

                        if (isHeaderVisible) {
                            if (selectedStartPlace == null) {
                                onLocationSelected(place.name, place.placeId, isStart = true)
                            } else {
                                onLocationSelected(place.name, place.placeId, isStart = false)
                            }
                        }else{
                            onLocationSelected(place.name, place.placeId, isSelectingStart)
                        }
                    }
                    else -> {
                        val searchItem = SearchItem(
                            placeId = place.placeId,
                            name = place.name,
                            address = place.address,
                            category = place.category,
                            openStatus = place.openStatus,
                            lat = place.lat,
                            lng = place.lng,
                            distance = ""
                        )
                        val mapFrag = childFragmentManager.findFragmentById(R.id.route_map_fcv) as? MapFragment
                        exitSearchMode()
                        isDetailFromRecommend = true
                        showLocationDetail(searchItem)
                        mapFrag?.clearMarkers()
                        mapFrag?.showMultipleMarkers(listOf(searchItem)){}
                    }
                }
            }
        }
    }

    private fun setMapPaddingToBottomSheetHeight() {
        val mapFrag = childFragmentManager.findFragmentById(R.id.route_map_fcv) as? MapFragment ?: return

        val parentHeight = binding.bottomSheetContainer.parent.let { (it as View).height }

        val screenHeight = if (parentHeight > 0) parentHeight else resources.displayMetrics.heightPixels
        val halfHeight = (screenHeight * bottomSheetBehavior.halfExpandedRatio).toInt()

        mapFrag.setMapPadding(halfHeight)
    }

private fun setupMyLocationButton() {
    binding.layoutMapSelectOverlay.btnGoMyLocationOverlay.setOnClickListener {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            mainActivity?.checkPermissionAndStart()
            return@setOnClickListener
        }

        val location = mainActivity?.myLocation

        if (location == null) {
            mainActivity?.startLocationUpdates()
            return@setOnClickListener
        }

        val targetLocation = LatLng(location.latitude, location.longitude)
        val mapFrag = childFragmentManager.findFragmentById(R.id.route_map_fcv) as? MapFragment
        val supportMapFrag = mapFrag?.childFragmentManager?.findFragmentById(R.id.google_map_container) as? SupportMapFragment
        supportMapFrag?.getMapAsync { googleMap ->
            googleMap.animateCamera(com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(targetLocation, 15f))
        }
    }
}
    private fun saveRecentSearch(query: String){
        searchViewModel.insertSearch(query)
    }
    private fun saveRecentPlace(item: SearchItem) {
        val recentPlace = RecentPlace(
            placeId = item.placeId,
            name = item.name,
            address = item.address,
            category = item.category,
            openStatus = item.openStatus ?: "",
            lat = item.lat,
            lng = item.lng,
            timestamp = System.currentTimeMillis()
        )

        searchViewModel.insertPlace(recentPlace)
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
            types.contains("train_station") -> "기차역"
            types.contains("bus_station") -> "버스정류장"
            types.contains("transit_station") -> "교통/역"

            types.contains("lodging") || types.contains("hotel") -> "숙박"
            types.contains("hospital") -> "병원"
            types.contains("university") -> "대학교"
            types.contains("school") -> "학교"
            types.contains("gym") || types.contains("health") -> "운동/헬스"
            types.contains("bank") || types.contains("atm") -> "은행/ATM"
            types.contains("park") -> "공원"

            types.contains("pharmacy") -> "약국"
            types.contains("bakery") -> "제과점"
            types.contains("cafe") -> "카페"
            types.contains("bar") -> "술집"

            types.contains("restaurant") || types.contains("food") -> "음식점"

            types.contains("convenience_store") -> "편의점"
            types.contains("clothing_store") -> "의류"

            types.contains("store") || types.contains("shopping_mall") -> "상점/쇼핑"

            else -> "기타장소"
        }
    }

    fun calculateDistance(dest: LatLng?): String {
        val myLoc = mainActivity?.myLocation ?: return ""
        val destLoc = android.location.Location("dest").apply { latitude = dest?.latitude ?: 0.0; longitude = dest?.longitude ?: 0.0 }
        val dist = myLoc.distanceTo(destLoc)
        return if (dist >= 1000) String.format("%.1fkm", dist / 1000.0) else "${dist.toInt()}m"
    }

    fun getPlaceStatus(place: Place): String {
        if (place.businessStatus == Place.BusinessStatus.CLOSED_PERMANENTLY) return "운영 중단"
        return try {
            // 현재 시간을 기준으로 영업 여부 확인
            if (place.isOpen(System.currentTimeMillis()) == true) {
                "영업 중"
            } else {
                "영업 종료"
            }
        } catch (e: Exception) {
            // SDK 내부에서 Invalid range 에러가 발생할 경우 기본값 반환
            e.printStackTrace()
            "정보 없음"
        }
    }

    fun getCurrentLocation(): android.location.Location? {
        return (activity as? MainActivity)?.myLocation
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

enum class RouteSortOption(val uiText: String, val apiValue: String) {
    BEST("최적 경로순", "EFFICIENT"),
    TIME("최소 시간순", "MIN_TIME"),
    TRANSFER("최소 환승순", "MIN_TRANSFER"),
    WALK("최소 도보순", "MIN_WALK")
}
package com.example.pace.ui.main.route

import android.Manifest
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
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
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.pace.BuildConfig
import com.example.pace.PaceApplication
import com.example.pace.R
import com.example.pace.data.datasource.AuthDataStore
import com.example.pace.data.db.SearchDatabase
import com.example.pace.data.model.MarkScheduleProvider
import com.example.pace.data.model.MyPlace
import com.example.pace.data.model.RecentHistoryItem
import com.example.pace.data.model.RecentPlace
import com.example.pace.data.model.RecentRoute
import com.example.pace.data.model.RouteMappingData
import com.example.pace.data.model.request.RouteSearchRequest
import com.example.pace.data.model.response.BusItemList
import com.example.pace.data.model.response.RouteOnlyScheduleData
import com.example.pace.data.model.response.RouteResponse
import com.example.pace.data.model.response.SubwayTransitResult
import com.example.pace.data.repository.SearchRepository
import com.example.pace.data.util.RouteConstants
import com.example.pace.data.viewmodel.RouteViewModel
import com.example.pace.data.viewmodel.SearchViewModel
import com.example.pace.data.viewmodel.SearchViewModelFactory
import com.example.pace.data.viewmodel.SettingsViewModel
import com.example.pace.data.viewmodel.TransitViewModel
import com.example.pace.databinding.FragmentRouteBinding
import com.example.pace.ui.NetworkErrorDialog
import com.example.pace.ui.add_schedule.AddScheduleActivity
import com.example.pace.ui.main.MainActivity
import com.example.pace.ui.search_box.*
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.net.SearchNearbyRequest
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.SearchByTextRequest
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.tabs.TabLayout
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.lang.AutoCloseable
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.ArrayList
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

@AndroidEntryPoint
class RouteFragment : Fragment() {
    @Inject
    lateinit var authDataStore: AuthDataStore

    private var _binding: FragmentRouteBinding? = null
    private val binding get() = _binding!!

    private val mainActivity: MainActivity? get() = activity as? MainActivity
    private val mainBinding get() = (activity as? MainActivity)?.binding

    private val searchViewModel: SearchViewModel by viewModels {
        SearchViewModelFactory((requireActivity().application as PaceApplication).searchRepository)
    }

    private val routeViewModel: RouteViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by activityViewModels()
    private val transitViewModel: TransitViewModel by viewModels()

    private var hasSchedule: Boolean = true
    private var currentTransitType: String? = null // 칩 선택 값
    private var isStart: Boolean = true

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
    private var selectedOnMapPlace: Pair<String, String>? = null
    private var currentMapCenter: LatLng? = null
    private var currentMapAddress: String? = ""
    private var currentMapCategory: String? = ""
    private var currentMyLocation: LatLng? = null
    private var earlyArriveTime: Int = -1
    private var currentSortOption: RouteSortOption = RouteSortOption.BEST

    private var isDetailFromRecommend = false
    private var isSelectingStart = true
    private var isBookmarkSearchMode = false
    private var wasRouteHeaderVisibleBeforeBookmark = false
    enum class BookmarkTarget { NONE, HOME, WORK }

    private var bookmarkTarget = BookmarkTarget.NONE
    private var selectedGroupId: Int? = null

    private var currentRankPreference = SearchByTextRequest.RankPreference.RELEVANCE // 검색 필터
    private var lastQuery: String = ""
    private var isPoiMode = false

    //백엔드 경로 탐색을 위해 여기다가 placeId를 좌표로 api 검색해서 주기
    private var startLatLng: LatLng? = null
    private var endLatLng: LatLng? = null
    private var scheduleColor: String = ""
    private var scheduleName: String = ""
    private var scheduleTime: String = "00:00"
    private var scheduleEndTime: String = "00:00"
    private var scheduleDate: String = "2026-11-11"
    private var requestSearchTime: String = ""
    private var responseArrivelTime: String = ""
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private var searchJob: Job? = null
    private var realtimePollingJob: Job? = null
    private var currentRealtimeParams: List<RealtimeParam>? = null
    private var sessionToken: AutocompleteSessionToken? = null
    private var pendingResetToCurrentLocationState = false
    private var pendingActionModeExtras: Bundle? = null

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
        initDetailBottomSheet()

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

        mapFragment.onPoiClick = { poi ->
            if(binding.layoutRouteDetailOverlay.root.visibility == View.GONE &&
                binding.layoutMapSelectOverlay.root.visibility == View.GONE &&
                binding.layoutRouteInputHeader.root.visibility == View.GONE){
                onPoiSelected(poi.placeId)
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
        setupBookmarkHeaderListenrs()

        hasSchedule = false

        routeViewModel.fetchRouteOnlySchedule()

        observeRouteViewModel()
        observeSettings()
        applyPendingActionModeIfNeeded()
        applyPendingResetIfNeeded()
    }

    private fun showDefaultScheduleOverlay(data: RouteOnlyScheduleData? = null) {
        if (data == null) return
        if (!hasSchedule) return

        val scheduleInfo = data.scheduleInfo
        val routeInfo = data.route ?: return

//        val mockSchedule = MarkScheduleProvider.getMockSchedule()
//        val json = mockSchedule.placeJson ?: return

        binding.routeSearchFcv.visibility = View.GONE
        binding.layoutRouteInputHeader.root.visibility = View.GONE
        mainBinding?.mainBackIv?.visibility = View.GONE
        mainBinding?.mainToolbar?.visibility = View.VISIBLE // 메인 툴바는 보이게
        mainBinding?.mainBnv?.visibility = View.VISIBLE     // 바텀 네비도 보이게

        binding.layoutRouteDetailOverlay.root.visibility = View.VISIBLE
        binding.layoutRouteDetailOverlay.btnRouteDetailBackDetail.visibility = View.GONE
        binding.layoutRouteDetailOverlay.root.bringToFront()
        binding.layoutRouteDetailOverlay.layoutRouteSelectContainer.visibility = View.GONE
        binding.layoutRouteDetailOverlay.tvScheduleRouteDetailName.visibility = View.VISIBLE
        binding.layoutRouteDetailOverlay.tvScheduleRouteDetailTime.visibility = View.VISIBLE
        binding.layoutRouteDetailOverlay.viewColorDotRouteDetail.visibility = View.VISIBLE
        binding.layoutRouteDetailOverlay.bottomSheetRouteDetail.visibility = View.VISIBLE

        // 일정 모드 UI 세팅
        binding.layoutRouteDetailOverlay.layoutRouteDetailInfo.visibility = View.VISIBLE
        scheduleName = scheduleInfo.title ?: "일정 없음"
        val rawTime = scheduleInfo.startTime ?: "00:00:00"
        scheduleTime = if (rawTime.length >= 5) rawTime.take(5) else rawTime
        binding.layoutRouteDetailOverlay.tvScheduleRouteDetailName.text = scheduleName
        binding.layoutRouteDetailOverlay.tvScheduleRouteDetailTime.text = scheduleTime

        try {
            // 2. 데이터 파싱
            val assembledRouteResponse = RouteResponse(
                totalDistance = routeInfo.totalDistance,
                totalTime = routeInfo.totalTime,
                arrivalTime = routeInfo.arrivalTime ?: "${scheduleInfo.endDate}T${scheduleInfo.endTime}",
                departureTime = routeInfo.departureTime ?: "${scheduleInfo.startDate}T${scheduleInfo.startTime}",

                // 변환 없이 바로 대입!
                routeDetails = routeInfo.routeDetails ?: emptyList()
            )

            // 3. 시작/도착 좌표 추출 (경로의 첫번째와 마지막 포인트)
            val firstDetail = assembledRouteResponse.routeDetails.firstOrNull()
            val lastDetail = assembledRouteResponse.routeDetails.lastOrNull()

            if (firstDetail != null && lastDetail != null) {
                this.startLatLng = LatLng(firstDetail.startLat, firstDetail.startLng)
                this.endLatLng = LatLng(lastDetail.endLat, lastDetail.endLng)
            }

            // 4. UI 텍스트 설정
            scheduleColor = scheduleInfo.color ?: "#F4F4F4"

            // 5. 지도에 경로 그리기
//            val mapFrag = childFragmentManager.findFragmentById(R.id.route_map_fcv) as? MapFragment
//            mapFrag?.drawRouteOnMap(assembledRouteResponse, startLatLng, endLatLng)


            try {
                val colorInt = Color.parseColor(scheduleColor)
                binding.layoutRouteDetailOverlay.viewColorDotRouteDetail.backgroundTintList = ColorStateList.valueOf(colorInt)
            } catch (e: Exception) {
                binding.layoutRouteDetailOverlay.viewColorDotRouteDetail.backgroundTintList = ColorStateList.valueOf(Color.RED)
            }

            // 7. 바텀시트(상세 경로 리스트) 설정
            val bottomSheetView = binding.layoutRouteDetailOverlay.root.findViewById<View>(R.id.sheet_route_detail)
            val behavior = BottomSheetBehavior.from(bottomSheetView)

            behavior.isHideable = false
            behavior.state = BottomSheetBehavior.STATE_COLLAPSED
//            behavior.peekHeight = (250 * resources.displayMetrics.density).toInt()
            behavior.peekHeight = getScreenHeightPercentage(0.3f)

            routeViewModel.updateScheduleForAdapter(assembledRouteResponse)
            // 헬퍼를 이용해 리사이클러뷰 데이터 채우기
            val realtimeParams = RouteDetailHelper.setupData(requireContext(), bottomSheetView, assembledRouteResponse, routeInfo.destName ?: "", routeInfo.originName ?: "", childFragmentManager)

            startRealtimePolling(realtimeParams)
            bottomSheetView.post {
                val mapFrag = childFragmentManager.findFragmentById(R.id.route_map_fcv) as? MapFragment

                // 1. 현재 바텀시트가 올라온 실제 높이 계산
                val parentHeight = (bottomSheetView.parent as View).height
                val currentSheetHeight = parentHeight - bottomSheetView.top

                // 2. 지도 패딩 먼저 설정 (지도의 중심을 시트 위로 올림)
                mapFrag?.setMapPadding(currentSheetHeight)

                // 3. 내 위치 버튼 위치 조정 및 최상단 이동
                mapFrag?.updateButtonTranslation(currentSheetHeight.toFloat())
                val btn = mapFrag?.view?.findViewById<View>(R.id.btn_go_my_location)
                btn?.bringToFront()
                btn?.alpha = 1f

                // 4. 경로 그리기 (패딩이 적용된 상태에서 마커 중앙 정렬)
                mapFrag?.drawRouteOnMap(assembledRouteResponse, startLatLng, endLatLng)

                // 좌표 초기화
                startLatLng = null
                endLatLng = null
            }

        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("RouteFragment", "Schedule Overlay Error: ${e.message}")
        }
    }

    private fun observeRouteViewModel() {
        // 결과 데이터 관찰
        routeViewModel.routeResult.observe(viewLifecycleOwner) { routes ->
            Log.d("RouteDebug", "데이터 수신: ${routes?.size}개")
            if (routes.isNullOrEmpty()) {
                android.widget.Toast.makeText(requireContext(), "검색 기록 없음", android.widget.Toast.LENGTH_SHORT).show()
                // 1. 검색 결과가 없을 때 -> '결과 없음' 뷰 표시
                binding.layoutNoSearchResult.visibility = View.VISIBLE
                binding.layoutNoSearchResult.bringToFront()

            }else{
                binding.layoutNoSearchResult.visibility = View.GONE
                val fragment = childFragmentManager.findFragmentByTag("ROUTE_RESULT") as? RouteResultFragment
                fragment?.updateRoutes(routes, selectedEndPlace?.first ?: "도착지 없음")

            }

        }

        // 에러 메시지 관찰
        routeViewModel.errorMessage.observe(viewLifecycleOwner) { msg ->
            if (msg.isNotEmpty()) {
                android.util.Log.d("RouteFragment", "검색 에러 발생: $msg")

                binding.layoutNoSearchResult.visibility = View.VISIBLE
                binding.layoutNoSearchResult.bringToFront()
            }
        }

        routeViewModel.routeOnlySchedule.observe(viewLifecycleOwner) { data ->
            if (currentEntryMode != EntryMode.MAIN) return@observe
            showMainEntryOverlay(data)
            return@observe
            // Single source of truth is routeViewModel.routeOnlySchedule.
            if (data != null) {
                // 1. 데이터가 있으면: hasSchedule 켜고, 오버레이 표시
                hasSchedule = true
                showDefaultScheduleOverlay(data)
            } else {
                // 2. 데이터가 없으면: hasSchedule 끄기
                hasSchedule = false
                // (경로 검색 중이거나 상세 정보를 보고 있을 때는 숨기면 안 됨)
                if (currentEntryMode == EntryMode.MAIN) {
                    binding.layoutRouteDetailOverlay.root.visibility = View.VISIBLE
                    binding.layoutRouteDetailOverlay.root.bringToFront()

                    binding.layoutRouteDetailOverlay.layoutRouteDetailInfo.visibility = View.VISIBLE

                    binding.layoutRouteDetailOverlay.tvScheduleRouteDetailName.visibility = View.VISIBLE
                    binding.layoutRouteDetailOverlay.tvScheduleRouteDetailName.text = "경로 일정 목록"

                    binding.layoutRouteDetailOverlay.tvScheduleRouteDetailTime.visibility = View.GONE
                    binding.layoutRouteDetailOverlay.viewColorDotRouteDetail.visibility = View.GONE
                    binding.layoutRouteDetailOverlay.btnRouteDetailBackDetail.visibility = View.GONE
                    binding.layoutRouteDetailOverlay.layoutRouteSelectContainer.visibility = View.GONE
                    binding.layoutRouteDetailOverlay.bottomSheetRouteDetail.visibility = View.GONE
                }
            }
        }
    }

    private fun observeSettings() {
        viewLifecycleOwner.lifecycleScope.launch {
            settingsViewModel.userSettings.collect { settings ->
                settings?.let {
                    Log.d("RouteOnboarding", "관찰된 온보딩 값: ${it.earlyArrivalTime}")
                }
            }
        }
    }



    private fun setupMainActivityListeners() {
        val mapFrag = childFragmentManager.findFragmentById(R.id.route_map_fcv) as? MapFragment
        mainBinding?.searchEt?.apply {
            setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    if (!isNetworkAvailable()) {
                        view.clearFocus()
                        hideKeyboard()
                        // [추가] 다이얼로그 띄우기
                        NetworkErrorDialog(requireContext()) {
                            requestFocus() // 새로고침 시 다시 포커스 요청
                        }.show()
                        return@setOnFocusChangeListener
                    }
                    enterSearchMode()
                }
            }

            setOnClickListener { view ->
                if (!isNetworkAvailable()) {
                    view.clearFocus()
                    hideKeyboard()
                    NetworkErrorDialog(requireContext()) {
                        requestFocus()
                    }.show()
                    return@setOnClickListener
                }
                if (!hasFocus()) {
                    requestFocus()
                } else {
                    enterSearchMode()
                }
            }
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
    private fun FinalfetchRouteData(){
        if (!isNetworkAvailable()) {
            NetworkErrorDialog(requireContext()) {
                FinalfetchRouteData()
            }.show()
            return
        }

        lifecycleScope.launch {
            if (currentEntryMode == EntryMode.SCHEDULE_ROUTE && earlyArriveTime == -1) {
                if (earlyArriveTime == -1) {
                    var count = 0
                    while (settingsViewModel.userSettings.value == null && count < 10) {
                        delay(100) // 0.1초씩 대기
                        count++
                    }

                    val settings = settingsViewModel.userSettings.value
                    earlyArriveTime = settings?.earlyArrivalTime ?: 10 // 로드 실패 시 기본값 10

                    updateRequestSearchTimeWithEarlyArrival(earlyArriveTime)
                    Log.d("RouteOnboarding", "데이터 로드 후 반영 완료: $earlyArriveTime 분")
                } else {
                    updateRequestSearchTimeWithEarlyArrival(earlyArriveTime)
                }
            }
            // 출발지 좌표가 없다면 ID로 조회
            if (startLatLng == null && selectedStartPlace != null) {
                startLatLng = fetchLatLngFromPlaceId(selectedStartPlace!!.second)
            }

            // 도착지 좌표가 없다면 ID로 조회
            if (endLatLng == null && selectedEndPlace != null) {
                endLatLng = fetchLatLngFromPlaceId(selectedEndPlace!!.second)
            }

            // 좌표 확보 후 API 호출
            Log.d("Route", "66${selectedStartPlace.toString()}--${selectedEndPlace.toString()} +${startLatLng.toString()}--${endLatLng.toString()} ")
            fetchRouteData()
        }
    }
    private fun updateRequestSearchTimeWithEarlyArrival(minutes: Int) {
        try {
            val safeTime = if (scheduleTime.length == 5) "$scheduleTime:00" else scheduleTime
            val dateTimeString = "${scheduleDate}T$safeTime"

            // 2. 파서 형식을 명시적으로 지정하여 에러 방지
            val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
            val scheduledDateTime = LocalDateTime.parse(dateTimeString, formatter)

            val adjustedDateTime = scheduledDateTime.minusMinutes(minutes.toLong())

            // 3. 서버 전송용 시간(UTC) 업데이트
            // 기기 로컬 시간대(KST)를 기준으로 UTC로 변환하여 ISO 8601 형식으로 포맷팅
            requestSearchTime = adjustedDateTime.atZone(ZoneId.systemDefault())
                .withZoneSameInstant(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"))

            // 4. UI 텍스트(tvTimeFilter) 업데이트
            val today = LocalDate.now()
            val targetDate = scheduledDateTime.toLocalDate()

            val datePrefix = when (targetDate) {
                today -> "오늘"
                today.plusDays(1) -> "내일"
                else -> targetDate.format(DateTimeFormatter.ofPattern("MM월 dd일"))
            }

            val timeText = adjustedDateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
            binding.layoutRouteInputHeader.tvTimeFilter.text = "$datePrefix $timeText 도착"

            Log.d("RouteOnboarding", "시간 재계산 완료: $minutes 분 차감 반영됨 ($requestSearchTime)")
        } catch (e: Exception) {
            Log.e("RouteOnboarding", "시간 재계산 실패: ${e.message}")
        }
    }

    private fun fetchRouteData() {

        val start = startLatLng
        val end = endLatLng

        if (start == null || end == null) {
            return
        }

        if(requestSearchTime.isNullOrEmpty()){
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            requestSearchTime = sdf.format(Date())
        }

        val request = RouteSearchRequest(
            originLat = start.latitude,
            originLng = start.longitude,
            destLat = end.latitude,
            destLng = end.longitude,
            arrivalTime = if (!isStart) requestSearchTime else null,
            departureTime = if (isStart) requestSearchTime else null,
            transitType = currentTransitType ?: "SUBWAY",
            searchWay = currentSortOption.apiValue?:"EFFICIENT"
        )

        Log.d("RouteApi", "================= API REQUEST START =================")
        Log.d("RouteApi", "Origin      : ${request.originLat}, ${request.originLng}")
        Log.d("RouteApi", "Dest        : ${request.destLat}, ${request.destLng}")
        Log.d("RouteApi", "Time        : ${request.departureTime ?: request.arrivalTime} (IsDeparture: $isStart + $requestSearchTime)")
        Log.d("RouteApi", "TransitType : ${request.transitType ?: "ALL"}")
        Log.d("RouteApi", "SortOption  : ${request.searchWay}")
        Log.d("RouteApi", "Full Request: $request")
        Log.d("RouteApi", "=====================================================")

        val accessToken = authDataStore.getAccessToken().orEmpty()
        val token = if (accessToken.isNotEmpty() && !accessToken.startsWith("Bearer ")) {
            "Bearer $accessToken"
        } else {
            accessToken
        }
        if (token.isEmpty()) return

        routeViewModel.searchRoutes(token, request)
    }

    private suspend fun fetchLatLngFromPlaceId(placeId: String): LatLng? = suspendCancellableCoroutine { continuation ->
        if (placeId.isEmpty()) {
            continuation.resume(null, null)
            return@suspendCancellableCoroutine
        }
        if (!::placesClient.isInitialized) {
            Log.e("PlaceApi", "PlacesClient not initialized")
            continuation.resume(null, null)
            return@suspendCancellableCoroutine
        }

        // 위도/경도 정보만 요청
        val placeFields = listOf(Place.Field.LAT_LNG)
        val request = FetchPlaceRequest.newInstance(placeId, placeFields)

        placesClient.fetchPlace(request)
            .addOnSuccessListener { response ->
                val latLng = response.place.latLng
                if (latLng != null) {
                    Log.d("PlaceApi", "Success fetch LatLng: $latLng for ID: $placeId")
                    continuation.resume(latLng, null)
                } else {
                    Log.e("PlaceApi", "LatLng is null for ID: $placeId")
                    continuation.resume(null, null)
                }
            }
            .addOnFailureListener { exception ->
                Log.e("PlaceApi", "Failed to fetch place: ${exception.message}")
                continuation.resume(null, null)
            }
    }

    fun startScheduleMode() {
        if (_binding == null || !isAdded || view == null) return

        currentEntryMode = EntryMode.SCHEDULE

        enterSearchMode()
    }

    fun startScheduleRouteMode(intent: android.content.Intent? = activity?.intent) {
        if (_binding == null || !isAdded || view == null || intent == null) return

        currentEntryMode = EntryMode.SCHEDULE_ROUTE

        val nameExtra = intent.getStringExtra("SCHEDULE_NAME")
        scheduleName = if(nameExtra.isNullOrBlank()) "일정명" else nameExtra
        scheduleColor = intent.getStringExtra("SCHEDULE_COLOR") ?: "#DC354B"
        var tmpDate = intent.getStringExtra("SCHEDULE_DATE") ?: "2032-12-02"
        scheduleDate = tmpDate
        var tmpTime = intent.getStringExtra("SCHEDULE_TIME") ?: "00:00:00"
        var tmpEndTime = intent.getStringExtra("SCHEDULE_END_TIME") ?: "00:00:00"
        scheduleTime = tmpTime?.substring(0, 5) ?: ""
        scheduleEndTime = tmpEndTime.substring(0, 5)
        val combinedTimeStr = "$tmpDate $tmpTime"

        val inputSdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val outputSdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        outputSdf.timeZone = TimeZone.getTimeZone("UTC")
        val date = inputSdf.parse(combinedTimeStr)
        requestSearchTime = outputSdf.format(date ?: Date())

        val startName = intent.getStringExtra("START_NAME")
        val startLatVal = intent.getDoubleExtra("START_LAT", Double.NaN)
        val startLngVal = intent.getDoubleExtra("START_LNG", Double.NaN)

        if (!startLatVal.isNaN() && !startLngVal.isNaN()) {
            startLatLng = LatLng(startLatVal, startLngVal)
            // 이름이 있으면 그 이름, 없으면 "지정된 위치", ID는 없으므로 빈값 처리
            selectedStartPlace = Pair(startName ?: "지정된 위치", "")
        } else {
            startLatLng = null
            selectedStartPlace = null
        }

        val endName = intent.getStringExtra("END_NAME")
        val endLatVal = intent.getDoubleExtra("END_LAT", Double.NaN)
        val endLngVal = intent.getDoubleExtra("END_LNG", Double.NaN)

        if (!endLatVal.isNaN() && !endLngVal.isNaN()) {
            endLatLng = LatLng(endLatVal, endLngVal)
            selectedEndPlace = Pair(endName ?: "지정된 위치", "")
        } else {
            endLatLng = null
            selectedEndPlace = null
        }
        isStart = false

        binding.layoutRouteInputHeader.tvRouteStart.text = selectedStartPlace?.first ?: ""
        binding.layoutRouteInputHeader.tvRouteEnd.text = selectedEndPlace?.first ?: ""

        updateClearButtonVisibility()

        earlyArriveTime = intent.getIntExtra("EARLY_ARRIVE_TIME", -1)

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

        binding.layoutRouteDetailOverlay.root.visibility = View.GONE

        showSearchRouteFragment()
    }

    // 출발/도착 눌렀을 때 (디테일에서)
    fun onLocationSelected(itemName: String, placeId: String, isStart: Boolean) {
        exitPoiMode()
        val detailFrag = childFragmentManager.findFragmentByTag("DETAIL")
        if (detailFrag != null) {
            childFragmentManager.popBackStackImmediate("DETAIL", androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }

        isDetailFromRecommend = false
        historyFragment.setRouteOptionsVisible(false)
        mainBinding?.mainBnv?.visibility = View.GONE

        if (isStart) {
            if(placeId == selectedEndPlace?.second || itemName == selectedEndPlace?.first){
                swapLocations()
            }
            Log.d("Route", "onLocationSelected 출발지로!")
            selectedStartPlace = Pair(itemName, placeId)
            lifecycleScope.launch {
                startLatLng = fetchLatLngFromPlaceId(selectedStartPlace!!.second)
            }
            Log.d("Route", "${selectedStartPlace.toString()}--${selectedEndPlace.toString()}  ")

            binding.layoutRouteInputHeader.tvRouteStart.setText(itemName)
            updateClearButtonVisibility()
        } else {
            if(placeId == selectedStartPlace?.second || itemName == selectedEndPlace?.first){
                swapLocations()
            }
            selectedEndPlace = Pair(itemName, placeId)
            lifecycleScope.launch {
                endLatLng = fetchLatLngFromPlaceId(selectedEndPlace!!.second)
            }
            binding.layoutRouteInputHeader.tvRouteEnd.setText(itemName)
            updateClearButtonVisibility()
        }

        if (::bottomSheetBehavior.isInitialized) {
            bottomSheetBehavior.isHideable = true
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            bottomSheetBehavior.peekHeight = 0
        }

        binding.layoutRouteInputHeader.root.visibility = View.VISIBLE
        binding.layoutRouteInputHeader.root.bringToFront()
        mainBinding?.mainToolbar?.visibility = View.GONE
        mainBinding?.mainBackIv?.visibility = View.GONE
        mainBinding?.searchEt?.setText("")

        showSearchRouteFragment()
    }

    fun onScheduleLocationSelected(name: String, placeId: String) {

        showNameConfirmDialog(name) { finalName ->
            if(isBookmarkSearchMode){
                handleBookmarkSingleRegistration(finalName, placeId)

                val transaction = childFragmentManager.beginTransaction()
                val detailFrag = childFragmentManager.findFragmentByTag("DETAIL")
                val listFrag = childFragmentManager.findFragmentByTag(LocationBottomSheetFragment.TAG)
                if (detailFrag != null) transaction.remove(detailFrag)
                if (listFrag != null) transaction.remove(listFrag)
                transaction.commitAllowingStateLoss()

                if (::bottomSheetBehavior.isInitialized) {
                    bottomSheetBehavior.isHideable = true
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                    bottomSheetBehavior.peekHeight = 0
                }
                binding.bottomSheetContainer.visibility = View.GONE

                val mapFrag = childFragmentManager.findFragmentById(R.id.route_map_fcv) as? MapFragment


                mapFrag?.setMapPadding(0)
                mapFrag?.clearMarkers()

                binding.layoutMapSelectOverlay.root.visibility = View.GONE
                binding.routeSearchFcv.visibility = View.VISIBLE
            }else{
                val resultIntent = android.content.Intent().apply {
                    putExtra("placeName", finalName)
                    putExtra("placeId", placeId)
                }

                // 2. 결과 설정 (RESULT_OK)
                requireActivity().setResult(android.app.Activity.RESULT_OK, resultIntent)

                binding.routeMapFcv.visibility = View.GONE
                requireActivity().finish()
            }
        }
    }

    private fun handleBookmarkSingleRegistration(name: String, placeId: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            if (selectedGroupId != null) {
                // todo 일반 장소 저장 로직 -> 다시 생각해보니 필요없어 보이긴함; 무슨 생각이 있긴 했겠지?
            } else {
                val type = if (bookmarkTarget == BookmarkTarget.HOME) "HOME" else "WORK"
                val myPlace = MyPlace(
                    type = type,
                    name = name,
                    placeId = placeId
                )
                searchViewModel.insertMyPlace(myPlace)
            }

            withContext(Dispatchers.Main) {
                bookmarkTarget = BookmarkTarget.NONE
                selectedGroupId = null

                exitBookmarkSearchMode()
            }

        }
    }

    private fun showNameConfirmDialog(originalName: String, onConfirm: (String) -> Unit) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_confirm_place_name, null)

        // 2. 일반 AlertDialog 생성
        val alertDialog = android.app.AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        val tvFullAddress = dialogView.findViewById<android.widget.TextView>(R.id.tv_dialog_origin_place_name)
        val etPlaceName = dialogView.findViewById<android.widget.EditText>(R.id.et_dialog_place_name)
        val btnCancel = dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_dialog_single_place_cancel)
        val btnSave = dialogView.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.btn_dialog_single_place_save)

        tvFullAddress.text = originalName
        etPlaceName.requestFocus()


        btnCancel.setOnClickListener {
            alertDialog.dismiss()
        }

        btnSave.setOnClickListener {
            hideKeyboard()
            val finalName = etPlaceName.text.toString().trim()
            if (finalName.isNotEmpty()) {
                onConfirm(finalName)
                alertDialog.dismiss()
            }
        }

        alertDialog.show()

        showKeyBoard()
    }



    private fun setupMapSelectListeners() {
        // 확인 버튼 클릭 시
        binding.layoutMapSelectOverlay.btnMapSelectConfirm.setOnClickListener {
            val tempName = selectedOnMapPlace?.first ?: binding.layoutMapSelectOverlay.tvMapSelectName.text.toString()
            val existingId = selectedOnMapPlace?.second ?: ""

            if(isBookmarkSearchMode){
                lifecycleScope.launch {
                    val finalId = if (existingId.isEmpty() && currentMapCenter != null) {
                        getNearbyPlaceId(currentMapCenter!!) ?: ""
                    } else {
                        existingId
                    }

                    onScheduleLocationSelected(tempName, finalId)
                }
            }else{
                val selectedItem = SearchItem(
                    placeId = existingId,
                    name = tempName,
                    lat = currentMapCenter?.latitude ?: 0.0,
                    lng = currentMapCenter?.longitude ?: 0.0,
                    address = currentMapAddress ?: "",
                    category = currentMapCategory ?: "",
                    distance = ""
                )

                saveRecentPlace(selectedItem)
                onLocationSelected(tempName, existingId, isSelectingStart)
                startLatLng = currentMapCenter

                binding.layoutMapSelectOverlay.root.visibility = View.GONE
                val mapFrag = childFragmentManager.findFragmentById(R.id.route_map_fcv) as? MapFragment
                mapFrag?.setMyLocationButtonVisibility(true)

                selectedOnMapPlace = null
            }
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

        binding.layoutRouteDetailOverlay.btnRealtimeRefresh.setOnClickListener {
            currentRealtimeParams?.let { params ->
                startRealtimePolling(params)
            }
        }

        binding.layoutRouteDetailOverlay.layoutRouteDetailInfo.setOnClickListener {
            if (currentEntryMode == EntryMode.SCHEDULE_ROUTE) {
                return@setOnClickListener
            }
            if (!isNetworkAvailable()) {
                NetworkErrorDialog(requireContext()) {
                    binding.layoutRouteDetailOverlay.layoutRouteDetailInfo.performClick()
                }.show()
                return@setOnClickListener
            }

            routeViewModel.fetchAllRouteSchedules()

            routeViewModel.routeScheduleList.observe(viewLifecycleOwner, object : androidx.lifecycle.Observer<List<RouteOnlyScheduleData>> {
                override fun onChanged(list: List<RouteOnlyScheduleData>) {
                    if (list.isNotEmpty()) {
                        showGroupedRouteBottomSheet(list) // 아래 만든 함수 호출
                        routeViewModel.routeScheduleList.removeObserver(this)
                    }
                }
            })
        }
    }

    private fun showGroupedRouteBottomSheet(dataList: List<RouteOnlyScheduleData>) {
        val dialog = BottomSheetDialog(requireContext())
        // 바텀시트 레이아웃 (rv_saved_routes가 들어있는 XML)
        val sheetBinding = com.example.pace.databinding.BottomSheetRouteScheduleListBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)

        // 1. 데이터 그룹화 로직 (startDate 기준)
        val groupedList = mutableListOf<RouteScheduleItem>()
        // 날짜순 정렬
        val sortedList = dataList.sortedBy { it.scheduleInfo.startDate }
        var lastDate = ""

        sortedList.forEach { item ->
            val dateStr = item.scheduleInfo.startDate ?: "날짜 미정"

            // 날짜가 달라지면 헤더 추가
            if (dateStr != lastDate) {
                // 날짜 포맷팅 (2026-02-18 -> 2월 18일 (수))
                val formattedDate = try {
                    val date = LocalDate.parse(dateStr)
                    date.format(DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN))
                } catch (e: Exception) {
                    dateStr
                }
                groupedList.add(RouteScheduleItem.DateHeader(formattedDate))
                lastDate = dateStr
            }
            // 내용 추가
            groupedList.add(RouteScheduleItem.ScheduleContent(item))
        }

        // 2. 어댑터 연결
        val adapter = RouteScheduleListAdapter(groupedList) { selectedData ->
            hasSchedule = true

            showDefaultScheduleOverlay(selectedData)
            dialog.dismiss()
        }

        sheetBinding.rvSavedRoutes.apply {
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
            this.adapter = adapter
        }

        // 바텀시트 높이 설정
        val bottomSheetBehavior = BottomSheetBehavior.from(sheetBinding.root.parent as View)
//        bottomSheetBehavior.peekHeight = (400 * resources.displayMetrics.density).toInt()
        bottomSheetBehavior.peekHeight = getScreenHeightPercentage(0.5f)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED

        dialog.show()
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
        mainBinding?.mainBackIv?.visibility = View.GONE
        mainBinding?.mainBnv?.visibility = View.GONE

        if (::bottomSheetBehavior.isInitialized) {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }

        binding.layoutMapSelectOverlay.root.visibility = View.VISIBLE
        binding.layoutMapSelectOverlay.root.bringToFront()

        if(isBookmarkSearchMode == true){
            binding.layoutMapSelectOverlay.tvMapSelectInfo.text = "일정 선택"
        }else if(isSelectingStart){
            binding.layoutMapSelectOverlay.tvMapSelectInfo.text = "출발지 선택"
        }else{
            binding.layoutMapSelectOverlay.tvMapSelectInfo.text = "도착지 선택"
        }

        val mapFrag = childFragmentManager.findFragmentById(R.id.route_map_fcv) as? MapFragment
        mapFrag?.initMapSelectionMode()
        mapFrag?.clearRoute()

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

            val mapFrag = childFragmentManager.findFragmentById(R.id.route_map_fcv) as? MapFragment
            binding.layoutRouteDetailOverlay.root.visibility = View.VISIBLE
            binding.layoutRouteDetailOverlay.layoutRouteDetailInfo.visibility = View.VISIBLE
            binding.layoutRouteDetailOverlay.btnRouteDetailBackDetail.visibility = View.VISIBLE
            binding.layoutRouteDetailOverlay.layoutRouteSelectContainer.visibility = View.VISIBLE
            binding.layoutRouteDetailOverlay.bottomSheetRouteDetail.visibility = View.VISIBLE
            binding.layoutRouteDetailOverlay.root.bringToFront()

            binding.layoutRouteDetailOverlay.btnRouteSelect.setOnClickListener {
                onRouteSelectedFinal(item)
            }

            if(currentEntryMode == EntryMode.SCHEDULE_ROUTE){
                binding.layoutRouteDetailOverlay.layoutRouteDetailInfo.visibility = View.VISIBLE
            }else{
                binding.layoutRouteDetailOverlay.layoutRouteDetailInfo.visibility = View.GONE
            }
            try {
                val colorInt = Color.parseColor(scheduleColor)

                binding.layoutRouteDetailOverlay.viewColorDotRouteDetail.backgroundTintList = ColorStateList.valueOf(colorInt)

            } catch (e: Exception) {
                e.printStackTrace()
                binding.layoutRouteDetailOverlay.viewColorDotRouteDetail.backgroundTintList = ColorStateList.valueOf(Color.RED)
            }
            binding.layoutRouteDetailOverlay.tvScheduleRouteDetailName.text = " $scheduleName "
            binding.layoutRouteDetailOverlay.tvScheduleRouteDetailTime.text = "$scheduleTime - $scheduleEndTime"

            val bottomSheetView = binding.layoutRouteDetailOverlay.root.findViewById<View>(R.id.sheet_route_detail)
            val detailBehavior = BottomSheetBehavior.from(bottomSheetView) // 지역 변수 명확히 사용

            detailBehavior.apply {
                isHideable = false
                // peekHeight를 전역 변수가 아닌 detailBehavior에 직접 설정
                // 0.3f(30%)도 높다면 0.2f(20%) 정도로 조절하세요.
                peekHeight = getScreenHeightPercentage(0.4f)
                state = BottomSheetBehavior.STATE_COLLAPSED // 강제로 접힌 상태 설정
            }

            val realtimeParams = RouteDetailHelper.setupData(requireContext(),bottomSheetView, item, selectedEndPlace?.first ?: "", selectedStartPlace?.first ?: "", parentFragmentManager)
            startRealtimePolling(realtimeParams)

            bottomSheetView.post {
                val parentHeight = (bottomSheetView.parent as View).height
                val currentSheetHeight = parentHeight - bottomSheetView.top

                mapFrag?.setMapPadding(currentSheetHeight)
                mapFrag?.updateButtonTranslation(currentSheetHeight.toFloat())

                val btn = mapFrag?.view?.findViewById<View>(R.id.btn_go_my_location)
                btn?.bringToFront()

                mapFrag?.drawRouteOnMap(item, startLatLng, endLatLng)
            }
        }
    }

    fun onRouteSelectedFinal(item: RouteResponse){
        val now = java.time.LocalDateTime.now()

        try {
            val rawDepartureTime = java.time.LocalDateTime.parse(item.departureTime)
            val departureTimeKst = rawDepartureTime.plusHours(9)

            // 로그로 보정된 시간 확인
            android.util.Log.d("RouteTimeCheck", "========================================")
            android.util.Log.d("RouteTimeCheck", "현재 시간(KST): $now")
            android.util.Log.d("RouteTimeCheck", "서버 원본(UTC): $rawDepartureTime")
            android.util.Log.d("RouteTimeCheck", "보정된 출발 시간(KST): $departureTimeKst")
            android.util.Log.d("RouteTimeCheck", "이미 지났나?: ${now.isAfter(departureTimeKst)}")
            android.util.Log.d("RouteTimeCheck", "========================================")

            // 보정된 시간(KST)을 기준으로 비교
            if (now.isAfter(departureTimeKst)) {
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("선택 불가")
                    .setMessage("이미 출발 시간이 지난 경로입니다.\n다른 경로를 선택해주세요.")
                    .setPositiveButton("확인", null)
                    .show()
                return
            }
        } catch (e: Exception) {
            android.util.Log.e("RouteSelection", "시간 파싱 에러: ${item.departureTime}")
        }
        if(currentEntryMode == EntryMode.SCHEDULE_ROUTE){
            val resultIntent = android.content.Intent().apply {
                putExtra("START_NAME", binding.layoutRouteInputHeader.tvRouteStart.text)
                putExtra("START_LAT", startLatLng?.latitude)
                putExtra("START_LNG", startLatLng?.longitude)
                putExtra("END_NAME", binding.layoutRouteInputHeader.tvRouteEnd.text)
                putExtra("END_LAT", endLatLng?.latitude)
                putExtra("END_LNG", endLatLng?.longitude)
                putExtra("EARLY_ARRIVE_TIME", earlyArriveTime)
                putExtra("ROUTE_DETAIL", Gson().toJson(item))
            }

            requireActivity().setResult(android.app.Activity.RESULT_OK, resultIntent)
            requireActivity().finish()
        }else {
            val intent = android.content.Intent(requireContext(), AddScheduleActivity::class.java).apply {
                putExtra("START_NAME", binding.layoutRouteInputHeader.tvRouteStart.text)
                putExtra("END_NAME", binding.layoutRouteInputHeader.tvRouteEnd.text)
                putExtra("EARLY_ARRIVE_TIME", earlyArriveTime)
                putExtra("ROUTE_DETAIL", Gson().toJson(item))

                // 일반 일정이 아닌 '경로 일정' 탭으로 바로 보내기 위한 플래그
                putExtra("OPEN_ROUTE_TAB", true)
            }
            startActivity(intent)
        }
    }

    private fun enterSearchMode() {
        exitPoiMode()
        val mapFrag = childFragmentManager.findFragmentById(R.id.route_map_fcv) as? MapFragment
        mapFrag?.clearMap()
        binding.layoutRouteDetailOverlay.root.visibility = View.GONE

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
//            targetFragment.setRouteOptionsVisible(isRoutePlan)
            if(isBookmarkSearchMode){
                targetFragment.setRouteOptionsVisible(true)
            }
            else{
                targetFragment.setRouteOptionsVisible(isRoutePlan)
            }

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
        exitPoiMode()
        hideKeyboard()
        mainBinding?.searchEt?.clearFocus()
        mainBinding?.searchEt?.setText("")

        searchJob?.cancel()

        selectedStartPlace = null
        startLatLng = null
        selectedEndPlace = null
        endLatLng = null
        selectedOnMapPlace = null
        if(currentEntryMode == EntryMode.ROUTE_PLAN){
            currentEntryMode = EntryMode.MAIN
        }
        isDetailFromRecommend = false
        sessionToken = null
        startLatLng = null
        endLatLng = null
        bookmarkTarget = BookmarkTarget.NONE

        binding.layoutRouteInputHeader.tvRouteStart.setText("")
        binding.layoutRouteInputHeader.tvRouteEnd.setText("")

        val transaction = childFragmentManager.beginTransaction()
        if (historyFragment.isAdded) transaction.hide(historyFragment)
        if (recommendFragment.isAdded) transaction.hide(recommendFragment)
        transaction.commitAllowingStateLoss()

        mainBinding?.mainSearchLl?.visibility = View.VISIBLE
        binding.routeSearchFcv.visibility = View.GONE
        mainBinding?.mainBackIv?.visibility = View.VISIBLE
        mainBinding?.mainToolbar?.visibility = View.VISIBLE
//        if(currentEntryMode == EntryMode.SCHEDULE_ROUTE || currentEntryMode == EntryMode.SCHEDULE){
//            mainBinding?.mainBnv?.visibility = View.GONE
//        }else{
//            mainBinding?.mainBnv?.visibility = View.VISIBLE
//        }
//        mainBinding?.mainBnv?.visibility = View.VISIBLE

        binding.routeSearchFcv.visibility = View.GONE
        binding.layoutBookmarkHeader.root.visibility = View.GONE
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
                    if (isBookmarkSearchMode) {
                        currentMyLocation = latLng
                        val currentPlaceId = getNearbyPlaceId(latLng)
                        onScheduleLocationSelected(fullAddress, currentPlaceId?:"")
                    } else {
                        applyCurrentLocationSelection(fullAddress, latLng)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    applyCurrentLocationSelection("현재 위치", latLng)
                }
            }
        }
    }

    private suspend fun getNearbyPlaceId(latLng: LatLng): String? = suspendCancellableCoroutine { continuation ->
        val circle = CircularBounds.newInstance(latLng, 30.0)
        val placeFields = listOf(Place.Field.ID)

        val searchNearbyRequest = SearchNearbyRequest.builder(circle, placeFields)
            .setMaxResultCount(1)
            .build()

        val task = placesClient.searchNearby(searchNearbyRequest)
            .addOnSuccessListener { response ->
                val placeId = response.places.firstOrNull()?.id
                continuation.resume(placeId) {}
            }
            .addOnFailureListener { exception ->
                exception.printStackTrace()
                continuation.resume(null) {}
            }

        continuation.invokeOnCancellation {}
    }

    private fun applyCurrentLocationSelection(address: String, latLng: LatLng) {
        if (isSelectingStart) {
            if(selectedEndPlace?.first == address){
                swapLocations()
            }
            selectedStartPlace = Pair(address, "내 위치")
            startLatLng = latLng
            binding.layoutRouteInputHeader.tvRouteStart.text = address
            updateClearButtonVisibility()
        } else {
            if(selectedStartPlace?.first == address){
                swapLocations()
            }
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

    fun onSavedPlaceClick(placeId: String) {
        val ctx = context ?: return

        if(binding.layoutBookmarkHeader.root.visibility == View.VISIBLE){
            return
        }

        if (!::placesClient.isInitialized) {
            return
        }

        val placeFields = listOf(
            Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG,
            Place.Field.ADDRESS, Place.Field.TYPES, Place.Field.PHOTO_METADATAS,
            Place.Field.BUSINESS_STATUS, Place.Field.OPENING_HOURS
        )
        val request = FetchPlaceRequest.newInstance(placeId, placeFields)

        placesClient.fetchPlace(request).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val place = task.result.place

                val item = SearchItem(
                    name = place.name ?: "",
                    placeId = place.id ?: placeId,
                    lat = place.latLng?.latitude ?: 0.0,
                    lng = place.latLng?.longitude ?: 0.0,
                    address = place.address ?: "",
                    category = convertTypeToKorean(place.types?.map { it.toString().lowercase() } ?: emptyList()),
                    distance = calculateDistance(place.latLng),
                    openStatus = getPlaceStatus(place),
                    photoMetadata = place.photoMetadatas?.firstOrNull()
                )

                hideKeyboard()
                mainBinding?.searchEt?.clearFocus()
                Log.d("DEBUG_CLICK", "Saved Place Clicked: ${item.placeId}, Name: ${item.name}")

                val mapFrag = childFragmentManager.findFragmentById(R.id.route_map_fcv) as? MapFragment
                mapFrag?.clearMarkers()

                if (isBookmarkSearchMode) {
                    onScheduleLocationSelected(item.name, item.placeId)
                    return@addOnCompleteListener
                }

                if(currentEntryMode == EntryMode.ROUTE_PLAN || currentEntryMode == EntryMode.SCHEDULE_ROUTE){
                    onLocationSelected(item.name, item.placeId, isSelectingStart)

                    val transaction = childFragmentManager.beginTransaction()
                    if (historyFragment.isAdded) transaction.hide(historyFragment)
                    if (recommendFragment.isAdded) transaction.hide(recommendFragment)
                    transaction.commitAllowingStateLoss()

                    binding.routeSearchFcv.visibility = View.GONE
                    mainBinding?.searchEt?.setText("")

                    return@addOnCompleteListener
                }
                exitSearchMode()
                isDetailFromRecommend = true
                showLocationDetail(item)

                mainBinding?.mainBackIv?.visibility = View.VISIBLE

            } else {
                val exception = task.exception
                Log.e("PlacesAPI", "Place not found: ${exception?.message}")
                android.widget.Toast.makeText(ctx, "장소 정보를 불러올 수 없습니다.", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun onPoiSelected(placeId: String) {
        if (!isNetworkAvailable()) {
            NetworkErrorDialog(requireContext()) {
                onPoiSelected(placeId)
            }.show()
            return
        }

        val ctx = context ?: return

        // 1. Google Place API 클라이언트 확인
        if (!::placesClient.isInitialized) {
            return
        }

        // 2. 가져올 정보 정의 (이름, 좌표, 주소, 타입 등)
        val placeFields = listOf(
            Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG,
            Place.Field.ADDRESS, Place.Field.TYPES, Place.Field.PHOTO_METADATAS,
            Place.Field.BUSINESS_STATUS, Place.Field.OPENING_HOURS
        )
        val request = FetchPlaceRequest.newInstance(placeId, placeFields)

        // 3. API 요청
        placesClient.fetchPlace(request).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val place = task.result.place

                // 4. 받아온 정보로 SearchItem 객체 생성
                val item = SearchItem(
                    name = place.name ?: "",
                    placeId = place.id ?: placeId,
                    lat = place.latLng?.latitude ?: 0.0,
                    lng = place.latLng?.longitude ?: 0.0,
                    address = place.address ?: "",
                    category = convertTypeToKorean(place.types?.map { it.toString().lowercase() } ?: emptyList()),
                    distance = calculateDistance(place.latLng),
                    openStatus = getPlaceStatus(place),
                    photoMetadata = place.photoMetadatas?.firstOrNull()
                )

                // 5. 키보드 숨김 및 포커스 해제
                hideKeyboard()
                mainBinding?.searchEt?.clearFocus()

                val mapFrag = childFragmentManager.findFragmentById(R.id.route_map_fcv) as? MapFragment
                mapFrag?.showTemporaryMarker(item)

                val transaction = childFragmentManager.beginTransaction()

                val existingList = childFragmentManager.findFragmentByTag(LocationBottomSheetFragment.TAG)
                if (existingList != null && !existingList.isHidden) {
                    transaction.hide(existingList)
                }

                // B. 혹시 이전에 떠 있던 '상세(DETAIL)'가 있다면 숨기기
                val existingDetail = childFragmentManager.findFragmentByTag("DETAIL")
                if (existingDetail != null && !existingDetail.isHidden) {
                    transaction.hide(existingDetail)
                }

                // C. 혹시 이전에 떠 있던 'POI 상세(POI_DETAIL)'가 있다면 제거 (새로 띄워야 하니까)
                val oldPoi = childFragmentManager.findFragmentByTag("POI_DETAIL")
                if (oldPoi != null) {
                    transaction.remove(oldPoi)
                }

                // D. 새로운 POI 바텀시트 추가 (Add)
                // *주의* addToBackStack을 쓰지 않습니다. 우리가 수동으로 관리할 거니까요.
                val newPoiFrag = LocationDetailFragment.newInstance(item, false, false)
                transaction.add(R.id.bottom_sheet_container, newPoiFrag, "POI_DETAIL")

                transaction.commitAllowingStateLoss()
                // ================= 핵심 로직 끝 =================

                // 4. 바텀시트 올라오게 설정
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
                setMapPaddingToBottomSheetHeight()

                // 5. "나 지금 POI 모드야" 라고 깃발 들기
                isPoiMode = true

                // 6. 뒤로가기 버튼 보이게 하기
                mainBinding?.mainBackIv?.visibility = View.VISIBLE

            } else {
                val exception = task.exception
                android.util.Log.e("PlacesAPI", "Place not found: ${exception?.message}")
                android.widget.Toast.makeText(ctx, "장소 정보를 불러올 수 없습니다.", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun exitPoiMode() {
        if (!isPoiMode) return // 이미 POI 모드가 아니면 패스

        val transaction = childFragmentManager.beginTransaction()

        val poiFrag = childFragmentManager.findFragmentByTag("POI_DETAIL")
        if (poiFrag != null) {
            transaction.remove(poiFrag)
        }

        transaction.commitAllowingStateLoss()

        // 3. 지도 임시 마커 삭제
        val mapFrag = childFragmentManager.findFragmentById(R.id.route_map_fcv) as? MapFragment
        mapFrag?.clearTemporaryMarker()

        // 4. 상태 플래그 끄기
        isPoiMode = false
    }

    fun onRecommendItemClick(item: SearchItem) {
        if (!isNetworkAvailable()) {
            NetworkErrorDialog(requireContext()) {
                onRecommendItemClick(item)
            }.show()
            return
        }

        hideKeyboard()
        mainBinding?.searchEt?.clearFocus()
        Log.d("DEBUG_CLICK", "Clicked item ID: ${item.placeId}, Name: ${item.name}")

        val mapFrag = childFragmentManager.findFragmentById(R.id.route_map_fcv) as? MapFragment
        mapFrag?.clearMarkers()

        if (isBookmarkSearchMode) {
            onScheduleLocationSelected(item.name, item.placeId)
            return
        }

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

        if (isDetailFromRecommend && !isPoiMode) {
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

    fun handleRecentRouteClick(route: RecentRoute){
        selectedStartPlace = Pair(route.startPlaceName, route.startPlaceId)
        selectedEndPlace = Pair(route.endPlaceName, route.endPlaceId)

        startLatLng = null
        endLatLng = null

        binding.layoutRouteInputHeader.tvRouteStart.text = route.startPlaceName
        binding.layoutRouteInputHeader.tvRouteEnd.text = route.endPlaceName

        updateClearButtonVisibility()

        hideKeyboard()
        mainBinding?.searchEt?.clearFocus()
        mainBinding?.searchEt?.setText("")

        if (::bottomSheetBehavior.isInitialized) {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }

        showSearchRouteFragment()
    }

    private fun showSearchRouteFragment() {
        exitPoiMode()
        if(currentEntryMode == EntryMode.MAIN){
            currentEntryMode = EntryMode.ROUTE_PLAN
        }
        val transaction = childFragmentManager.beginTransaction()
        binding.layoutNoSearchResult.visibility = View.GONE

        mainBinding?.mainBnv?.visibility = View.GONE
        mainBinding?.mainToolbar?.visibility = View.GONE

        val calendar = Calendar.getInstance()
        if(currentEntryMode != EntryMode.SCHEDULE_ROUTE){
            val currentTime = SimpleDateFormat("HH시 mm분", Locale.KOREAN).format(calendar.time)
            binding.layoutRouteInputHeader.tvTimeFilter.text = "오늘 $currentTime 출발"
        }
        historyFragment.setRouteOptionsVisible(false)

        val existingRouteFrag = childFragmentManager.findFragmentByTag("ROUTE_RESULT") as? RouteResultFragment
        Log.d("Route", "22${selectedStartPlace.toString()}--${selectedEndPlace.toString()} +${startLatLng.toString()}--${endLatLng.toString()} ")
        if ((selectedStartPlace != null && selectedEndPlace != null) || (startLatLng != null && endLatLng != null)) {
            saveCurrentRoute()
            if (historyFragment.isAdded) transaction.remove(historyFragment)
            if (recommendFragment.isAdded) transaction.remove(recommendFragment)

            if (existingRouteFrag != null) {
                transaction.show(existingRouteFrag)

                existingRouteFrag.onChipSelected = { type ->
                    this.currentTransitType = type
                    Log.d("Route", "33${selectedStartPlace.toString()}--${selectedEndPlace.toString()}  ")
                    FinalfetchRouteData()
                }
            } else {
                val newRouteFrag = RouteResultFragment(selectedEndPlace?.first ?: "도착지 없음")

                newRouteFrag.onChipSelected = { type ->
                    this.currentTransitType = type
                    Log.d("Route", "44${selectedStartPlace.toString()}--${selectedEndPlace.toString()}  ")
                    FinalfetchRouteData()
                }

                transaction.add(R.id.route_search_fcv, newRouteFrag, "ROUTE_RESULT")
            }

            binding.layoutRouteInputHeader.layoutFilterOptions.visibility = View.VISIBLE

            if (currentEntryMode == EntryMode.SCHEDULE_ROUTE) {
                val date = LocalDate.parse(scheduleDate)
                val today = LocalDate.now()

                val datePrefix = when (date) {
                    today -> "오늘"
                    today.plusDays(1) -> "내일"
                    else -> date.format(DateTimeFormatter.ofPattern("M월 d일"))
                }

                val timeText = scheduleTime

                binding.layoutRouteInputHeader.tvTimeFilter.text = "$datePrefix $timeText 도착"
                binding.layoutRouteInputHeader.tvSortFilter.text = currentSortOption.uiText
            }
            Log.d("Route", "55${selectedStartPlace.toString()}--${selectedEndPlace.toString()} +${startLatLng.toString()}--${endLatLng.toString()} ")
            FinalfetchRouteData()

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
        mainBinding?.mainBnv?.visibility = View.GONE
        hideKeyboard()

        if (::bottomSheetBehavior.isInitialized) {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }
    }

    private fun saveCurrentRoute() {
        val start = selectedStartPlace ?: return
        val end = selectedEndPlace ?: return

        if (start.second == end.second && start.second.isNotEmpty()) return
        if (start.first == end.first) return

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

    fun handleMyPlaceClick(myPlace: MyPlace) {
        val name = myPlace.name
        val placeId = myPlace.placeId
        Log.d("Route", "시작${selectedStartPlace.toString()}--${selectedEndPlace.toString()} +${startLatLng.toString()}--${endLatLng.toString()} ")
        when {
            currentEntryMode == EntryMode.SCHEDULE -> {
                onScheduleLocationSelected(name, placeId)
            }

            currentEntryMode == EntryMode.MAIN -> {
                isSelectingStart = true
                Log.d("Route", "메인${selectedStartPlace.toString()}--${selectedEndPlace.toString()} +${startLatLng.toString()}--${endLatLng.toString()} ")
                onLocationSelected(name, placeId, isStart = true)
            }

            else -> {
                if (selectedStartPlace == null) {
                    onLocationSelected(name, placeId, isStart = true)
                } else {
                    onLocationSelected(name, placeId, isStart = false)
                }
//                onLocationSelected(name, placeId, isSelectingStart)
            }
        }
    }

    fun enterBookmarkMode(){
        isBookmarkSearchMode = false
        wasRouteHeaderVisibleBeforeBookmark = binding.layoutRouteInputHeader.root.visibility == View.VISIBLE
        hideKeyboard()

        mainBinding?.mainToolbar?.visibility = View.GONE
        binding.layoutRouteInputHeader.root.visibility = View.GONE

        binding.layoutBookmarkHeader.root.visibility = View.VISIBLE

        val selectedTabPosition = binding.layoutBookmarkHeader.tabLayoutBookmark.selectedTabPosition
        val targetFragment = when (selectedTabPosition) {
            0 -> BookmarkHomeWorkFragment()
            1 -> BookmarkPlaceFragment()
            else -> BookmarkHomeWorkFragment()
        } as Fragment
        val tag: String? = if (selectedTabPosition == 0) "BOOKMARK_HOME" else "BOOKMARK_PLACE"

        val transaction = childFragmentManager.beginTransaction()

        transaction.replace(R.id.route_search_fcv, targetFragment, tag)
        transaction.commitAllowingStateLoss()
    }

    private fun exitBookmarkSearchMode() {
        hideKeyboard()
        mainBinding?.searchEt?.clearFocus()
        mainBinding?.searchEt?.setText("")

        historyFragment.setChipsVisibility(true)
        historyFragment.setRouteOptionsVisible(false)

        isBookmarkSearchMode = false
        bookmarkTarget = BookmarkTarget.NONE

        val transaction = childFragmentManager.beginTransaction()
        if (historyFragment.isAdded) transaction.hide(historyFragment)
        if (recommendFragment.isAdded) transaction.hide(recommendFragment)
        transaction.commitAllowingStateLoss()

        binding.layoutBookmarkHeader.root.visibility = View.VISIBLE
        binding.layoutRouteInputHeader.root.visibility = View.GONE
        mainBinding?.mainToolbar?.visibility = View.GONE

        val showTransaction = childFragmentManager.beginTransaction()
        val homeFrag = childFragmentManager.findFragmentByTag("BOOKMARK_HOME")
        val placeFrag = childFragmentManager.findFragmentByTag("BOOKMARK_PLACE")

        val selectedTab = binding.layoutBookmarkHeader.tabLayoutBookmark.selectedTabPosition
        if (selectedTab == 0) {
            homeFrag?.let { showTransaction.show(it) }
        } else {
            placeFrag?.let { showTransaction.show(it) }
        }
        showTransaction.commitAllowingStateLoss()
    }

    private fun exitBookmarkMode(){
        isBookmarkSearchMode = false
        binding.layoutBookmarkHeader.root.visibility = View.GONE

        val transaction = childFragmentManager.beginTransaction()
        val homeFrag = childFragmentManager.findFragmentByTag("BOOKMARK_HOME")
        val placeFrag = childFragmentManager.findFragmentByTag("BOOKMARK_PLACE")

        homeFrag?.let { transaction.remove(it) }
        placeFrag?.let { transaction.remove(it) }
        transaction.commitAllowingStateLoss()

        if (wasRouteHeaderVisibleBeforeBookmark) {
            binding.layoutRouteInputHeader.root.visibility = View.VISIBLE
            binding.layoutBookmarkHeader.tabLayoutBookmark.visibility = View.GONE
            mainBinding?.mainToolbar?.visibility = View.GONE


            showSearchFragment(historyFragment)

        } else {
            enterSearchMode()
        }
    }

    private fun replaceBookmarkChildFragment(fragment: Fragment, tag: String) {
        childFragmentManager.beginTransaction()
            .replace(R.id.route_search_fcv, fragment, tag)
            .setReorderingAllowed(true)
            .commitAllowingStateLoss()
    }

    private fun setupBookmarkHeaderListenrs(){
        binding.layoutBookmarkHeader.tabLayoutBookmark.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> replaceBookmarkChildFragment(BookmarkHomeWorkFragment(), "BOOKMARK_HOME") // 집/회사 탭
                    1 -> replaceBookmarkChildFragment(BookmarkPlaceFragment(), "BOOKMARK_PLACE")    // 장소 탭
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        binding.layoutBookmarkHeader.btnBookmarkBack.setOnClickListener {
            handleCustomBackClick()
        }
    }

    fun startBookmarkSearch(target: BookmarkTarget) {
        this.bookmarkTarget = target
        this.isBookmarkSearchMode = true

        val transaction = childFragmentManager.beginTransaction()
        val homeFrag = childFragmentManager.findFragmentByTag("BOOKMARK_HOME")
        val placeFrag = childFragmentManager.findFragmentByTag("BOOKMARK_PLACE")

        homeFrag?.let { if (it.isVisible) transaction.hide(it) }
        placeFrag?.let { if (it.isVisible) transaction.hide(it) }
        transaction.commitAllowingStateLoss()

        binding.layoutBookmarkHeader.root.visibility = View.GONE

        historyFragment.setChipsVisibility(false)
        historyFragment.forcePlaceFilter()
        historyFragment.setRouteOptionsVisible(true)
        enterSearchMode()
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
            swapLocations()
        }

        binding.layoutRouteInputHeader.btnRouteBack.setOnClickListener {

            if(currentEntryMode == EntryMode.SCHEDULE_ROUTE){
                binding.routeMapFcv.visibility = View.GONE
                activity?.finish()
            }
            else{
                exitSearchMode()
                realtimePollingJob?.cancel()
                binding.layoutRouteDetailOverlay.layoutRealtimeRefresh.visibility = View.GONE

                mainBinding?.mainBnv?.visibility = View.VISIBLE
                mainBinding?.mainBackIv?.visibility = View.GONE

                if (hasSchedule) {
                    showMainEntryOverlay(routeViewModel.routeOnlySchedule.value)
                } else {
                    binding.layoutRouteDetailOverlay.root.visibility = View.VISIBLE
                    binding.layoutRouteDetailOverlay.root.bringToFront()

                    binding.layoutRouteDetailOverlay.layoutRouteDetailInfo.visibility = View.VISIBLE

                    binding.layoutRouteDetailOverlay.tvScheduleRouteDetailName.visibility = View.VISIBLE
                    binding.layoutRouteDetailOverlay.tvScheduleRouteDetailName.text = "경로 일정 목록"

                    binding.layoutRouteDetailOverlay.tvScheduleRouteDetailTime.visibility = View.GONE
                    binding.layoutRouteDetailOverlay.viewColorDotRouteDetail.visibility = View.GONE
                    binding.layoutRouteDetailOverlay.btnRouteDetailBackDetail.visibility = View.GONE
                    binding.layoutRouteDetailOverlay.layoutRouteSelectContainer.visibility = View.GONE
                    binding.layoutRouteDetailOverlay.bottomSheetRouteDetail.visibility = View.GONE
                }
            }
        }

        binding.layoutRouteInputHeader.tvTimeFilter.setOnClickListener {
            if (currentEntryMode == EntryMode.SCHEDULE_ROUTE) {
                showScheduleRouteDialog()
            } else {
                showRoutePlanDialog()
            }
        }

        binding.layoutRouteInputHeader.tvSortFilter.setOnClickListener {
            showSortOptionBottomSheet()
        }
    }

    fun swapLocations() {
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
        if (isPoiMode) {
            val transaction = childFragmentManager.beginTransaction()

            val poiFrag = childFragmentManager.findFragmentByTag("POI_DETAIL")
            if (poiFrag != null) {
                transaction.remove(poiFrag)
            }

            val hiddenDetail = childFragmentManager.findFragmentByTag("DETAIL")
            val hiddenList = childFragmentManager.findFragmentByTag(LocationBottomSheetFragment.TAG)

            if (hiddenDetail != null && hiddenDetail.isHidden) {
                transaction.show(hiddenDetail)
            } else if (hiddenList != null && hiddenList.isHidden) {
                transaction.show(hiddenList)
            }

            transaction.commitAllowingStateLoss()

            val mapFrag = childFragmentManager.findFragmentById(R.id.route_map_fcv) as? MapFragment
            mapFrag?.clearTemporaryMarker()

            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
            setMapPaddingToBottomSheetHeight()

            isPoiMode = false
            return
        }

        val detailFrag = childFragmentManager.findFragmentByTag("DETAIL")
        if (detailFrag != null && detailFrag.isVisible) {
            childFragmentManager.popBackStack()
            bottomSheetBehavior.isDraggable = true
            if (isDetailFromRecommend) {
                bottomSheetBehavior.isHideable = true
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                if (!isBookmarkSearchMode) {
                    enterSearchMode()
                }
            } else {
                bottomSheetBehavior.isHideable = false
                val density = resources.displayMetrics.density
//                bottomSheetBehavior.peekHeight = (130 * density).toInt()
                bottomSheetBehavior.peekHeight = getScreenHeightPercentage(0.16f)
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

        if (isSearchMode()) {
            if (isBookmarkSearchMode) {
                exitBookmarkSearchMode()
                return
            }
        }

        if (binding.layoutBookmarkHeader.root.visibility == View.VISIBLE) {
            exitBookmarkMode()
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
            mainBinding?.mainBnv?.visibility = View.VISIBLE
            mainBinding?.mainBackIv?.visibility = View.GONE
            if (hasSchedule) {
                // 일정이 있으면 해당 일정 오버레이 표시
                showMainEntryOverlay(routeViewModel.routeOnlySchedule.value)
            } else {
                binding.layoutRouteDetailOverlay.root.visibility = View.VISIBLE
                binding.layoutRouteDetailOverlay.root.bringToFront()

                binding.layoutRouteDetailOverlay.layoutRouteDetailInfo.visibility = View.VISIBLE

                binding.layoutRouteDetailOverlay.tvScheduleRouteDetailName.visibility = View.VISIBLE
                binding.layoutRouteDetailOverlay.tvScheduleRouteDetailName.text = "경로 일정 목록"

                binding.layoutRouteDetailOverlay.tvScheduleRouteDetailTime.visibility = View.GONE
                binding.layoutRouteDetailOverlay.viewColorDotRouteDetail.visibility = View.GONE
                binding.layoutRouteDetailOverlay.btnRouteDetailBackDetail.visibility = View.GONE
                binding.layoutRouteDetailOverlay.layoutRouteSelectContainer.visibility = View.GONE
                binding.layoutRouteDetailOverlay.bottomSheetRouteDetail.visibility = View.GONE
            }
            return // 앱 종료 방지
        }

        if (binding.layoutRouteDetailOverlay.root.visibility == View.VISIBLE && currentEntryMode!=EntryMode.MAIN) {
            realtimePollingJob?.cancel()
            binding.layoutRouteDetailOverlay.layoutRealtimeRefresh.visibility = View.GONE

            binding.layoutRouteDetailOverlay.root.visibility = View.GONE

            val mapFrag = childFragmentManager.findFragmentById(R.id.route_map_fcv) as? MapFragment
            mapFrag?.clearRoute()
            mapFrag?.setMapPadding(0)
            mapFrag?.updateButtonTranslation(0f)

            binding.routeSearchFcv.visibility = View.VISIBLE
            binding.layoutRouteInputHeader.root.visibility = View.VISIBLE

            mainBinding?.mainBnv?.visibility = View.GONE
            mainBinding?.mainToolbar?.visibility = View.GONE
            return
        }

        // 2. 지도 선택 오버레이
        if (binding.layoutMapSelectOverlay.root.visibility == View.VISIBLE) {
            binding.layoutMapSelectOverlay.root.visibility = View.GONE
            selectedOnMapPlace = null
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
//                bottomSheetBehavior.peekHeight = (130 * density).toInt()
                bottomSheetBehavior.peekHeight = getScreenHeightPercentage(0.16f)
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
                    mainBinding?.mainBnv?.visibility = View.VISIBLE
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
            mainBinding?.mainBackIv?.visibility = View.GONE
            if (hasSchedule) {
                showMainEntryOverlay(routeViewModel.routeOnlySchedule.value)
                mainBinding?.mainBnv?.visibility = View.VISIBLE
            }
            else {
                binding.layoutRouteDetailOverlay.root.visibility = View.VISIBLE
                binding.layoutRouteDetailOverlay.root.bringToFront()

                binding.layoutRouteDetailOverlay.layoutRouteDetailInfo.visibility = View.VISIBLE

                binding.layoutRouteDetailOverlay.tvScheduleRouteDetailName.visibility = View.VISIBLE
                binding.layoutRouteDetailOverlay.tvScheduleRouteDetailName.text = "경로 일정 목록"

                binding.layoutRouteDetailOverlay.tvScheduleRouteDetailTime.visibility = View.GONE
                binding.layoutRouteDetailOverlay.viewColorDotRouteDetail.visibility = View.GONE
                binding.layoutRouteDetailOverlay.btnRouteDetailBackDetail.visibility = View.GONE
                binding.layoutRouteDetailOverlay.layoutRouteSelectContainer.visibility = View.GONE
                binding.layoutRouteDetailOverlay.bottomSheetRouteDetail.visibility = View.GONE
            }
            mainBinding?.mainBnv?.visibility = View.VISIBLE
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
            realtimePollingJob?.cancel()
            binding.layoutRouteDetailOverlay.layoutRealtimeRefresh.visibility = View.GONE

            binding.layoutRouteDetailOverlay.root.visibility = View.GONE

            binding.routeSearchFcv.visibility = View.VISIBLE
            binding.layoutRouteInputHeader.root.visibility = View.VISIBLE
            return
        }

        if (binding.layoutMapSelectOverlay.root.visibility == View.VISIBLE) {
            binding.layoutMapSelectOverlay.root.visibility = View.GONE
            selectedOnMapPlace = null
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
//                bottomSheetBehavior.peekHeight = (130 * density).toInt()
                bottomSheetBehavior.peekHeight = getScreenHeightPercentage(0.16f)
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

        currentMapCenter = latLng

        val geocoder = android.location.Geocoder(requireContext(), Locale.KOREAN)

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

                            selectedOnMapPlace = Pair(name, id)

                            if (place != null) {
                                val category = convertTypeToKorean(place.types?.map { it.toString().lowercase() } ?: emptyList())
                                binding.layoutMapSelectOverlay.tvMapSelectName.text = place.name
                                currentMapAddress = place.address
                                currentMapCategory = category
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

    private fun showRoutePlanDialog() {
        val initialCalendar = Calendar.getInstance().apply {
            if (!requestSearchTime.isNullOrEmpty()) {
                try {
                    // [수정] Locale을 저장할 때와 동일하게 KOREAN으로 맞추고 로그 추가
                    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.KOREAN).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }

                    val date = sdf.parse(requestSearchTime)
                    if (date != null) {
                        time = date
                        android.util.Log.d("RouteTimeDebug", "파싱 성공! 설정된 시간: $date")
                    }
                } catch (e: Exception) {
                    // [확인] 여기서 에러가 찍힌다면 포맷 글자 하나가 다른 겁니다.
                    android.util.Log.e("RouteTimeDebug", "파싱 실패: ${e.message} | 원본값: $requestSearchTime")
                }
            }
        }
        val bottomSheet = RoutePlanFilterBottomSheet(
            initialCalendar = initialCalendar,
            initialMode = if (isStart) 0 else 1
        ) { selectedCalendar, mode ->
            val isoSdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.KOREAN).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            requestSearchTime = isoSdf.format(selectedCalendar.time)

            val today = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val target = (selectedCalendar.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }

            val diffDays = ((target.timeInMillis - today.timeInMillis) / (24 * 60 * 60 * 1000)).toInt()
            val datePrefix = when (diffDays) {
                0 -> "오늘"
                1 -> "내일"
                else -> SimpleDateFormat("M월 d일", Locale.KOREAN).format(selectedCalendar.time)
            }
            val timeString = SimpleDateFormat("HH시 mm분", Locale.KOREAN).format(selectedCalendar.time)
            val modeString = if (mode == 0) "출발" else "도착"
            isStart = if (mode == 0) true else false

            binding.layoutRouteInputHeader.tvTimeFilter.text = "$datePrefix $timeString $modeString"
            FinalfetchRouteData()
        }
        bottomSheet.show(childFragmentManager, "RoutePlanFilter")
    }


    private fun showScheduleRouteDialog(){
        val view = layoutInflater.inflate(R.layout.dialog_schedule_route_filter, null)

        val bottomSheetDialog = BottomSheetDialog(requireContext())
        bottomSheetDialog.setContentView(view)

        val tvStartTime = view.findViewById<android.widget.TextView>(R.id.tv_schedule_time_info)

        if (scheduleTime.isNotEmpty()) {
            val timeParts = scheduleTime.split(":")
            val hour = timeParts.getOrNull(0) ?: "00"
            val minute = timeParts.getOrNull(1) ?: "00"

            val displayTime = if (minute == "00") {
                "${hour}시"
            } else {
                "${hour}시 ${minute}분"
            }

            tvStartTime?.text = "일정 시작 시간 : $displayTime"
        }

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
            isStart = false

            // 1. 문자열 조합 및 LocalDateTime 생성
            // scheduleData: "2026-02-11", scheduleTime: "13:30"
            val dateTimeString = "${scheduleDate}T${scheduleTime}:00"
            val scheduledDateTime = LocalDateTime.parse(dateTimeString)

            // 2. earlyArriveTime(분)만큼 차감하여 requestSearchTime 생성
            val adjustedDateTime = scheduledDateTime.minusMinutes(selectedMinute.toLong())

            // ISO 8601 형식으로 변환 (예: 2026-02-11T01:34:48.825Z)
            // .atZone(ZoneId.of("UTC"))를 사용하여 Z(Zulu) 표시를 포함합니다.
            requestSearchTime = adjustedDateTime.atZone(ZoneId.systemDefault()) // 기기 로컬 시간대(KST)
                .withZoneSameInstant(ZoneOffset.UTC) // 실제 UTC 시간으로 변환 (-9시간)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"))

            // 3. UI 텍스트 설정 (오늘/내일 판단)
            val today = LocalDate.now()
            val targetDate = scheduledDateTime.toLocalDate()

            val datePrefix = when (targetDate) {
                today -> "오늘"
                today.plusDays(1) -> "내일"
                else -> targetDate.format(DateTimeFormatter.ofPattern("MM월 dd일"))
            }

            binding.layoutRouteInputHeader.tvTimeFilter.text =
                "$datePrefix ${adjustedDateTime.format(DateTimeFormatter.ofPattern("HH:mm"))} 도착"

            FinalfetchRouteData()

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
            FinalfetchRouteData()
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
//            peekHeight = (130 * density).toInt()
            peekHeight = getScreenHeightPercentage(0.5f)

            expandedOffset = 0
            state = BottomSheetBehavior.STATE_HALF_EXPANDED
        }
    }

    private fun initDetailBottomSheet() {
        val detailSheet = binding.layoutRouteDetailOverlay.sheetRouteDetail.root
        val detailBehavior = BottomSheetBehavior.from(detailSheet)

        detailBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                // 디테일 뷰가 완전히 펼쳐졌을 때(STATE_EXPANDED)
                // 내 위치 버튼이 완전히 사라지게 하고 싶다면 여기서 제어할 수 있습니다.
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                val mapFragment = childFragmentManager.findFragmentById(R.id.route_map_fcv) as? MapFragment
                val parentHeight = (bottomSheet.parent as View).height
                val currentSheetHeight = parentHeight - bottomSheet.top

                mapFragment?.updateButtonTranslation(currentSheetHeight.toFloat())
            }
        })
    }

    private fun initBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheetContainer)
        bottomSheetBehavior.apply {
            isFitToContents = false
            halfExpandedRatio = 0.5f
//            peekHeight = (130 * resources.displayMetrics.density).toInt()
            peekHeight = getScreenHeightPercentage(0.16f)
            isHideable = true
            state = BottomSheetBehavior.STATE_HIDDEN
        }

        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                mainBinding?.mainBnv?.visibility = View.GONE
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
//                peekHeight = (130 * resources.displayMetrics.density).toInt()
                peekHeight = getScreenHeightPercentage(0.16f)
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

        val detailFragment = LocationDetailFragment.newInstance(item, isSchedule, isBookmarkSearchMode)

        childFragmentManager.beginTransaction()
            .replace(R.id.bottom_sheet_container, detailFragment, "DETAIL")
            .addToBackStack("DETAIL")
            .commit()
        bottomSheetBehavior.apply { isFitToContents = false; state = BottomSheetBehavior.STATE_HALF_EXPANDED }

        bottomSheetBehavior.apply {
            val density = resources.displayMetrics.density
//            peekHeight = (130 * density).toInt()
            peekHeight = getScreenHeightPercentage(0.5f)
            isFitToContents = false
            halfExpandedRatio = 0.5f
            expandedOffset = getScreenHeightPercentage(0.5f)
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

                if (isBookmarkSearchMode) {
                    onScheduleLocationSelected(place.name, place.placeId)
                    return
                }

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

            moveMapToCurrentLocation(location, animate = true)
        }
    }

    fun resetToCurrentLocationState() {
        if (_binding == null || !isAdded || !::bottomSheetBehavior.isInitialized) {
            pendingResetToCurrentLocationState = true
            return
        }
        pendingResetToCurrentLocationState = false

        hideKeyboard()
        mainBinding?.searchEt?.clearFocus()
        mainBinding?.searchEt?.setText("")

        searchJob?.cancel()
        realtimePollingJob?.cancel()
        currentRealtimeParams = null
        sessionToken = null

        currentEntryMode = EntryMode.MAIN
        currentTransitType = null
        isStart = true
        isDetailFromRecommend = false
        isSelectingStart = true
        isBookmarkSearchMode = false
        wasRouteHeaderVisibleBeforeBookmark = false
        bookmarkTarget = BookmarkTarget.NONE
        selectedGroupId = null
        selectedStartPlace = null
        selectedEndPlace = null
        selectedOnMapPlace = null
        startLatLng = null
        endLatLng = null
        currentMapCenter = null
        currentMapAddress = ""
        currentMapCategory = ""
        lastQuery = ""
        isPoiMode = false
        currentSortOption = RouteSortOption.BEST
        requestSearchTime = ""
        responseArrivelTime = ""
        earlyArriveTime = -1

        binding.layoutRouteInputHeader.tvRouteStart.text = ""
        binding.layoutRouteInputHeader.tvRouteEnd.text = ""
        binding.layoutRouteInputHeader.tvSortFilter.text = currentSortOption.uiText
        binding.layoutRouteInputHeader.root.visibility = View.GONE
        binding.layoutRouteInputHeader.layoutFilterOptions.visibility = View.GONE
        binding.layoutBookmarkHeader.root.visibility = View.GONE
        binding.layoutMapSelectOverlay.root.visibility = View.GONE
        binding.layoutRouteDetailOverlay.root.visibility = View.GONE
        binding.layoutRouteDetailOverlay.bottomSheetRouteDetail.visibility = View.GONE
        binding.routeSearchFcv.visibility = View.GONE
        binding.routeMapFcv.visibility = View.VISIBLE

        mainBinding?.mainToolbar?.visibility = View.VISIBLE
        mainBinding?.mainBackIv?.visibility = View.GONE
        mainBinding?.mainSearchLl?.visibility = View.VISIBLE
        mainBinding?.mainBnv?.visibility = View.VISIBLE

        val transaction = childFragmentManager.beginTransaction()
        if (historyFragment.isAdded) transaction.hide(historyFragment)
        if (recommendFragment.isAdded) transaction.hide(recommendFragment)
        childFragmentManager.findFragmentByTag("ROUTE_RESULT")?.let { transaction.hide(it) }
        transaction.commitAllowingStateLoss()
        childFragmentManager.popBackStackImmediate("DETAIL", FragmentManager.POP_BACK_STACK_INCLUSIVE)

        val mapFrag = childFragmentManager.findFragmentById(R.id.route_map_fcv) as? MapFragment
        mapFrag?.clearTemporaryMarker()
        mapFrag?.clearMarkers()
        mapFrag?.clearRoute()
        mapFrag?.setMapPadding(0)
        mapFrag?.updateButtonTranslation(0f)

        if (::bottomSheetBehavior.isInitialized) {
            bottomSheetBehavior.isHideable = true
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }

        mainActivity?.myLocation?.let {
            currentMyLocation = LatLng(it.latitude, it.longitude)
            moveMapToCurrentLocation(it, animate = false)
        }

        val defaultScheduleData = routeViewModel.routeOnlySchedule.value
        if (hasSchedule && defaultScheduleData != null) {
            showDefaultScheduleOverlay(defaultScheduleData)
        } else {
            binding.layoutRouteDetailOverlay.root.visibility = View.VISIBLE
            binding.layoutRouteDetailOverlay.root.bringToFront()
            binding.layoutRouteDetailOverlay.layoutRouteDetailInfo.visibility = View.VISIBLE
            binding.layoutRouteDetailOverlay.tvScheduleRouteDetailName.visibility = View.VISIBLE
            binding.layoutRouteDetailOverlay.tvScheduleRouteDetailName.text = "경로 일정 목록"
            binding.layoutRouteDetailOverlay.tvScheduleRouteDetailTime.visibility = View.GONE
            binding.layoutRouteDetailOverlay.viewColorDotRouteDetail.visibility = View.GONE
            binding.layoutRouteDetailOverlay.btnRouteDetailBackDetail.visibility = View.GONE
            binding.layoutRouteDetailOverlay.layoutRouteSelectContainer.visibility = View.GONE
            binding.layoutRouteDetailOverlay.bottomSheetRouteDetail.visibility = View.GONE
        }
    }

    private fun showMainEntryOverlay(data: RouteOnlyScheduleData?) {
        if (_binding == null || !isAdded || currentEntryMode != EntryMode.MAIN) return

        if (data != null) {
            hasSchedule = true
            showDefaultScheduleOverlay(data)
            return
        }

        hasSchedule = false
        binding.layoutRouteDetailOverlay.root.visibility = View.VISIBLE
        binding.layoutRouteDetailOverlay.root.bringToFront()
        binding.layoutRouteDetailOverlay.layoutRouteDetailInfo.visibility = View.VISIBLE
        binding.layoutRouteDetailOverlay.tvScheduleRouteDetailName.visibility = View.VISIBLE
        binding.layoutRouteDetailOverlay.tvScheduleRouteDetailName.text = "경로 일정 목록"
        binding.layoutRouteDetailOverlay.tvScheduleRouteDetailTime.visibility = View.GONE
        binding.layoutRouteDetailOverlay.viewColorDotRouteDetail.visibility = View.GONE
        binding.layoutRouteDetailOverlay.btnRouteDetailBackDetail.visibility = View.GONE
        binding.layoutRouteDetailOverlay.layoutRouteSelectContainer.visibility = View.GONE
        binding.layoutRouteDetailOverlay.bottomSheetRouteDetail.visibility = View.GONE
    }

    fun isInScheduleSelectionMode(): Boolean {
        return currentEntryMode == EntryMode.SCHEDULE ||
            currentEntryMode == EntryMode.SCHEDULE_ROUTE
    }

    fun consumeActionModeIntent(intent: android.content.Intent? = activity?.intent): Boolean {
        if (_binding == null || !isAdded || view == null || intent == null) {
            if (intent != null) {
                pendingActionModeExtras = Bundle(intent.extras ?: Bundle())
            }
            return false
        }

        val actionMode = intent.getStringExtra("ACTION_MODE")

        return when (actionMode) {
            "SCHEDULE" -> {
                startScheduleMode()
                intent.removeExtra("ACTION_MODE")
                activity?.intent?.removeExtra("ACTION_MODE")
                true
            }
            "SCHEDULE_ROUTE", "ROUTE_RESEARCH" -> {
                startScheduleRouteMode(intent)
                intent.removeExtra("ACTION_MODE")
                activity?.intent?.removeExtra("ACTION_MODE")
                true
            }
            else -> false
        }
    }

    private fun applyPendingActionModeIfNeeded() {
        val extras = pendingActionModeExtras ?: return
        val pendingIntent = android.content.Intent().apply {
            replaceExtras(Bundle(extras))
        }
        if (consumeActionModeIntent(pendingIntent)) {
            pendingActionModeExtras = null
        }
    }

    private fun applyPendingResetIfNeeded() {
        if (!pendingResetToCurrentLocationState || _binding == null || !::bottomSheetBehavior.isInitialized) return
        resetToCurrentLocationState()
    }

    private fun moveMapToCurrentLocation(location: android.location.Location, animate: Boolean) {
        val targetLocation = LatLng(location.latitude, location.longitude)
        val mapFrag = childFragmentManager.findFragmentById(R.id.route_map_fcv) as? MapFragment
        val supportMapFrag =
            mapFrag?.childFragmentManager?.findFragmentById(R.id.google_map_container) as? SupportMapFragment

        supportMapFrag?.getMapAsync { googleMap ->
            val cameraUpdate =
                com.google.android.gms.maps.CameraUpdateFactory.newLatLngZoom(targetLocation, 15f)
            if (animate) {
                googleMap.animateCamera(cameraUpdate)
            } else {
                googleMap.moveCamera(cameraUpdate)
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

    private fun startRealtimePolling(params: List<RealtimeParam>) {
        realtimePollingJob?.cancel()
        currentRealtimeParams = params

        binding.layoutRouteDetailOverlay.layoutRealtimeRefresh.visibility = View.VISIBLE

        realtimePollingJob = viewLifecycleOwner.lifecycleScope.launch {
            while (true) {
                params.forEach { param ->
                    when (param.type) {
                        "SUBWAY" -> {
                            // 뷰모델의 suspend 함수를 호출하고 결과를 바로 받음
                            val result = transitViewModel.fetchRealTimeSubwayArrivals(param.lineName, param.startStation, param.endStation)
                            // Helper에게 "해당 레이아웃(타겟)에 이 결과값으로 글씨 갱신해!" 라고 지시
                            RouteDetailHelper.updateSubwayUI(requireContext(), param.targetLayout, result)
                        }
                        "BUS" -> {
                            val result = transitViewModel.fetchRealTimeBusArrivals(param.lineName, param.startStation, param.endStation)
                            RouteDetailHelper.updateBusUI(requireContext(), param.targetLayout, result)
                        }
                    }
                }
                Log.d("RouteFragment", "실시간 데이터 15초 갱신 완료!")

                for (i in 15 downTo 1) {
                    binding.layoutRouteDetailOverlay.tvRefreshCountdown.text = i.toString()
                    delay(1000) // 1초 대기
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
    private fun initPlacesClient() {
        if (!Places.isInitialized()) {
            Places.initialize(requireContext(), BuildConfig.GOOGLE_API_KEY)
        }

        //이미 초기화 되어있으면 재생성하지 않음
        if(!::placesClient.isInitialized){
            placesClient = Places.createClient(requireContext())
        }
        if(sessionToken == null) {
            sessionToken = AutocompleteSessionToken.newInstance()
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = requireContext().getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false

        return activeNetwork.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                activeNetwork.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) ||
                activeNetwork.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private fun getScreenHeightPercentage(ratio: Float): Int {
        val screenHeight = resources.displayMetrics.heightPixels
        return (screenHeight * ratio).toInt()
    }

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
        //placesClient 정리추가
        if(::placesClient.isInitialized){
            try{
                (placesClient as? AutoCloseable)?.close()
            }catch (e: Exception){
                Log.e("PlacesClient", "close error: ${e.message}")
            }
        }
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

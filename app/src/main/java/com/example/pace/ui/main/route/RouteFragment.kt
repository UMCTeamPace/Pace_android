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
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.pace.BuildConfig
import com.example.pace.R
import com.example.pace.databinding.FragmentRouteBinding
import com.example.pace.ui.main.MainActivity
import com.example.pace.ui.search_box.*
import com.google.android.gms.location.LocationServices
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

class RouteFragment : Fragment() {
    private var _binding: FragmentRouteBinding? = null
    private val binding get() = _binding!!

    // MainActivity의 뷰와 데이터를 안전하게 가져오기 위한 속성
//    private val mainActivity get() = requireActivity() as MainActivity
//    private val mainBinding get() = mainActivity.binding

    private val mainActivity: MainActivity? get() = activity as? MainActivity
    private val mainBinding get() = (activity as? MainActivity)?.binding

    private lateinit var placesClient: PlacesClient
    private val fusedLocationClient by lazy { LocationServices.getFusedLocationProviderClient(requireActivity()) }

    private val historyFragment = SearchHistoryFragment()
    private val recommendFragment = SearchRecommendFragment()
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>

    private var isDetailFromRecommend = false
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
    }

    private fun setupMainActivityListeners() {
        mainBinding?.searchEt?.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) enterSearchMode(false)
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

        mainBinding?.mainBackIv?.setOnClickListener { handleCustomBackClick() }

        mainBinding?.btnSearch?.setOnClickListener {
            if (mainBinding?.searchEt?.text?.isNotEmpty() == true) mainBinding?.searchEt?.setText("")
        }
    }

    private fun enterSearchMode(isRouteInput: Boolean = false) {
        mainBinding?.mainBackIv?.visibility = View.VISIBLE
        mainBinding?.mainBnv?.visibility = View.GONE

        if (childFragmentManager.backStackEntryCount > 0) {
            childFragmentManager.popBackStackImmediate(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }

        if (::bottomSheetBehavior.isInitialized) {
            bottomSheetBehavior.isHideable = true
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        }

        binding.routeSearchFcv.visibility = View.VISIBLE
        binding.routeSearchFcv.bringToFront()

        val query = mainBinding?.searchEt?.text.toString().trim()
        val targetFragment = if (query.isNotEmpty()) recommendFragment else historyFragment
        showSearchFragment(targetFragment)

        if (targetFragment is SearchHistoryFragment) {
            targetFragment.setRouteOptionsVisible(isRouteInput)
        }

        mainBinding?.searchEt?.requestFocus()
        showKeyBoard()
    }

    private fun exitSearchMode() {
        hideKeyboard()
        mainBinding?.searchEt?.clearFocus()
        mainBinding?.searchEt?.setText("")

        searchJob?.cancel()

        val transaction = childFragmentManager.beginTransaction()
        if (historyFragment.isAdded) transaction.hide(historyFragment)
        if (recommendFragment.isAdded) transaction.hide(recommendFragment)
        transaction.commitAllowingStateLoss()

        binding.routeSearchFcv.visibility = View.GONE
        mainBinding?.mainBackIv?.visibility = View.GONE
        mainBinding?.mainBnv?.visibility = View.VISIBLE
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

    fun onRecommendItemClick(item: SearchItem) {
        // 1. 키보드 내리고 포커스 해제
        hideKeyboard()
        mainBinding?.searchEt?.clearFocus()

        // 2. 검색 모드 UI 종료 (History/Recommend 프래그먼트 숨기기)
        exitSearchMode()

        // 3. 추천 검색어로부터 온 상세 화면임을 표시 (뒤로가기 로직용)
        isDetailFromRecommend = true

        // 4. 상세 화면 프래그먼트 띄우기
        showLocationDetail(item)

        // 5. 상단 뒤로가기(←) 버튼 보이게 설정
        mainBinding?.mainBackIv?.visibility = View.VISIBLE
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

    private fun setupOnBackPressed() {
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val currentSheetFragment = childFragmentManager.findFragmentById(R.id.bottom_sheet_container)

                if (currentSheetFragment is LocationDetailFragment) {
                    childFragmentManager.popBackStack()
                    if (isDetailFromRecommend) {
                        bottomSheetBehavior.isHideable = true
                        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                        enterSearchMode(false)
                    } else {
                        bottomSheetBehavior.isFitToContents = false
                        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
                    }
                    return
                }

                if (isBottomSheetVisible()) {
                    if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
                        bottomSheetBehavior.isHideable = true
                        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                        mainBinding?.searchEt?.setText("")
                        enterSearchMode(false)
                    } else {
                        bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                    }
                    return
                }

                if (isSearchMode()) {
                    exitSearchMode()
                    return
                }

                isEnabled = false
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        })
    }

    private fun handleCustomBackClick() {
        if (isBottomSheetVisible()) {
            bottomSheetBehavior.isHideable = true
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
            mainBinding?.searchEt?.setText("")
            enterSearchMode(false)
            return
        }

        if (isSearchMode()) {
            if (mainBinding?.searchEt?.hasFocus() == true) {
                hideKeyboard()
                mainBinding?.searchEt?.clearFocus()
            } else exitSearchMode()
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
        childFragmentManager.beginTransaction()
            .replace(R.id.bottom_sheet_container, LocationDetailFragment.newInstance(item))
            .addToBackStack("DETAIL").commit()
        bottomSheetBehavior.apply { isFitToContents = false; state = BottomSheetBehavior.STATE_HALF_EXPANDED }
    }

    // 유틸리티 함수들
    private fun isSearchMode() = (historyFragment.isAdded && !historyFragment.isHidden) || (recommendFragment.isAdded && !recommendFragment.isHidden)
    private fun isBottomSheetVisible() = ::bottomSheetBehavior.isInitialized && bottomSheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN
    private fun showKeyBoard() = (requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(mainBinding?.searchEt, InputMethodManager.SHOW_IMPLICIT)
    private fun hideKeyboard() = (requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(mainBinding?.searchEt?.windowToken, 0)
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
package com.example.pace.ui.search_box

import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import com.example.pace.databinding.FragmentLocationDetailBinding
import com.example.pace.R
import com.example.pace.data.viewmodel.PlaceSavedGroupState
import com.example.pace.data.viewmodel.GroupViewModel
import com.example.pace.ui.main.route.RouteFragment
import com.example.pace.ui.search_box.group.SavePlaceGroupSelectBottomSheet
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPhotoRequest
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.PlacesClient
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LocationDetailFragment : Fragment() {

    private var _binding: FragmentLocationDetailBinding? = null
    private val binding get() = _binding!!
    private val groupViewModel: GroupViewModel by activityViewModels()
    private lateinit var placesClient: PlacesClient
    private var hasPhotoSection = false
    private var currentPlaceId: String = ""
    private var hasRequestedSavedState = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLocationDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        placesClient = Places.createClient(requireContext())

        observeViewModel()

        val name = arguments?.getString("name") ?: ""
        val category = arguments?.getString("category") ?: ""
        val address = arguments?.getString("address") ?: ""
        val defaultDistance = arguments?.getString("distance") ?: ""
        val placeId = arguments?.getString("placeId") ?: ""
        currentPlaceId = placeId
        val openStatus = arguments?.getString("openStatus") ?: ""
        val isScheduleMode = arguments?.getBoolean("isScheduleMode") ?: false
        val isBookmarkMode = arguments?.getBoolean("isBookmarkMode") ?: false
        val argLat = arguments?.getDouble("lat", 0.0) ?: 0.0
        val argLng = arguments?.getDouble("lng", 0.0) ?: 0.0

        binding.tvTitle.text = name
        binding.tvOpenStatus.text = openStatus

        val context = requireContext()
        val colorResId = when {
            openStatus.contains("영업 중") -> R.color.semantic_info
            openStatus.contains("영업 종료") || openStatus.contains("운영 중단") -> R.color.semantic_warning
            else -> R.color.black
        }
        binding.tvOpenStatus.setTextColor(androidx.core.content.ContextCompat.getColor(context, colorResId))

        val parent = parentFragment as? RouteFragment
        var displayDistance = defaultDistance

        if (argLat != 0.0 && argLng != 0.0) {
            // 부모의 지도 업데이트 (마커 이동 등)
            parent?.updateMapFromDetail(name, placeId, argLat, argLng)

            // 내 위치와 타겟 좌표 사이의 거리 실시간 계산
            val targetLatLng = LatLng(argLat, argLng)
            val calculatedDist = parent?.calculateDistance(targetLatLng)

            if (!calculatedDist.isNullOrEmpty()) {
                displayDistance = calculatedDist
            }
        }
        updateMetaInfoText(category, displayDistance, address)

        if (placeId.isNotEmpty()) {
            requestSavedStateIfPossible(placeId)
            fetchPlacePhotos(placeId)
        } else {
            updateStarState(emptyList())
            hasPhotoSection = false
            binding.svPhotos.visibility = View.GONE
        }

        when {
            isBookmarkMode -> {
                binding.icStart.visibility = View.GONE
                binding.icArrive.visibility = View.GONE
                binding.icSelectLocation.visibility = View.VISIBLE
            }
            isScheduleMode -> {
                binding.icStart.visibility = View.GONE
                binding.icArrive.visibility = View.GONE
                binding.icSelectLocation.visibility = View.VISIBLE
            }
            else -> {
                binding.icStart.visibility = View.VISIBLE
                binding.icArrive.visibility = View.VISIBLE
                binding.icSelectLocation.visibility = View.GONE
            }
        }

        binding.icStart.setOnClickListener {
            val parent = parentFragment as? RouteFragment
            parent?.onLocationSelected(name, placeId, isStart = true)
        }

        binding.icArrive.setOnClickListener {
            val parent = parentFragment as? RouteFragment
            parent?.onLocationSelected(name, placeId, isStart = false)
        }

        binding.icSelectLocation.setOnClickListener {
            val parent = parentFragment as? RouteFragment
            parent?.onScheduleLocationSelected(name, placeId)
        }

        binding.layoutStar.setOnClickListener {
            val currentPlaceId = arguments?.getString("placeId") ?: ""
            val originalName = binding.tvTitle.text.toString()

            val bottomSheet = SavePlaceGroupSelectBottomSheet(
                placeName = originalName,
                placeId = currentPlaceId
            )

            bottomSheet.show(parentFragmentManager, "SavePlaceGroupSelectBottomSheet")
        }
    }

    private fun observeViewModel() {
        groupViewModel.errorMessage.observe(viewLifecycleOwner, Observer { msg ->
            if (msg.isNullOrBlank()) return@Observer

            val isDuplicateError = (groupViewModel.errorCode.value == "PLACE400_1")

            if (!isDuplicateError) { }
        })

        // 3. 여기도 Observer { } 로 감싸기
        groupViewModel.isOperationSuccess.observe(viewLifecycleOwner, Observer { isSuccess ->
            if (isSuccess) { }
        })

        groupViewModel.groupList.observe(viewLifecycleOwner) { groups ->
            if (currentPlaceId.isNotEmpty() && !hasRequestedSavedState && groups.isNotEmpty()) {
                hasRequestedSavedState = true
                groupViewModel.fetchSavedGroupsForPlace(currentPlaceId, groups)
            }
        }

        groupViewModel.placeSavedStatesByPlaceId.observe(viewLifecycleOwner) { statesByPlaceId ->
            if (currentPlaceId.isEmpty()) return@observe
            if (!statesByPlaceId.containsKey(currentPlaceId) && hasRequestedSavedState) {
                val groups = groupViewModel.groupList.value
                if (!groups.isNullOrEmpty()) {
                    groupViewModel.fetchSavedGroupsForPlace(currentPlaceId, groups, forceRefresh = true)
                }
                return@observe
            }
            updateStarState(statesByPlaceId[currentPlaceId].orEmpty())
        }
    }

    private fun requestSavedStateIfPossible(placeId: String) {
        updateStarState(emptyList())
        val groups = groupViewModel.groupList.value
        if (!groups.isNullOrEmpty()) {
            hasRequestedSavedState = true
            groupViewModel.fetchSavedGroupsForPlace(placeId, groups)
        } else {
            hasRequestedSavedState = false
            groupViewModel.fetchGroupList()
        }
    }

    private fun updateStarState(savedStates: List<PlaceSavedGroupState>) {
        val latestState = savedStates.maxByOrNull { it.createdAt }
        if (latestState == null) {
            binding.ivStarLine.imageTintList = null
            binding.ivStarLine.setImageResource(R.drawable.ic_star_line_outline)
            return
        }

        try {
            val color = Color.parseColor(latestState.groupColor)
            binding.ivStarLine.setImageResource(R.drawable.ic_star_line_filled)
            binding.ivStarLine.imageTintList = ColorStateList.valueOf(color)
        } catch (e: Exception) {
            binding.ivStarLine.imageTintList = null
            binding.ivStarLine.setImageResource(R.drawable.ic_star_line_outline)
        }
    }

    private fun updateMetaInfoText(category: String, distance: String, address: String) {
        val result = if (distance.isNotEmpty()) {
            "$category · $distance · $address"
        } else {
            "$category · $address"
        }
        binding.tvMetaInfo.text = result
    }

    private fun fetchPlacePhotos(placeId: String) {
        val fields = listOf(Place.Field.PHOTO_METADATAS, Place.Field.LAT_LNG)
        val request = FetchPlaceRequest.newInstance(placeId, fields)

        placesClient.fetchPlace(request).addOnSuccessListener { response ->
            if (_binding == null) return@addOnSuccessListener

            val place = response.place
            if (place.latLng != null) {
                val lat = place.latLng!!.latitude
                val lng = place.latLng!!.longitude
                val name = binding.tvTitle.text.toString()

                (parentFragment as? RouteFragment)?.updateMapFromDetail(name, placeId, lat, lng)
            }

            val metadataList = response.place.photoMetadatas

            if (metadataList.isNullOrEmpty()) {
                hasPhotoSection = false
                binding.svPhotos.visibility = View.GONE
            }else {
                hasPhotoSection = true
                binding.svPhotos.visibility = View.VISIBLE
                binding.photoContainer.removeAllViews()

                val count = minOf(metadataList.size, 3)

                for (i in 0 until count) {
                    val metadata = metadataList[i]
                    val photoRequest = FetchPhotoRequest.builder(metadata)
                        .setMaxWidth(1000)
                        .setMaxHeight(600)
                        .build()

                    placesClient.fetchPhoto(photoRequest).addOnSuccessListener { photoResponse ->
                        if (_binding == null) return@addOnSuccessListener

                        addDynamicPhotoView(photoResponse.bitmap)
                    }.addOnFailureListener { error ->
                        Log.e("PlacePhoto", "Location detail photo fetch failed: placeId=$placeId, index=$i", error)
                    }
                }
            }
        }.addOnFailureListener { error ->
            if (_binding == null) return@addOnFailureListener

            Log.e("PlacePhoto", "Location detail metadata fetch failed: placeId=$placeId", error)
            hasPhotoSection = false
            binding.svPhotos.visibility = View.GONE
        }
    }

    fun updateCollapsedState(isCollapsed: Boolean) {
        if (_binding == null) return
        if (!hasPhotoSection) {
            binding.svPhotos.visibility = View.GONE
            return
        }
        binding.svPhotos.visibility = if (isCollapsed) View.GONE else View.VISIBLE
    }


    private fun addDynamicPhotoView(bitmap: Bitmap) {
        if (_binding == null) return

        val context = requireContext()

        // 1. CardView 생성
        val cardView = androidx.cardview.widget.CardView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(130),
                LinearLayout.LayoutParams.MATCH_PARENT
            ).apply {
                marginEnd = dpToPx(8) // 사진 간격
            }
            radius = dpToPx(8).toFloat()
            cardElevation = 0f
        }

        // 2. ImageView 생성
        val imageView = ImageView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageBitmap(bitmap)
        }

        cardView.addView(imageView)
        binding.photoContainer.addView(cardView)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    fun setDragHandleTouchListener(listener: View.OnTouchListener?) {
        _binding?.viewDragHandle?.setOnTouchListener(listener)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(item: SearchItem, isScheduleMode: Boolean, isBookmarkMode: Boolean): LocationDetailFragment {
            val fragment = LocationDetailFragment()
            val bundle = Bundle().apply {
                putString("name", item.name)
                putString("category", item.category)
                putString("address", item.address)
                putString("distance", item.distance)
                putString("placeId", item.placeId)
                putString("openStatus", item.openStatus)
                putBoolean("isScheduleMode", isScheduleMode)
                putBoolean("isBookmarkMode", isBookmarkMode)
                putDouble("lat", item.lat)
                putDouble("lng", item.lng)
            }
            fragment.arguments = bundle
            return fragment
        }
    }
}

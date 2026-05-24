package com.example.pace.ui.search_box

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.pace.R
import com.example.pace.data.viewmodel.GroupViewModel
import com.example.pace.data.viewmodel.PlaceSavedGroupState
import com.example.pace.databinding.FragmentLocationDetailCompactBinding
import com.example.pace.ui.main.route.RouteFragment
import com.example.pace.ui.search_box.group.SavePlaceGroupSelectBottomSheet
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LocationDetailCompactFragment : Fragment() {

    private var _binding: FragmentLocationDetailCompactBinding? = null
    private val binding get() = _binding!!
    private val groupViewModel: GroupViewModel by activityViewModels()

    private var currentPlaceId: String = ""
    private var hasRequestedSavedState = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLocationDetailCompactBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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
        binding.tvOpenStatus.setTextColor(
            androidx.core.content.ContextCompat.getColor(requireContext(), R.color.semantic_info)
        )

        val parent = parentFragment as? RouteFragment
        var displayDistance = defaultDistance
        if (argLat != 0.0 && argLng != 0.0) {
            parent?.updateMapFromDetail(name, placeId, argLat, argLng)
            val calculatedDist = parent?.calculateDistance(LatLng(argLat, argLng))
            if (!calculatedDist.isNullOrEmpty()) {
                displayDistance = calculatedDist
            }
        }
        updateMetaInfoText(category, displayDistance, address)

        if (placeId.isNotEmpty()) {
            requestSavedStateIfPossible(placeId)
        } else {
            updateStarState(emptyList())
        }

        when {
            isBookmarkMode || isScheduleMode -> {
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
            (parentFragment as? RouteFragment)?.onLocationSelected(name, placeId, isStart = true)
        }

        binding.icArrive.setOnClickListener {
            (parentFragment as? RouteFragment)?.onLocationSelected(name, placeId, isStart = false)
        }

        binding.icSelectLocation.setOnClickListener {
            (parentFragment as? RouteFragment)?.onScheduleLocationSelected(name, placeId)
        }

        binding.layoutStar.setOnClickListener {
            SavePlaceGroupSelectBottomSheet(
                placeName = binding.tvTitle.text.toString(),
                placeId = currentPlaceId
            ).show(parentFragmentManager, "SavePlaceGroupSelectBottomSheet")
        }
    }

    private fun observeViewModel() {
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
        binding.tvMetaInfo.text = if (distance.isNotEmpty()) {
            "$category · $distance · $address"
        } else {
            "$category · $address"
        }
    }

    fun updateCollapsedState(@Suppress("UNUSED_PARAMETER") isCollapsed: Boolean) = Unit

    fun setDragHandleTouchListener(listener: View.OnTouchListener?) {
        _binding?.viewDragHandle?.setOnTouchListener(listener)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(
            item: SearchItem,
            isScheduleMode: Boolean,
            isBookmarkMode: Boolean
        ): LocationDetailCompactFragment {
            val fragment = LocationDetailCompactFragment()
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

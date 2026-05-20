package com.example.pace.ui.search_box

import android.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.RadioGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pace.R
import com.example.pace.data.viewmodel.GroupViewModel
import com.example.pace.databinding.FragmentLocationBottomSheetBinding
import com.example.pace.ui.search_box.group.SavePlaceGroupSelectBottomSheet
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.SearchByTextRequest
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LocationBottomSheetFragment : Fragment() {

    private var _binding: FragmentLocationBottomSheetBinding? = null
    private val binding get() = _binding!!

    private val groupViewModel: GroupViewModel by activityViewModels()

    private lateinit var adapter: LocationListAdapter
    private lateinit var placesClient: PlacesClient

    private var currentItems: List<SearchItem> = emptyList()
    private var currentSortPreference: SearchByTextRequest.RankPreference = SearchByTextRequest.RankPreference.RELEVANCE

    var onItemClick: ((SearchItem) -> Unit)? = null
    var onSortTypeSelected: ((SearchByTextRequest.RankPreference) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLocationBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (view.parent as? View)?.backgroundTintList = null

        if(!::placesClient.isInitialized) {
            placesClient=Places.createClient(requireContext())
        }

        binding.tvFilterLocation.setOnClickListener {
            showFilterDialog()
        }

        setupRecyclerView()
        setupFilterListeners()
        observeViewModel()

        if (currentItems.isNotEmpty()) {
            adapter.submitList(currentItems)
        }
    }

    private fun observeViewModel() {
        groupViewModel.errorMessage.observe(viewLifecycleOwner) { msg ->
            if (!msg.isNullOrBlank()) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            }
        }

        groupViewModel.isOperationSuccess.observe(viewLifecycleOwner) { isSuccess ->
            if (isSuccess) {
            }
        }

        groupViewModel.groupList.observe(viewLifecycleOwner) { groups ->
            if (groups.isNotEmpty()) {
                requestSavedStatesForCurrentItems()
            }
        }

        groupViewModel.placeSavedStatesByPlaceId.observe(viewLifecycleOwner) { statesByPlaceId ->
            if (!::adapter.isInitialized) return@observe
            val colorsByPlaceId = currentItems.mapNotNull { item ->
                val latestState = statesByPlaceId[item.placeId]
                    ?.maxByOrNull { it.createdAt }
                latestState?.let { item.placeId to it.groupColor }
            }.toMap()
            adapter.updateSavedStarColors(colorsByPlaceId)
        }
    }

    private fun setupRecyclerView() {
        adapter = LocationListAdapter(placesClient) { selectedItem ->
            onItemClick?.invoke(selectedItem)
        }

        adapter.onFavoriteClick = { selectedItem ->
            Log.d("DEBUG", "선택된 장소 ID: ${selectedItem.placeId}")
            val groupSelectSheet = SavePlaceGroupSelectBottomSheet(
                placeName = selectedItem.name,
                placeId = selectedItem.placeId
            )

            groupSelectSheet.show(parentFragmentManager, "SavePlaceGroupSelectBottomSheet")
        }

        binding.rvSearchResults.apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = this@LocationBottomSheetFragment.adapter
        }
    }

    private fun setupFilterListeners() {
        binding.tvFilterLocation.setOnClickListener {
            showFilterDialog()
        }
    }

    private fun showFilterDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_search_filter, null)

        val builder = AlertDialog.Builder(requireContext())
        builder.setView(dialogView)

        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val rgSortOptions = dialogView.findViewById<RadioGroup>(R.id.rg_sort_options_search_filter)
        val btnCancel = dialogView.findViewById<Button>(R.id.btn_cancel_search_filter)
        val btnConfirm = dialogView.findViewById<Button>(R.id.btn_save_search_filter)

        if (currentSortPreference == SearchByTextRequest.RankPreference.DISTANCE) {
            rgSortOptions.check(R.id.rb_distance)
        } else {
            rgSortOptions.check(R.id.rb_relevance)
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnConfirm.setOnClickListener {
            val selectedId = rgSortOptions.checkedRadioButtonId

            when (selectedId) {
                R.id.rb_relevance -> {
                    currentSortPreference = SearchByTextRequest.RankPreference.RELEVANCE
                    binding.tvFilterLocation.text = "관련도 순"
                    onSortTypeSelected?.invoke(SearchByTextRequest.RankPreference.RELEVANCE)
                }
                R.id.rb_distance -> {
                    currentSortPreference = SearchByTextRequest.RankPreference.DISTANCE
                    binding.tvFilterLocation.text = "거리 순"
                    onSortTypeSelected?.invoke(SearchByTextRequest.RankPreference.DISTANCE)
                }
            }
            dialog.dismiss()
        }

        dialog.show()

        val displayMetrics = resources.displayMetrics
        val width = (displayMetrics.widthPixels * 0.90).toInt()
        dialog.window?.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
    }

    fun updateData(items: List<SearchItem>) {
        this.currentItems = items
        if (_binding == null || !::adapter.isInitialized) {
            return
        }
        adapter.submitList(items)
        requestSavedStatesForCurrentItems()

        binding.rvSearchResults.scrollToPosition(0)
    }

    private fun requestSavedStatesForCurrentItems() {
        if (currentItems.isEmpty()) return

        val groups = groupViewModel.groupList.value
        if (groups.isNullOrEmpty()) {
            groupViewModel.fetchGroupList()
            return
        }

        currentItems
            .map { it.placeId }
            .filter { it.isNotBlank() }
            .distinct()
            .let { placeIds ->
                groupViewModel.fetchSavedGroupsForPlaces(placeIds, groups)
            }
    }

    fun resetFilter() {
        if (_binding != null) {
            currentSortPreference = SearchByTextRequest.RankPreference.RELEVANCE
            binding.tvFilterLocation.text = "관련도 순"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "LocationBottomSheetFragment"
    }
}

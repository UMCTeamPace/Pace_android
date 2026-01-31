package com.example.pace.ui.search_box

import android.os.Bundle
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pace.R
import com.example.pace.databinding.FragmentLocationBottomSheetBinding
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.SearchByTextRequest
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

class LocationBottomSheetFragment : Fragment() {

    private var _binding: FragmentLocationBottomSheetBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: LocationListAdapter
    private lateinit var placesClient: PlacesClient

    private var currentItems: List<SearchItem> = emptyList()

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

        placesClient = Places.createClient(requireContext())

        adapter = LocationListAdapter(placesClient) { selectedItem ->
            onItemClick?.invoke(selectedItem)
        }
        binding.rvSearchResults.adapter = adapter

        // 3. UI 설정
        setupRecyclerView()
        // 검색
        setupFilterListeners()

        if (currentItems.isNotEmpty()) {
            adapter.submitList(currentItems)
        }
    }

    private fun setupRecyclerView() {
        adapter = LocationListAdapter(placesClient) { selectedItem ->
            onItemClick?.invoke(selectedItem)
        }

        binding.rvSearchResults.apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = this@LocationBottomSheetFragment.adapter
        }
    }

    private fun setupFilterListeners() {

        binding.tvFilterLocation.setOnClickListener {
            val popup = PopupMenu(requireContext(), view)

            popup.menu.add(0, 0, 0, "관련도 순")
            popup.menu.add(0, 1, 1, "거리 순")

            popup.setOnMenuItemClickListener { item ->
                when (item.title) {
                    "관련도 순" -> {
                        binding.tvFilterLocation.text = "관련도 순"
                        onSortTypeSelected?.invoke(SearchByTextRequest.RankPreference.RELEVANCE)
                    }
                    "거리 순" -> {
                        binding.tvFilterLocation.text = "거리 순"
                        onSortTypeSelected?.invoke(SearchByTextRequest.RankPreference.DISTANCE)
                    }
                }
                true
            }
            popup.show()
        }
    }

    fun updateData(items: List<SearchItem>) {
        this.currentItems = items
        if (_binding == null || !::adapter.isInitialized) {
            return
        }
        adapter.submitList(items)

        binding.rvSearchResults.scrollToPosition(0)
    }

    fun resetFilter() {
        if (_binding != null) {
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
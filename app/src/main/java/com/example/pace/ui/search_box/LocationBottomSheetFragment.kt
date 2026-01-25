package com.example.pace.ui.search_box

import android.os.Bundle
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pace.R
import com.example.pace.databinding.FragmentLocationBottomSheetBinding
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

class LocationBottomSheetFragment : Fragment() {

    private var _binding: FragmentLocationBottomSheetBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: LocationListAdapter
    private lateinit var placesClient: PlacesClient

    private var currentItems: List<SearchItem> = emptyList()

    var onItemClick: ((SearchItem) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLocationBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. 둥근 모서리 배경
        (view.parent as? View)?.backgroundTintList = null

        // 2. Places 클라이언트 초기화
        placesClient = Places.createClient(requireContext())

        adapter = LocationListAdapter(placesClient) { selectedItem ->
            // 어댑터에서 클릭 발생 -> 여기서 받아서 -> 메인 액티비티로 토스!
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

        // 1. 위치 필터 클릭 시
        binding.tvFilterLocation.setOnClickListener {
            // 나중에 여기에 팝업 메뉴 코드 넣으시면 됩니다.
            Toast.makeText(context, "위치 필터 기능 준비 중", Toast.LENGTH_SHORT).show()
        }

        // 2. 정렬 필터 클릭 시
        binding.tvFilterSort.setOnClickListener {
            // 나중에 여기에 팝업 메뉴 코드 넣으시면 됩니다.
            Toast.makeText(context, "정렬 필터 기능 준비 중", Toast.LENGTH_SHORT).show()
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "LocationBottomSheetFragment"
    }
}
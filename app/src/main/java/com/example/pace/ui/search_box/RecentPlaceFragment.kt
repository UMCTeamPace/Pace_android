package com.example.pace.ui.search_box

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pace.data.db.SearchDatabase
import com.example.pace.data.model.RecentHistoryItem
import com.example.pace.data.repository.SearchRepository
import com.example.pace.databinding.FragmentRecentPlaceBinding
import kotlinx.coroutines.launch

class RecentPlaceFragment : Fragment() {
    private var _binding: FragmentRecentPlaceBinding? = null
    private val binding get() = _binding!!
    private lateinit var repository: SearchRepository
    private lateinit var historyAdapter: RecentHistoryAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRecentPlaceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val dao = SearchDatabase.getDatabase(requireContext()).searchDao()
        repository = SearchRepository(dao)

        setupRecyclerView()
        observeData()
    }

    private fun setupRecyclerView() {
        historyAdapter = RecentHistoryAdapter(
            onItemClick = { item ->
                // 장소 클릭 시 지도로 이동 등의 로직 처리
            },
            onDeleteClick = { /* 삭제 로직 미구현 */ }
        )
        binding.rvRecentPlace.apply {
            adapter = historyAdapter
            layoutManager = LinearLayoutManager(context)
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.recentPlaces.collect { places ->
                    val items = places.map {
                        RecentHistoryItem(
                            type = RecentHistoryItem.TYPE_PLACE,
                            mainText = it.name,
                            timestamp = it.timestamp,
                            placeEntity = it
                        )
                    }

                    historyAdapter.submitList(items.toList())
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
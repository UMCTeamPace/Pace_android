package com.example.pace.ui.search_box

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pace.PaceApplication
import com.example.pace.data.db.SearchDatabase
import com.example.pace.data.model.RecentHistoryItem
import com.example.pace.data.repository.SearchRepository
import com.example.pace.data.viewmodel.SearchViewModel
import com.example.pace.data.viewmodel.SearchViewModelFactory
import com.example.pace.databinding.FragmentRecentPlaceBinding
import com.example.pace.ui.main.route.RouteFragment
import kotlinx.coroutines.launch

class RecentPlaceFragment : Fragment() {
    private var _binding: FragmentRecentPlaceBinding? = null
    private val binding get() = _binding!!
    private lateinit var historyAdapter: RecentHistoryAdapter

    private val searchViewModel: SearchViewModel by viewModels {
        SearchViewModelFactory((requireActivity().application as PaceApplication).searchRepository)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRecentPlaceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        observeData()
    }

    private fun setupRecyclerView() {
        val touchHelper = CommonSwipeTouchHelper()
        val itemTouchHelper = ItemTouchHelper(touchHelper)

        historyAdapter = RecentHistoryAdapter(
            onItemClick = { item ->
                (parentFragment?.parentFragment as? RouteFragment)?.handleHistoryItemClick(item)
            },
            onDeleteClick = { item ->
                searchViewModel.deleteHistoryItem(item) }
        )

        historyAdapter.setHelper(touchHelper)

        binding.rvRecentPlace.apply {
            adapter = historyAdapter
            layoutManager = LinearLayoutManager(context)

            itemTouchHelper.attachToRecyclerView(this)

            addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: androidx.recyclerview.widget.RecyclerView, newState: Int) {
                    if (newState == androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_DRAGGING) {
                        touchHelper.closeSwipedMenu()
                    }
                }
            })
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                searchViewModel.allHistory.collect { historyItems ->
                    val placesOnly = historyItems.filter { it.type == RecentHistoryItem.TYPE_PLACE }
                    historyAdapter.submitList(placesOnly)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
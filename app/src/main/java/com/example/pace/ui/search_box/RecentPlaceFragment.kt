package com.example.pace.ui.search_box

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.pace.PaceApplication
import com.example.pace.data.model.RecentHistoryItem
import com.example.pace.data.viewmodel.GroupViewModel
import com.example.pace.data.viewmodel.SearchViewModel
import com.example.pace.data.viewmodel.SearchViewModelFactory
import com.example.pace.databinding.FragmentRecentPlaceBinding
import com.example.pace.ui.main.route.RouteFragment
import kotlinx.coroutines.launch

class RecentPlaceFragment : Fragment() {
    private var _binding: FragmentRecentPlaceBinding? = null
    private val binding get() = _binding!!
    private lateinit var historyAdapter: RecentHistoryAdapter
    private var hasObservedHistory = false
    private var lastFirstHistoryKey: String? = null
    private var lastHistorySize = 0
    private var isUserScrolling = false
    private var currentPlaceItems: List<RecentHistoryItem> = emptyList()
    private var requestedPlaceIds: Set<String> = emptySet()

    private val searchViewModel: SearchViewModel by viewModels {
        SearchViewModelFactory((requireActivity().application as PaceApplication).searchRepository)
    }
    private val groupViewModel: GroupViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRecentPlaceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.root.isFocusable = false
        binding.root.isFocusableInTouchMode = false
        binding.rvRecentPlace.isFocusable = false
        binding.rvRecentPlace.isFocusableInTouchMode = false
        setupRecyclerView()
        observeData()
        observeSavedPlaceState()
    }

    private fun setupRecyclerView() {
        historyAdapter = RecentHistoryAdapter(
            onItemClick = { item ->
                (parentFragment?.parentFragment as? RouteFragment)?.handleHistoryItemClick(item)
            },
            onDeleteClick = { item ->
                searchViewModel.deleteHistoryItem(item)
                UndoSnackbar.show(binding.root, "장소가 삭제되었습니다.") {
                    when (item.type) {
                        RecentHistoryItem.TYPE_SEARCH_TEXT -> {
                            item.searchEntity?.let(searchViewModel::insertSearch)
                                ?: searchViewModel.insertSearch(item.mainText)
                        }
                        RecentHistoryItem.TYPE_PLACE -> {
                            item.placeEntity?.let(searchViewModel::insertPlace)
                        }
                        }
                    }
                },
            onSwipeStart = {
                (parentFragment?.parentFragment as? RouteFragment)?.dismissSearchInputFocus()
            }
        )

        binding.rvRecentPlace.apply {
            adapter = historyAdapter
            layoutManager = LinearLayoutManager(context)
            itemAnimator = null
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    isUserScrolling = newState == RecyclerView.SCROLL_STATE_DRAGGING ||
                        newState == RecyclerView.SCROLL_STATE_SETTLING
                    if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                        (parentFragment?.parentFragment as? RouteFragment)?.dismissSearchInputFocus()
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
                    currentPlaceItems = placesOnly
                    val shouldScrollToTop = shouldScrollToTop(placesOnly)
                    historyAdapter.submitList(placesOnly)
                    updateSavedStarColorsFromCache()
                    requestSavedStatesForCurrentItems()
                    updateHistorySnapshot(placesOnly)
                    if (shouldScrollToTop) {
                        binding.rvRecentPlace.scrollToPosition(0)
                    }
                }
            }
        }
    }

    private fun observeSavedPlaceState() {
        groupViewModel.groupList.observe(viewLifecycleOwner) { groups ->
            if (groups.isNotEmpty()) {
                requestSavedStatesForCurrentItems()
            }
        }

        groupViewModel.placeSavedStatesByPlaceId.observe(viewLifecycleOwner) { statesByPlaceId ->
            if (!::historyAdapter.isInitialized) return@observe
            if (statesByPlaceId.isEmpty() && currentPlaceItems.isNotEmpty() && requestedPlaceIds.isNotEmpty()) {
                requestedPlaceIds = emptySet()
                requestSavedStatesForCurrentItems()
            }
            updateSavedStarColorsFromCache()
        }
    }

    private fun updateSavedStarColorsFromCache() {
        if (!::historyAdapter.isInitialized) return
        val statesByPlaceId = groupViewModel.placeSavedStatesByPlaceId.value.orEmpty()
        val colorsByPlaceId = currentPlaceItems.mapNotNull { item ->
            val placeId = item.placeEntity?.placeId ?: return@mapNotNull null
            val latestState = statesByPlaceId[placeId]
                ?.maxByOrNull { it.createdAt }
            latestState?.let { placeId to it.groupColor }
        }.toMap()
        historyAdapter.updateSavedStarColors(colorsByPlaceId)
    }

    private fun requestSavedStatesForCurrentItems() {
        if (currentPlaceItems.isEmpty()) return

        val groups = groupViewModel.groupList.value
        if (groups.isNullOrEmpty()) {
            groupViewModel.fetchGroupList()
            return
        }

        val placeIds = currentPlaceItems
            .mapNotNull { it.placeEntity?.placeId }
            .filter { it.isNotBlank() }
            .toSet()

        val placeIdsToRequest = placeIds - requestedPlaceIds
        requestedPlaceIds = requestedPlaceIds + placeIdsToRequest

        groupViewModel.fetchSavedGroupsForPlaces(placeIdsToRequest, groups)
    }

    private fun shouldScrollToTop(historyList: List<RecentHistoryItem>): Boolean {
        if (!hasObservedHistory || isUserScrolling) return false
        val newFirstKey = historyList.firstOrNull()?.historyKey()
        val isDeletion = historyList.size < lastHistorySize
        return !isDeletion && newFirstKey != null && newFirstKey != lastFirstHistoryKey
    }

    private fun updateHistorySnapshot(historyList: List<RecentHistoryItem>) {
        hasObservedHistory = true
        lastFirstHistoryKey = historyList.firstOrNull()?.historyKey()
        lastHistorySize = historyList.size
    }

    private fun RecentHistoryItem.historyKey(): String {
        return "$type:$mainText:$timestamp"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

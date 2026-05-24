package com.example.pace.ui.search_box

import androidx.fragment.app.Fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.pace.PaceApplication
import com.example.pace.R
import com.example.pace.data.model.RecentHistoryItem
import com.example.pace.data.viewmodel.SearchViewModel
import com.example.pace.data.viewmodel.SearchViewModelFactory
import com.example.pace.databinding.FragmentSearchHistoryBinding
import com.example.pace.ui.main.route.RouteFragment
import kotlinx.coroutines.launch

class SearchHistoryFragment : Fragment() {
    private var _binding: FragmentSearchHistoryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SearchViewModel by activityViewModels {
        SearchViewModelFactory((requireActivity().application as PaceApplication).searchRepository)
    }

    var onRouteOptionClick: ((isMyLocation: Boolean) -> Unit)? = null
    private val recentSearchFragment = RecentSearchFragment()
    private val recentPlaceFragment = RecentPlaceFragment()
    private val recentRouteFragment = RecentRouteFragment()
    private val bookmarkPlaceFragment = BookmarkPlaceFragment()
    private var lastRouteHeaderState: Boolean = false
    private var lastIsScheduleMode: Boolean = false
    private var pendingChipsVisible: Boolean = true
    private var pendingRouteOptionsVisible: Boolean = false
    private var pendingForcePlaceFilter: Boolean = false
    private var pendingResetToRecentSearch: Boolean = false
    private var lastCheckedChipId: Int = View.NO_ID
    private var chipScrollStartX = 0f
    private var chipScrollDismissed = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentSearchHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupChipListeners()
        observeMyPlaces()
        applyPendingStates()
    }

    private fun setupChipListeners() {
        val parent = parentFragment as? RouteFragment

        binding.root.isFocusable = false
        binding.root.isFocusableInTouchMode = false
        binding.searchChipScrollView.isFocusable = false
        binding.searchChipScrollView.isFocusableInTouchMode = false
        binding.chipGroup.isFocusable = false
        binding.chipGroup.isFocusableInTouchMode = false
        binding.searchHistoryFcv.isFocusable = false
        binding.searchHistoryFcv.isFocusableInTouchMode = false

        val touchSlop = android.view.ViewConfiguration.get(requireContext()).scaledTouchSlop
        binding.searchChipScrollView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    chipScrollStartX = event.x
                    chipScrollDismissed = false
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    if (!chipScrollDismissed && kotlin.math.abs(event.x - chipScrollStartX) > touchSlop) {
                        parent?.dismissSearchInputFocus()
                        chipScrollDismissed = true
                    }
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    chipScrollDismissed = false
                }
            }
            false
        }

        binding.chipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            if (lastCheckedChipId == checkedId) return@setOnCheckedStateChangeListener
            lastCheckedChipId = checkedId

            when (checkedIds.firstOrNull()) {
                R.id.chip_recent_search -> showChildFragment(recentSearchFragment, "RECENT_SEARCH")
                R.id.chip_recent_place -> showChildFragment(recentPlaceFragment, "RECENT_PLACE")
                R.id.chip_recent_route -> showChildFragment(recentRouteFragment, "RECENT_ROUTE")
                R.id.chip_saved -> showChildFragment(bookmarkPlaceFragment, "BOOKMARK_PLACE")
            }
        }

        binding.chipSetting.setOnClickListener {
            parent?.dismissSearchInputFocus()
            (parentFragment as? RouteFragment)?.enterBookmarkMode()
        }

        binding.btnMyLocation.setOnClickListener {
            onRouteOptionClick?.invoke(true)
        }

        binding.btnSelectOnMap.setOnClickListener {
            parent?.onSelectOnMapSelected()
        }

        binding.chipHome.setOnClickListener {
            parent?.dismissSearchInputFocus()
            val homePlace = viewModel.homePlace.value
            if (homePlace == null) {
                parent?.enterBookmarkMode()
            } else {
                parent?.handleMyPlaceClick(homePlace)
            }
        }

        binding.chipWork.setOnClickListener {
            parent?.dismissSearchInputFocus()
            val workPlace = viewModel.workPlace.value
            if (workPlace == null) {
                parent?.enterBookmarkMode()
            } else {
                parent?.handleMyPlaceClick(workPlace)
            }
        }

        listOf(
            binding.chipRecentSearch,
            binding.chipRecentPlace,
            binding.chipRecentRoute,
            binding.chipSaved,
            binding.chipSetting
        ).forEach { chip ->
            chip.isFocusable = false
            chip.isFocusableInTouchMode = false
        }

        listOf(
            binding.chipRecentSearch,
            binding.chipRecentPlace,
            binding.chipRecentRoute,
            binding.chipSaved
        ).forEach { chip ->
            chip.setOnClickListener {
                parent?.dismissSearchInputFocus()
            }
        }
    }

    private fun observeMyPlaces() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.homePlace.collect { home ->
                        if (home != null) {
                            binding.chipHome.setChipIconResource(R.drawable.ic_home)
                        } else {
                            binding.chipHome.setChipIconResource(R.drawable.ic_home_unselected)
                        }
                    }
                }
                launch {
                    viewModel.workPlace.collect { work ->
                        if (work != null) {
                            binding.chipWork.setChipIconResource(R.drawable.ic_work_selected)
                        } else {
                            binding.chipWork.setChipIconResource(R.drawable.ic_work_unselected)
                        }
                    }
                }
            }
        }
    }

    private fun applyPendingStates() {
        binding.chipGroup.visibility = if (pendingChipsVisible) View.VISIBLE else View.GONE

        binding.layoutRouteOptions.visibility = if (pendingRouteOptionsVisible) View.VISIBLE else View.GONE

        updateChipsForScheduleMode(lastRouteHeaderState, lastIsScheduleMode)

        ensureSelectedChildFragment()

        if (pendingForcePlaceFilter) {
            binding.chipRecentPlace.isChecked = true
            pendingForcePlaceFilter = false
        }

        if (pendingResetToRecentSearch) {
            resetToRecentSearch()
        }
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) {
            updateChipsForScheduleMode(lastRouteHeaderState, lastIsScheduleMode)
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            updateChipsForScheduleMode(lastRouteHeaderState, lastIsScheduleMode)
        }
    }
    private fun showChildFragment(fragment: Fragment, tag: String) {
        val targetFragment = childFragmentManager.findFragmentByTag(tag) ?: fragment
        if (targetFragment.isAdded && !targetFragment.isHidden) return

        val transaction = childFragmentManager.beginTransaction()

        childFragmentManager.fragments.forEach { child ->
            transaction.hide(child)
        }

        if (targetFragment.isAdded) {
            transaction.show(targetFragment)
        } else {
            transaction.add(R.id.search_history_fcv, targetFragment, tag)
        }

        transaction.commitNowAllowingStateLoss()
    }

    private fun ensureSelectedChildFragment() {
        when (binding.chipGroup.checkedChipId) {
            R.id.chip_recent_search -> {
                lastCheckedChipId = R.id.chip_recent_search
                showChildFragment(recentSearchFragment, "RECENT_SEARCH")
            }
            R.id.chip_recent_place -> {
                lastCheckedChipId = R.id.chip_recent_place
                showChildFragment(recentPlaceFragment, "RECENT_PLACE")
            }
            R.id.chip_recent_route -> {
                lastCheckedChipId = R.id.chip_recent_route
                showChildFragment(recentRouteFragment, "RECENT_ROUTE")
            }
            R.id.chip_saved -> {
                lastCheckedChipId = R.id.chip_saved
                showChildFragment(bookmarkPlaceFragment, "BOOKMARK_PLACE")
            }
        }
    }

    fun setChipsVisibility(isVisible: Boolean) {
        pendingChipsVisible = isVisible // 상태 기억
        if (_binding != null) {
            binding.chipGroup.visibility = if (isVisible) View.VISIBLE else View.GONE
        }
    }
    fun setRouteOptionsVisible(isVisible: Boolean) {
        pendingRouteOptionsVisible = isVisible
        if (_binding == null) return
        if (isVisible) {
            binding.layoutRouteOptions.visibility = View.VISIBLE
        } else {
            binding.layoutRouteOptions.visibility = View.GONE
        }
    }

    fun forcePlaceFilter() {
        pendingForcePlaceFilter = true
        if (_binding != null) {
            binding.chipRecentPlace.isChecked = true
        }
    }

    fun resetToRecentSearch() {
        pendingResetToRecentSearch = true
        if (_binding == null) return

        pendingResetToRecentSearch = false
        binding.chipRecentSearch.visibility = View.VISIBLE
        selectRecentSearchChip()
        binding.searchChipScrollView.post {
            binding.searchChipScrollView.scrollTo(0, 0)
            selectRecentSearchChip()
        }
        ensureSelectedChildFragment()
    }

    private fun selectRecentSearchChip() {
        lastCheckedChipId = View.NO_ID
        binding.chipGroup.clearCheck()
        binding.chipGroup.check(R.id.chip_recent_search)
        binding.chipRecentSearch.isChecked = true
        showChildFragment(recentSearchFragment, "RECENT_SEARCH")
    }

    fun updateChipsForScheduleMode(isRouteHeaderVisible: Boolean, isScheduleMode: Boolean = false) {
        this.lastRouteHeaderState = isRouteHeaderVisible
        this.lastIsScheduleMode = isScheduleMode
        if (_binding == null) return

        val currentCheckedChipId = binding.chipGroup.checkedChipId

        if (isScheduleMode) {
            binding.chipRecentRoute.visibility = View.GONE
        } else {
            binding.chipRecentRoute.visibility = View.VISIBLE
        }

        if (isRouteHeaderVisible) {
            // 루트 헤더가 보일 때 (경로 검색 중) -> 최근 장소 고정
            binding.chipRecentSearch.visibility = View.GONE
            if (currentCheckedChipId == R.id.chip_recent_search ||
                currentCheckedChipId == View.NO_ID ||
                (currentCheckedChipId == R.id.chip_recent_route && isScheduleMode)
            ) {
                binding.chipRecentPlace.isChecked = true
            } else {
                ensureSelectedChildFragment()
            }
        } else {
            // 루트 헤더가 안 보일 때 (일반 검색 중) -> 최근 검색 고정
            binding.chipRecentSearch.visibility = View.VISIBLE
            if (currentCheckedChipId == R.id.chip_recent_route && isScheduleMode) {
                binding.chipRecentSearch.isChecked = true
            } else if (currentCheckedChipId == View.NO_ID) {
                binding.chipRecentSearch.isChecked = true
            } else {
                ensureSelectedChildFragment()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

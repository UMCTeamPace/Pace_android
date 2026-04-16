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
    private var lastCheckedChipId: Int = View.NO_ID

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
        installFocusDismissTouches(binding.root, parentFragment as? RouteFragment)
        observeMyPlaces()
        applyPendingStates()
    }

    private fun setupChipListeners() {
        val parent = parentFragment as? RouteFragment

        binding.chipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            lastCheckedChipId = checkedId

            when (checkedIds.firstOrNull()) {
                R.id.chip_recent_search -> showChildFragment(recentSearchFragment, "RECENT_SEARCH")
                R.id.chip_recent_place -> showChildFragment(recentPlaceFragment, "RECENT_PLACE")
                R.id.chip_recent_route -> showChildFragment(recentRouteFragment, "RECENT_ROUTE")
                R.id.chip_saved -> showChildFragment(bookmarkPlaceFragment, "BOOKMARK_PLACE")
            }
        }

        binding.chipSetting.setOnClickListener {
            (parentFragment as? RouteFragment)?.enterBookmarkMode()
        }

        binding.btnMyLocation.setOnClickListener {
            onRouteOptionClick?.invoke(true)
        }

        binding.btnSelectOnMap.setOnClickListener {
            parent?.onSelectOnMapSelected()
        }

        binding.chipHome.setOnClickListener {
            val homePlace = viewModel.homePlace.value
            if (homePlace == null) {
                parent?.enterBookmarkMode()
            } else {
                parent?.handleMyPlaceClick(homePlace)
            }
        }

        binding.chipWork.setOnClickListener {
            val workPlace = viewModel.workPlace.value
            if (workPlace == null) {
                parent?.enterBookmarkMode()
            } else {
                parent?.handleMyPlaceClick(workPlace)
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

        if (pendingForcePlaceFilter) {
            binding.chipRecentPlace.isChecked = true
            pendingForcePlaceFilter = false
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
        val transaction = childFragmentManager.beginTransaction()

        childFragmentManager.fragments.forEach { child ->
            transaction.hide(child)
        }

        if (fragment.isAdded) {
            transaction.show(fragment)
        } else {
            transaction.add(R.id.search_history_fcv, fragment, tag)
        }

        transaction.commitAllowingStateLoss()
    }

    fun setChipsVisibility(isVisible: Boolean) {
        pendingChipsVisible = isVisible // 상태 기억
        if (_binding != null) {
            binding.chipGroup.visibility = if (isVisible) View.VISIBLE else View.GONE
        }
    }
    fun setRouteOptionsVisible(isVisible: Boolean) {
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
            showChildFragment(recentPlaceFragment, "RECENT_PLACE")
        }
    }

    private fun installFocusDismissTouches(view: View, routeFragment: RouteFragment?) {
        if (view.id != R.id.search_history_fcv) {
            view.setOnTouchListener { _, event ->
                if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                    routeFragment?.dismissSearchInputFocus()
                }
                false
            }
        }

        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                installFocusDismissTouches(view.getChildAt(index), routeFragment)
            }
        }
    }

    fun updateChipsForScheduleMode(isRouteHeaderVisible: Boolean, isScheduleMode: Boolean = false) {
        this.lastRouteHeaderState = isRouteHeaderVisible
        this.lastIsScheduleMode = isScheduleMode
        if (_binding == null) return

        if (isScheduleMode) {
            binding.chipRecentRoute.visibility = View.GONE
        } else {
            binding.chipRecentRoute.visibility = View.VISIBLE
        }

        if (isRouteHeaderVisible) {
            // 루트 헤더가 보일 때 (경로 검색 중) -> 최근 장소 고정
            binding.chipRecentSearch.visibility = View.GONE
            binding.chipRecentPlace.isChecked = true
            showChildFragment(recentPlaceFragment, "RECENT_PLACE")
        } else {
            // 루트 헤더가 안 보일 때 (일반 검색 중) -> 최근 검색 고정
            binding.chipRecentSearch.visibility = View.VISIBLE
            binding.chipRecentSearch.isChecked = true
            showChildFragment(recentSearchFragment, "RECENT_SEARCH")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

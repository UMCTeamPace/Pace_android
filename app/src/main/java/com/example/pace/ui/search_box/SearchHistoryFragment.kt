package com.example.pace.ui.search_box

import androidx.fragment.app.Fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.pace.R
import com.example.pace.databinding.FragmentSearchHistoryBinding
import com.example.pace.ui.main.route.RouteFragment

class SearchHistoryFragment : Fragment() {
    private var _binding: FragmentSearchHistoryBinding? = null
    private val binding get() = _binding!!

    var onRouteOptionClick: ((isMyLocation: Boolean) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentSearchHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (savedInstanceState == null) {
            replaceChildFragment(RecentSearchFragment())
            binding.chipRecentSearch.isChecked = true
        }

        binding.chipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            when (checkedIds.firstOrNull()) {
                R.id.chip_recent_search -> replaceChildFragment(RecentSearchFragment())
                R.id.chip_recent_place -> replaceChildFragment(RecentPlaceFragment())
                R.id.chip_recent_route -> { /* 최근 경로 프래그먼트 */ }
                R.id.chip_saved -> { /* 저장됨 프래그먼트 */ }
                R.id.chip_route_history -> { /* 경로 히스토리 프래그먼트 */ }
                R.id.chip_setting -> {}
            }
        }

        binding.btnMyLocation.setOnClickListener {
            onRouteOptionClick?.invoke(true)
        }

        binding.btnSelectOnMap.setOnClickListener {
            val parent = parentFragment as? RouteFragment
            parent?.onSelectOnMapSelected()
        }
    }

    override fun onResume() {
        super.onResume()
        binding.chipRecentSearch.isChecked = true

        refreshToRecentSearch()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            refreshToRecentSearch()
        }
    }

    private fun refreshToRecentSearch() {
        replaceChildFragment(RecentSearchFragment())
    }

    private fun replaceChildFragment(fragment: Fragment) {
        childFragmentManager.beginTransaction()
            .replace(R.id.search_history_fcv, fragment)
            .commitAllowingStateLoss()
    }

    fun setRouteOptionsVisible(isVisible: Boolean) {
        if (_binding == null) return
        if (isVisible) {
            binding.layoutRouteOptions.visibility = View.VISIBLE
        } else {
            binding.layoutRouteOptions.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // 메모리 누수 방지
    }
}
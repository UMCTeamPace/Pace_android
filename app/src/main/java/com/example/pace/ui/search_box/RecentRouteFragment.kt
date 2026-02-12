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
import com.example.pace.data.repository.SearchRepository
import com.example.pace.data.viewmodel.SearchViewModel
import com.example.pace.data.viewmodel.SearchViewModelFactory
import com.example.pace.databinding.FragmentRecentRouteBinding
import com.example.pace.ui.main.route.RouteFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RecentRouteFragment : Fragment() {
    private var _binding: FragmentRecentRouteBinding? = null
    private val binding get() = _binding!!
    private val searchViewModel: SearchViewModel by viewModels {
        SearchViewModelFactory((requireActivity().application as PaceApplication).searchRepository)
    }
    private lateinit var routeAdapter: RecentRouteAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRecentRouteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        observeData()

        searchViewModel.deleteExpiredData()
    }

    private fun setupRecyclerView() {

        routeAdapter = RecentRouteAdapter(
            onItemClick = { route ->
                (parentFragment?.parentFragment as? RouteFragment)?.handleRecentRouteClick(route)
            },
            onDeleteClick = { route ->
                searchViewModel.deleteRecentRoute(route)
            }
        )
        val touchHelper = CommonSwipeTouchHelper(
            adapter = routeAdapter,
            clampWidthDp = 60
        )
        val itemTouchHelper = ItemTouchHelper(touchHelper)

        routeAdapter.setHelper(touchHelper)
        binding.rvRecentRoute.apply {
            adapter = routeAdapter
            layoutManager = LinearLayoutManager(context)

            itemTouchHelper.attachToRecyclerView(this)

            addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: androidx.recyclerview.widget.RecyclerView, newState: Int) {
                    if (newState == androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_DRAGGING) {
                        touchHelper.closeAllMenus(this@apply)
                    }
                }
            })
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                searchViewModel.recentRoutes.collect { routes ->
                    routeAdapter.submitList(routes)
                }
            }
        }
    }

    private fun cleanUpOldRoutes() {
        val thirtyDaysInMillis = 30L * 24 * 60 * 60 * 1000
        val threshold = System.currentTimeMillis() - thirtyDaysInMillis

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            SearchDatabase.getDatabase(requireContext()).recentRouteDao().deleteOldRoutes(threshold)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
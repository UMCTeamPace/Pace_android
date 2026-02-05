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
import com.example.pace.data.repository.SearchRepository
import com.example.pace.databinding.FragmentRecentRouteBinding
import com.example.pace.ui.main.route.RouteFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class RecentRouteFragment : Fragment() {
    private var _binding: FragmentRecentRouteBinding? = null
    private val binding get() = _binding!!
    private lateinit var repository: SearchRepository
    private lateinit var routeAdapter: RecentRouteAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRecentRouteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val database = SearchDatabase.getDatabase(requireContext())

        repository = SearchRepository(
            database.searchDao(),
            database.recentRouteDao()
        )

        setupRecyclerView()
        observeData()
    }

    private fun setupRecyclerView() {
        routeAdapter = RecentRouteAdapter(
            onItemClick = { route ->
                // RouteFragment 출발지 채우는 로직
            },
            onDeleteClick = { route ->
//                viewLifecycleOwner.lifecycleScope.launch {
//                    SearchDatabase.getDatabase(requireContext()).recentRouteDao().deleteRecentRoute(route)
//                }
            }
        )
        binding.rvRecentRoute.apply {
            adapter = routeAdapter
            layoutManager = LinearLayoutManager(context)
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                SearchDatabase.getDatabase(requireContext()).recentRouteDao().getRecentRoutes().collect { routes ->
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
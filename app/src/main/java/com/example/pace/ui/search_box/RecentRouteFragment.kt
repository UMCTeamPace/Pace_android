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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.pace.BuildConfig
import com.example.pace.PaceApplication
import com.example.pace.data.model.RecentRoute
import com.example.pace.data.viewmodel.SearchViewModel
import com.example.pace.data.viewmodel.SearchViewModelFactory
import com.example.pace.databinding.FragmentRecentRouteBinding
import com.example.pace.ui.main.route.RouteFragment
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.PlacesClient
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

class RecentRouteFragment : Fragment() {
    private var _binding: FragmentRecentRouteBinding? = null
    private val binding get() = _binding!!
    private var hasObservedRoutes = false
    private var lastFirstRouteKey: String? = null
    private var lastRouteSize = 0
    private var isUserScrolling = false
    private val searchViewModel: SearchViewModel by viewModels {
        SearchViewModelFactory((requireActivity().application as PaceApplication).searchRepository)
    }
    private lateinit var routeAdapter: RecentRouteAdapter
    private lateinit var placesClient: PlacesClient

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRecentRouteBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.root.isFocusable = false
        binding.root.isFocusableInTouchMode = false
        binding.rvRecentRoute.isFocusable = false
        binding.rvRecentRoute.isFocusableInTouchMode = false
        initPlacesClient()
        setupRecyclerView()
        observeData()
        searchViewModel.deleteExpiredData()
    }

    private fun initPlacesClient() {
        if (!Places.isInitialized()) {
            Places.initialize(requireContext(), BuildConfig.GOOGLE_API_KEY)
        }
        placesClient = Places.createClient(requireContext())
    }

    private fun setupRecyclerView() {
        routeAdapter = RecentRouteAdapter(
            onItemClick = { item ->
                (parentFragment?.parentFragment as? RouteFragment)
                    ?.handleRecentRouteClick(item.route, item.startPlaceName, item.endPlaceName)
            },
            onDeleteClick = { route ->
                searchViewModel.deleteRecentRoute(route)
                UndoSnackbar.show(binding.root, "경로가 삭제되었습니다.") {
                    searchViewModel.insertRecentRoute(route)
                }
            },
            onSwipeStart = {
                (parentFragment?.parentFragment as? RouteFragment)?.dismissSearchInputFocus()
            }
        )

        binding.rvRecentRoute.apply {
            adapter = routeAdapter
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
                searchViewModel.recentRoutes.collect { routes ->
                    val shouldScrollToTop = shouldScrollToTop(routes)
                    val uiItems = resolveRouteUiItems(routes)
                    routeAdapter.submitList(uiItems)
                    updateRouteSnapshot(routes)
                    if (shouldScrollToTop) {
                        binding.rvRecentRoute.scrollToPosition(0)
                    }
                }
            }
        }
    }

    private fun shouldScrollToTop(routes: List<com.example.pace.data.model.RecentRoute>): Boolean {
        if (!hasObservedRoutes || isUserScrolling) return false
        val newFirstKey = routes.firstOrNull()?.routeKey()
        val isDeletion = routes.size < lastRouteSize
        return !isDeletion && newFirstKey != null && newFirstKey != lastFirstRouteKey
    }

    private fun updateRouteSnapshot(routes: List<com.example.pace.data.model.RecentRoute>) {
        hasObservedRoutes = true
        lastFirstRouteKey = routes.firstOrNull()?.routeKey()
        lastRouteSize = routes.size
    }

    private fun com.example.pace.data.model.RecentRoute.routeKey(): String {
        return "$startPlaceId:$endPlaceId:$saveTime"
    }

    private suspend fun resolveRouteUiItems(routes: List<RecentRoute>): List<RecentRouteUiItem> {
        return routes.map { route ->
            RecentRouteUiItem(
                route = route,
                startPlaceName = fetchPlaceName(route.startPlaceId) ?: route.startPlaceId,
                endPlaceName = fetchPlaceName(route.endPlaceId) ?: route.endPlaceId
            )
        }
    }

    private suspend fun fetchPlaceName(placeId: String): String? = suspendCancellableCoroutine { continuation ->
        if (placeId.isBlank()) {
            continuation.resume(null, null)
            return@suspendCancellableCoroutine
        }

        val request = FetchPlaceRequest.newInstance(placeId, listOf(Place.Field.NAME))
        placesClient.fetchPlace(request)
            .addOnSuccessListener { response ->
                continuation.resume(response.place.name, null)
            }
            .addOnFailureListener {
                continuation.resume(null, null)
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

package com.example.pace.ui.search_box

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pace.R
import com.example.pace.data.model.response.RouteResponse
import com.example.pace.databinding.FragmentRouteResultBinding
import com.example.pace.ui.main.route.RouteFragment
import android.graphics.Rect
import androidx.recyclerview.widget.RecyclerView

class RouteResultFragment(
    private var destination: String
) : Fragment(){
    private var _binding: FragmentRouteResultBinding? = null
    private val binding get() = _binding!!

    var onChipSelected: ((String?) -> Unit)? = null

    private lateinit var adapter: RouteAdapter
    private var isLoading = true
    private var hasRoutes = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRouteResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()

        setupChipListener()

        binding.searchLocationRv.layoutManager = LinearLayoutManager(requireContext())
        binding.searchLocationRv.adapter = adapter
        applyResultState()
    }

    private fun setupRecyclerView() {
        adapter = RouteAdapter(
            context = requireContext(),
            items = emptyList(),
            onItemClick = { item ->
                (parentFragment as? RouteFragment)?.showRouteDetailOverlay(item)
            },
            destination = destination,
            onSelectClick = { item ->
                (parentFragment as? RouteFragment)?.onRouteSelectedFinal(item)
            }
        )
        binding.searchLocationRv.adapter = adapter
        binding.searchLocationRv.layoutManager = LinearLayoutManager(requireContext())

        if (binding.searchLocationRv.itemDecorationCount == 0) {
            binding.searchLocationRv.addItemDecoration(GapItemDecoration(8))
        }
    }

    private fun setupChipListener() {
        binding.routeChipGroup.setOnCheckedStateChangeListener { group, checkedIds ->

            val checkedId = checkedIds.firstOrNull()

            val transitType = when (checkedId) {
                R.id.chip_bus -> "BUS"
                R.id.chip_subway -> "SUBWAY"
                else -> null
            }

            // 부모 프래그먼트에게 "칩 바뀌었으니 API 다시 불러줘!" 라고 신호 보냄
            onChipSelected?.invoke(transitType)
        }
    }

    fun updateRoutes(newItems: List<RouteResponse>, newDestination: String) {
        this.destination = newDestination
        hasRoutes = newItems.isNotEmpty()
        isLoading = false

        adapter = RouteAdapter(
            context = requireContext(),
            items = newItems,
            destination = this.destination,
            onItemClick = { (parentFragment as? RouteFragment)?.showRouteDetailOverlay(it) },
            onSelectClick = { (parentFragment as? RouteFragment)?.onRouteSelectedFinal(it) }
        )
        binding.searchLocationRv.adapter = adapter
        applyResultState()
    }

    fun setLoading(loading: Boolean) {
        isLoading = loading
        if (loading) {
            hasRoutes = false
        }
        applyResultState()
    }

    private fun applyResultState() {
        val binding = _binding ?: return
        when {
            isLoading -> {
                binding.searchLocationRv.visibility = View.GONE
                binding.layoutEmptyRouteResult.visibility = View.GONE
            }
            hasRoutes -> {
                binding.searchLocationRv.visibility = View.VISIBLE
                binding.layoutEmptyRouteResult.visibility = View.GONE
            }
            else -> {
                binding.searchLocationRv.visibility = View.GONE
                binding.layoutEmptyRouteResult.visibility = View.VISIBLE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class GapItemDecoration(private val gapHeightDp: Int) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)
        val itemCount = state.itemCount

        val px = (gapHeightDp * view.resources.displayMetrics.density).toInt()

        if (position != itemCount - 1) {
            outRect.bottom = px
        }
    }
}

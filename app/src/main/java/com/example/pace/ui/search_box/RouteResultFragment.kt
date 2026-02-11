package com.example.pace.ui.search_box

import android.content.Context.MODE_PRIVATE
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pace.R
import com.example.pace.data.model.RouteResponseSample
import com.example.pace.data.model.request.Destination
import com.example.pace.data.model.response.RouteResponse
import com.example.pace.databinding.FragmentRouteResultBinding
import com.example.pace.ui.main.route.RouteFragment

class RouteResultFragment(
    private val destination: String
) : Fragment(){
    private var _binding: FragmentRouteResultBinding? = null
    private val binding get() = _binding!!

    var onChipSelected: ((String?) -> Unit)? = null

    private lateinit var adapter: RouteAdapter

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

    fun updateRoutes(newItems: List<RouteResponse>) {
        // 어댑터를 새로 만들어서 갈아끼우거나, Adapter 내부에 updateItems 함수를 만들어서 호출
        // 여기서는 간단하게 새로 생성하는 방식 유지 (Adapter에 update 기능이 없다면)
        adapter = RouteAdapter(
            context = requireContext(),
            items = newItems,
            destination = destination,
            onItemClick = { (parentFragment as? RouteFragment)?.showRouteDetailOverlay(it) },
            onSelectClick = { (parentFragment as? RouteFragment)?.onRouteSelectedFinal(it) }
        )
        binding.searchLocationRv.adapter = adapter
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
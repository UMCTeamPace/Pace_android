package com.example.pace.ui.search_box

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pace.data.model.RouteDetail
import com.example.pace.data.model.RouteResponse
import com.example.pace.data.model.TransitDetail
import com.example.pace.databinding.FragmentRouteResultBinding
import com.example.pace.ui.main.route.RouteFragment

class RouteResultFragment : Fragment(){
    private var _binding: FragmentRouteResultBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentRouteResultBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val dummyData = createFullDummyData()

        val adapter = RouteAdapter(
            items = dummyData,

            onItemClick = { item ->
                (parentFragment as? RouteFragment)?.showRouteDetailOverlay(item)
            },

            onSelectClick = { item ->
                (parentFragment as? RouteFragment)?.onRouteSelectedFinal(item)

            }
        )
//        val adapter = RouteAdapter(items = dummyData) { item ->
//            (parentFragment as? RouteFragment)?.showRouteDetailOverlay(item)
//        }

        binding.searchLocationRv.layoutManager = LinearLayoutManager(requireContext())
        binding.searchLocationRv.adapter = adapter
    }

    private fun createFullDummyData(): List<RouteResponse> {
        // 구간 1: 도보
        val walk1 = RouteDetail(
            sequence = 1,
            startLat = 37.5563, startLng = 126.9723,
            endLat = 37.5554, endLng = 126.9725,
            duration = 99, distance = 98,
            description = "서울역버스환승센터까지 도보",
            transitDetail = null
        )

        // 구간 2: 버스 (모든 상세 정보 포함)
        val bus1 = RouteDetail(
            sequence = 2,
            startLat = 37.5554, startLng = 126.9725,
            endLat = 37.4958, endLng = 127.0285,
            duration = 1823, distance = 11241,
            description = "버스 탑승",
            transitDetail = TransitDetail(
                transitType = "BUS",
                lineName = "402번",
                lineColor = "#374ff2",
                stopCount = 15,
                departureStop = "서울역버스환승센터",
                arrivalStop = "강남역",
                departureTime = "2026-02-03T09:11:13",
                arrivalTime = "2026-02-03T09:41:36",
                locationLat = 37.4958,
                locationLng = 127.0285,
                points = "dummy_polyline_string_encrypted", // 지도 그릴 때 사용
                headsign = "장지공영차고지행"
            )
        )

        // 구간 3: 도보
        val walk2 = RouteDetail(
            sequence = 3,
            startLat = 37.4958, startLng = 127.0285,
            endLat = 37.4979, endLng = 127.0276,
            duration = 245, distance = 244,
            description = "도착지까지 도보",
            transitDetail = null
        )

        // 전체 경로 객체
        val fullRoute = RouteResponse(
            totalDistance = 11583,
            totalTime = 2741,
            arrivalTime = "2026-02-03T09:45:41",
            departureTime = "2026-02-03T09:00:00",
            routeDetailInfoResDTOList = listOf(walk1, bus1, walk2)
        )

        // 리스트로 반환 (똑같은 데이터 3개)
        return listOf(fullRoute, fullRoute, fullRoute)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
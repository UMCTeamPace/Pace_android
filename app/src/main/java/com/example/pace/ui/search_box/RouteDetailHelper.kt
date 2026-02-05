package com.example.pace.ui.search_box

import android.view.View
import com.example.pace.data.db.RouteResponse
import com.example.pace.databinding.BottomSheetRouteDetailBinding

object RouteDetailHelper {

    fun setupData(bottomSheetView: View, item: RouteResponse) {
        // 1. 바인딩 연결 (팀원이 만든 XML인 bottom_sheet_route_detail.xml과 연결)
        // 주의: XML 파일이 <layout> 태그로 감싸져 있어야 DataBinding이 가능합니다.
        // 아니라면 findViewById를 써야 하지만, 이게 훨씬 편합니다.
        val binding = BottomSheetRouteDetailBinding.bind(bottomSheetView)

        // =============================================================
        // [팀원 작성 영역] 여기서 item 데이터를 뷰에 꽂아넣으세요.
        // =============================================================

        // 예시 1: 텍스트 채우기
        // binding.tvTotalTime.text = "${item.totalTime / 60}분 소요"

        // 예시 2: 시간 정보
        val startTime = item.departureTime.split("T").last().take(5)
        val endTime = item.arrivalTime.split("T").last().take(5)
        binding.routeDetailDepartureTimeTv.text = startTime

        // 예시 3: 리사이클러뷰 연결 (상세 경로 리스트)
        // val adapter = RouteDetailAdapter(item.routeDetailInfoResDTOList)
        // binding.rvRouteDetailList.adapter = adapter
        // binding.rvRouteDetailList.layoutManager = LinearLayoutManager(bottomSheetView.context)
    }
}
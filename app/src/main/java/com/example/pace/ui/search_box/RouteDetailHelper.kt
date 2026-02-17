package com.example.pace.ui.search_box

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.text.buildSpannedString
import androidx.core.text.color
import androidx.core.view.setPadding
import androidx.core.view.updatePadding
import com.example.pace.R
import com.example.pace.data.model.RouteResponseSample
import com.example.pace.data.model.response.RouteResponse
import com.example.pace.databinding.BottomSheetRouteDetailBinding
import com.example.pace.databinding.ItemRouteDetailArrivalBinding
import com.example.pace.databinding.ItemRouteDetailBriefBinding
import com.example.pace.databinding.ItemRouteDetailVehicleBinding
import com.example.pace.databinding.ItemRouteDetailWalkBinding
import com.example.pace.ui.RouteCalculator

object RouteDetailHelper {

    fun setupData(context: Context, bottomSheetView: View, item: RouteResponse, destination: String, startName: String) {
        val binding = BottomSheetRouteDetailBinding.bind(bottomSheetView)

        // 기본 정보
        val departureTime = RouteCalculator.convertUtcToKst(item.departureTime)
        val arrivalTime = RouteCalculator.convertUtcToKst(item.arrivalTime)
        binding.routeDetailDepartureTimeTv.text = departureTime
        binding.routeDetailArrivalTimeTv.text = arrivalTime
        binding.routeDetailTotalTv.text = buildSpannedString {
            append("총 ")
            color(context.resources.getColor(R.color.primary_500)){
                if(item.totalTime / 3600 > 0){
                    val time = item.totalTime % 3600
                    append("${item.totalTime / 3600} 시간 ")
                    if(time / 60 > 0){
                        append("${time / 60}분")
                    }
                }else{
                    append("${item.totalTime / 60}분")
                }

            }
            append(" 소요")
        }

        // 기존 데이터 삭제
        binding.routeDetailBriefLl.removeAllViews()
        binding.routeDetailExpandedLl.removeAllViews()

        var lastArrivalStop: String? = null

        // 데이터 동적 바인딩
        item.routeDetails.forEach { data ->
            val briefBinding = ItemRouteDetailBriefBinding.inflate(LayoutInflater.from(context))
            val expandedVehicleBinding = ItemRouteDetailVehicleBinding.inflate(LayoutInflater.from(context), binding.routeDetailExpandedLl, false)
            val expandedWalkBinding = ItemRouteDetailWalkBinding.inflate(LayoutInflater.from(context), binding.routeDetailExpandedLl, false)

            when(data.transitDetail){
                // 도보
                null -> {
                    // 일직선 정보
                    if(data.sequence == 1){
                        briefBinding.itemRouteDetailBriefIv.setImageResource(R.drawable.ic_people)
                    }else{
                        briefBinding.itemRouteDetailBriefIv.visibility = View.GONE
                        briefBinding.itemRouteDetailBriefTv.updatePadding(0)
                    }
                    briefBinding.itemRouteDetailBriefTv.text = "${data.duration / 60}분"
                    briefBinding.itemRouteDetailBriefTv.setTextColor(context.resources.getColor(R.color.gray_600))

                    val walkTitle = if (data.sequence == 1) {
                        startName
                    } else {
                        "${lastArrivalStop ?: "알 수 없는 정류장"} 하차"
                    }
                    // 상세 정보
                    expandedWalkBinding.itemRouteDetailWalkTv.text = walkTitle
                    expandedWalkBinding.itemRouteDetailWalkTimeTv.text = "${data.duration / 60}분 도보"
                    expandedWalkBinding.itemRouteDetailWalkDistanceTv.text = "${data.distance}M 이동"
                    if(data.sequence == 1){
                        expandedWalkBinding.itemRouteDetailWalkStartTv.visibility = View.VISIBLE
                        expandedWalkBinding.itemRouteDetailWalkStartTv.text = departureTime
                    }else{
                        expandedWalkBinding.itemRouteDetailWalkStartTv.visibility = View.INVISIBLE
                    }
                    binding.routeDetailExpandedLl.addView(expandedWalkBinding.root)
                }
                // 대중교통
                else -> {
                    val transitType = data.transitDetail.transitType
                    var arrivalStopName = data.transitDetail.arrivalStop

                    if (transitType == "SUBWAY" && !arrivalStopName.endsWith("역")) {
                        arrivalStopName += "역"
                    }

                    lastArrivalStop = arrivalStopName
                    // 아이콘 및 색상 설정
                    val layoutDrawable = (ContextCompat.getDrawable(context, R.drawable.ic_route_detail))?.mutate() as LayerDrawable
                    val iconColor = layoutDrawable.findDrawableByLayerId(R.id.ic_route_detail_color)?.mutate() as GradientDrawable
                    val bgColor = briefBinding.itemRouteDetailBriefTv.background.mutate() as GradientDrawable
                    val lineColor = Color.parseColor(data.transitDetail.lineColor)
                    var moreStation = false

                    when(data.transitDetail.transitType){
                        "BUS" -> {
                            val busDrawable = ContextCompat.getDrawable(context, R.drawable.ic_bus)
                            layoutDrawable.setDrawableByLayerId(R.id.ic_route_detail_vehicle, busDrawable)
                        }
                        "SUBWAY" -> {
                            val subwayDrawable = ContextCompat.getDrawable(context, R.drawable.ic_subway)
                            layoutDrawable.setDrawableByLayerId(R.id.ic_route_detail_vehicle, subwayDrawable)
                        }
                    }
                    iconColor.setColor(lineColor)
                    bgColor.setColor(lineColor)

                    // 일직선 정보
                    briefBinding.itemRouteDetailBriefIv.setImageDrawable(layoutDrawable)
                    briefBinding.itemRouteDetailBriefTv.text = "${data.duration / 60}분"

                    // 상세 정보
                    expandedVehicleBinding.itemRouteDetailVehicleIv.setImageDrawable(layoutDrawable)
                    expandedVehicleBinding.itemRouteDetailVehicleView.setBackgroundColor(lineColor)
                    var departureStopName = data.transitDetail.departureStop
                    if (data.transitDetail.transitType == "SUBWAY" && !departureStopName.endsWith("역")) {
                        departureStopName += "역"
                    }
                    expandedVehicleBinding.itemRouteDetailVehicleTv.text = "${departureStopName} 승차"
                    expandedVehicleBinding.itemRouteDetailVehicleTimeTv.text = data.transitDetail.departureTime.split("T").last().take(5)
                    expandedVehicleBinding.itemRouteDetailVehicleLineTv.text = data.transitDetail.shortName

                    if(data.transitDetail.stationPath.isNullOrEmpty()){
                        expandedVehicleBinding.itemRouteDetailVehicleDirectionTv.text = data.transitDetail.headsign + "행" ?: "방면 정보 없음"
                    }else{
                        expandedVehicleBinding.itemRouteDetailVehicleDirectionTv.text = data.transitDetail.stationPath[0] + "행 방면"
                    }

                    expandedVehicleBinding.itemRouteDetailVehicleStationsTv.text = "${data.transitDetail.stopCount}개 정류장 이동"
                    expandedVehicleBinding.itemRouteDetailVehicleStationsTimeTv.text = "${data.duration / 60}분"
                    expandedVehicleBinding.itemRouteDetailVehicleStationsLl.setOnClickListener {
                        if(moreStation){
                            expandedVehicleBinding.itemRouteDetailVehicleArrowDownIv.setImageResource(R.drawable.ic_arrow_down)
                            expandedVehicleBinding.itemRouteDetailVehicleRv.visibility = View.GONE
                            expandedVehicleBinding.itemRouteDetailVehicleStationsLl.setPadding(0, 0, 0, (17 * context.resources.displayMetrics.density).toInt())
                            moreStation = false
                        }else{
                            expandedVehicleBinding.itemRouteDetailVehicleArrowDownIv.setImageResource(R.drawable.ic_arrow_up)
                            expandedVehicleBinding.itemRouteDetailVehicleRv.visibility = View.VISIBLE
                            expandedVehicleBinding.itemRouteDetailVehicleStationsLl.setPadding(0)
                            moreStation = true
                        }
                    }
                    binding.routeDetailExpandedLl.addView(expandedVehicleBinding.root)

                    // 정류장 리사이클러뷰
                    val adapter = RouteDetailStationAdapter(context, data.transitDetail.stationPath!!, lineColor)
                    expandedVehicleBinding.itemRouteDetailVehicleRv.adapter = adapter
                }
            }
            // 일직선 데이터 추가
            val weight = RouteCalculator.calculateWeight(data.duration)
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
            binding.routeDetailBriefLl.addView(briefBinding.root, params)

        }

        val arrivalBinding = ItemRouteDetailArrivalBinding.inflate(LayoutInflater.from(context))
        arrivalBinding.itemRouteDetailArrivalTimeTv.text = arrivalTime
        arrivalBinding.itemRouteDetailArrivalTv.text = destination
        binding.routeDetailExpandedLl.addView(arrivalBinding.root)
    }
}
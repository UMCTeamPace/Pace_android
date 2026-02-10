package com.example.pace.ui.search_box

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import com.example.pace.R
import com.example.pace.data.model.response.RouteResponse
import com.example.pace.databinding.ItemRouteBinding
import com.example.pace.databinding.ItemRouteDetailBriefBinding
import com.example.pace.databinding.ItemRouteVehicleBinding
import com.example.pace.ui.WeightCalculator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class RouteAdapter(
    private val context: Context,
    private val items: List<RouteResponse>,
    private val onItemClick: (RouteResponse) -> Unit,
    private val onSelectClick: (RouteResponse) -> Unit
) : RecyclerView.Adapter<RouteAdapter.RouteViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RouteViewHolder {
        val binding = ItemRouteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RouteViewHolder(binding, context)
    }

    override fun onBindViewHolder(holder: RouteViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class RouteViewHolder(val binding: ItemRouteBinding, val context: Context) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: RouteResponse) {
            binding.routeBriefLl.removeAllViews()
            binding.routeVehicleLl.removeAllViews()

            binding.routeTotalTv.text = "총 ${item.totalTime / 60}분 소요"

            // 시간 자르기 (2026-02-03T09:00:00 -> 09:00)
            // 서버 데이터가 null이거나 형식이 다를 경우를 대비해 안전하게 처리
            val startTime = convertUtcToKst(item.departureTime)
            val endTime = convertUtcToKst(item.arrivalTime)

            binding.routeDepartureTimeTv.text = startTime
            binding.routeArrivalTimeTv.text = endTime

            item.routeDetails.forEachIndexed { index, data ->
                val briefBinding = ItemRouteDetailBriefBinding.inflate(LayoutInflater.from(context), binding.routeBriefLl, false)

                // 3-1. 걷기 (TransitDetail이 null인 경우)
                if (data.transitDetail == null) {
                    if (data.sequence == 1) { // 첫 번째 순서면 사람 아이콘
                        briefBinding.itemRouteDetailBriefIv.setImageResource(R.drawable.ic_people)
                    } else {
                        briefBinding.itemRouteDetailBriefIv.visibility = View.GONE
                        briefBinding.itemRouteDetailBriefTv.updatePadding(0)
                    }
                    briefBinding.itemRouteDetailBriefTv.text = "${data.duration / 60}분"
                    briefBinding.itemRouteDetailBriefTv.setTextColor(ContextCompat.getColor(context, R.color.gray_600))

                    // 마지막 단계(하차) 처리
                    // 리스트의 마지막 인덱스인지 확인
                    if (index == item.routeDetails.size - 1) {
                        val vehicleBinding = ItemRouteVehicleBinding.inflate(LayoutInflater.from(context), binding.routeVehicleLl, false)
                        vehicleBinding.itemRouteVehicleIv.setImageResource(R.drawable.ic_route_item_arrival_icon)
                        vehicleBinding.itemRouteVehicleLineTv.text = "도착"
                        vehicleBinding.itemRouteVehicleLineTv.setTextColor(ContextCompat.getColor(context, R.color.black))
                        vehicleBinding.itemRouteVehicleView.visibility = View.GONE

                        binding.routeVehicleLl.addView(vehicleBinding.root)
                    }
                }
                // 3-2. 대중교통 (버스, 지하철)
                else {
                    val vehicleBinding = ItemRouteVehicleBinding.inflate(LayoutInflater.from(context), binding.routeVehicleLl, false)

                    // 아이콘 및 색상 설정
                    val layoutDrawable = ContextCompat.getDrawable(context, R.drawable.ic_route_detail)?.mutate() as LayerDrawable
                    val iconShape = layoutDrawable.findDrawableByLayerId(R.id.ic_route_detail_color).mutate() as GradientDrawable
                    val briefBg = briefBinding.itemRouteDetailBriefTv.background.mutate() as GradientDrawable

                    // 색상 파싱 (서버에서 #RRGGBB 형태로 온다고 가정, 실패 시 기본값 검정)
                    val lineColorCode = try {
                        Color.parseColor(data.transitDetail.lineColor ?: "#000000")
                    } catch (e: Exception) {
                        Color.BLACK
                    }

                    // 교통 수단별 아이콘 변경
                    when (data.transitDetail.transitType) {
                        "BUS" -> {
                            val busDrawable = ContextCompat.getDrawable(context, R.drawable.ic_bus)
                            layoutDrawable.setDrawableByLayerId(R.id.ic_route_detail_vehicle, busDrawable)
                        }
                        "SUBWAY" -> {
                            val subwayDrawable = ContextCompat.getDrawable(context, R.drawable.ic_subway)
                            layoutDrawable.setDrawableByLayerId(R.id.ic_route_detail_vehicle, subwayDrawable)
                        }
                    }

                    // 색상 적용
                    iconShape.setColor(lineColorCode)
                    briefBg.setColor(lineColorCode)

                    // [상단 바] 정보 설정
                    briefBinding.itemRouteDetailBriefIv.setImageDrawable(layoutDrawable)
                    briefBinding.itemRouteDetailBriefTv.text = "${data.duration / 60}분"

                    // [하단 리스트] 상세 정보 설정
                    vehicleBinding.itemRouteVehicleIv.setImageDrawable(layoutDrawable)
                    vehicleBinding.itemRouteVehicleLineTv.text = data.transitDetail.lineName // shortName -> lineName (데이터 모델 확인 필요)
                    vehicleBinding.itemRouteVehicleLineTv.setTextColor(lineColorCode)
                    vehicleBinding.itemRouteVehicleTv.text = "${data.transitDetail.departureStop} 승차"

                    binding.routeVehicleLl.addView(vehicleBinding.root)
                }

                // 상단 바(Brief) 뷰 추가 (Weight 적용)
                val weight = WeightCalculator.forRouteDetailBrief(data.duration)
                val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
                binding.routeBriefLl.addView(briefBinding.root, params)
            }

            // 클릭 리스너 연결
            binding.root.setOnClickListener { onItemClick(item) }
            binding.routeDetailSelectBtn.setOnClickListener { onSelectClick(item) }
        }
        private fun convertUtcToKst(serverDateStr: String?): String {
            if (serverDateStr.isNullOrEmpty()) return ""

            try {
                // 1. 서버가 주는 날짜 형식 정의 (예: 2026-02-03T09:00:00)
                // 만약 서버가 초 단위 뒤에 .000 (밀리초)을 보낸다면 "yyyy-MM-dd'T'HH:mm:ss.SSS"로 수정해야 함
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                inputFormat.timeZone = TimeZone.getTimeZone("UTC") // "이 시간은 UTC다"라고 명시

                // 2. 문자열을 Date 객체로 변환
                val date: Date = inputFormat.parse(serverDateStr) ?: return ""

                // 3. 출력할 형식 정의 (예: 09:00)
                val outputFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                outputFormat.timeZone = TimeZone.getTimeZone("Asia/Seoul") // "한국 시간으로 바꿔라" (+9시간)

                return outputFormat.format(date)

            } catch (e: Exception) {
                e.printStackTrace()
                return "" // 에러 나면 빈 문자열 반환 (혹은 원래 문자열 반환)
            }
        }


            // 기본 정보 세팅
//            binding.routeTotalTv.text = "총 ${item.totalTime / 60}분 소요"
//
//            // 시간 자르기 (2026-02-03T09:00:00 -> 09:00)
//            val startTime = item.departureTime.split("T").last().take(5)
//            val endTime = item.arrivalTime.split("T").last().take(5)
//            binding.routeDepartureTimeTv.text = startTime
//            binding.routeArrivalTimeTv.text = endTime

//            var index = 0
//            // 동적으로 데이터 가져오기
//            item.routeDetailInfoResDTOList.forEach { data ->
//                val briefBinding = ItemRouteDetailBriefBinding.inflate(LayoutInflater.from(context), binding.routeBriefLl, false)
//                val vehicleBinding = ItemRouteVehicleBinding.inflate(LayoutInflater.from(context), binding.routeVehicleLl, false)
//
//                when(data.transitDetail){
//                    // 걷기
//                    null -> {
//                        if(data.sequence == 1){
//                            briefBinding.itemRouteDetailBriefIv.setImageResource(R.drawable.ic_people)
//                        }else{
//                            briefBinding.itemRouteDetailBriefIv.visibility = View.GONE
//                            briefBinding.itemRouteDetailBriefTv.updatePadding(0)
//                        }
//                        briefBinding.itemRouteDetailBriefTv.text = "${data.duration / 60}분"
//                        briefBinding.itemRouteDetailBriefTv.setTextColor(context.resources.getColor(R.color.gray_600))
//
//                        if(index == item.routeDetailInfoResDTOList.size - 1){
//                            vehicleBinding.itemRouteVehicleIv.setImageResource(R.drawable.ic_route_item_arrival_icon)
//                            vehicleBinding.itemRouteVehicleLineTv.text = "하차"
//                            vehicleBinding.itemRouteVehicleLineTv.setTextColor(context.resources.getColor(R.color.black))
//                            vehicleBinding.itemRouteVehicleView.visibility = View.GONE
//
//                            binding.routeVehicleLl.addView(vehicleBinding.root)
//                        }
//                    }
//                    // 대중교통
//                    else -> {
//                        // 아이콘 변경
//                        val layoutDrawable = ContextCompat.getDrawable(context, R.drawable.ic_route_detail)?.mutate() as LayerDrawable
//                        val iconColor = layoutDrawable.findDrawableByLayerId(R.id.ic_route_detail_color).mutate() as GradientDrawable
//                        val bgColor = briefBinding.itemRouteDetailBriefTv.background.mutate() as GradientDrawable
//                        val lineColor = Color.parseColor(data.transitDetail.lineColor)
//
//                        when(data.transitDetail.transitType){
//                            "BUS" -> {
//                                val busDrawable = ContextCompat.getDrawable(context, R.drawable.ic_bus)
//                                layoutDrawable.setDrawableByLayerId(R.id.ic_route_detail_vehicle, busDrawable)
//                            }
//                            "SUBWAY" -> {
//                                val subwayDrawable = ContextCompat.getDrawable(context, R.drawable.ic_subway)
//                                layoutDrawable.setDrawableByLayerId(R.id.ic_route_detail_vehicle, subwayDrawable)
//                            }
//                        }
//                        iconColor.setColor(lineColor)
//                        bgColor.setColor(lineColor)
//
//                        // 일직선 정보
//                        briefBinding.itemRouteDetailBriefIv.setImageDrawable(layoutDrawable)
//                        briefBinding.itemRouteDetailBriefTv.text = "${data.duration / 60}분"
//
//                        // 상세 정보
//                        vehicleBinding.itemRouteVehicleIv.setImageDrawable(layoutDrawable)
//                        vehicleBinding.itemRouteVehicleLineTv.text = data.transitDetail.shortName
//                        vehicleBinding.itemRouteVehicleLineTv.setTextColor(lineColor)
//                        vehicleBinding.itemRouteVehicleTv.text = data.transitDetail.departureStop
//
//                        if(vehicleBinding.root.parent != null){
//                            (vehicleBinding.root.parent as ViewGroup).removeView(vehicleBinding.root)
//                        }
//                        binding.routeVehicleLl.addView(vehicleBinding.root)
//                    }
//                }
//
//                if(briefBinding.root.parent != null){
//                    (briefBinding.root.parent as ViewGroup).removeView(briefBinding.root)
//                }
//                val weight = WeightCalculator.forRouteDetailBrief(data.duration)
//                val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
//                binding.routeBriefLl.addView(briefBinding.root, params)
//
//                index++
//                Log.d("check/condition", (index == item.routeDetailInfoResDTOList.size -1).toString() )
//            }


//            binding.root.setOnClickListener { onItemClick(item) }
//            binding.routeDetailSelectBtn.setOnClickListener {
//                onSelectClick(item)
//            }
        }
    }

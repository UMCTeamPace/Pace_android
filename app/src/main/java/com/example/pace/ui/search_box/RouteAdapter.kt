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
import com.example.pace.ui.RouteCalculator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class RouteAdapter(
    private val context: Context,
    private val items: List<RouteResponse>,
    private val destination: String,
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

            binding.routeTotalTv.text = if(item.totalTime / 3600L > 0 ){
                val time = item.totalTime % 3600L
                if(time / 60L > 0){
                    "${item.totalTime / 3600L}시간 ${time / 60L}분"
                }else{
                    "${item.totalTime / 3600L}시간"
                }
            }else{
                "${item.totalTime / 60L}분"
            }

            // 시간 자르기 (2026-02-03T09:00:00 -> 09:00)
            // 서버 데이터가 null이거나 형식이 다를 경우를 대비해 안전하게 처리
            val startTime = RouteCalculator.convertUtcToKst(item.departureTime)
            val endTime = RouteCalculator.convertUtcToKst(item.arrivalTime)

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
                    vehicleBinding.itemRouteVehicleLineTv.text = data.transitDetail.shortName
                    vehicleBinding.itemRouteVehicleLineTv.setTextColor(lineColorCode)
                    vehicleBinding.itemRouteVehicleTv.text = "${data.transitDetail.departureStop} 승차"

                    binding.routeVehicleLl.addView(vehicleBinding.root)
                }

                // 마지막 단계(하차) 처리
                // 리스트의 마지막 인덱스인지 확인
                if (index == item.routeDetails.size - 1) {
                    val vehicleBinding = ItemRouteVehicleBinding.inflate(LayoutInflater.from(context), binding.routeVehicleLl, false)
                    vehicleBinding.itemRouteVehicleIv.setImageResource(R.drawable.ic_route_item_arrival_icon)
                    vehicleBinding.itemRouteVehicleLineTv.text = "도착"
                    vehicleBinding.itemRouteVehicleLineTv.setTextColor(ContextCompat.getColor(context, R.color.black))
                    vehicleBinding.itemRouteVehicleView.visibility = View.GONE
                    vehicleBinding.itemRouteVehicleTv.text = destination

                    binding.routeVehicleLl.addView(vehicleBinding.root)
                }

                // 상단 바(Brief) 뷰 추가 (Weight 적용)
                val weight = RouteCalculator.calculateWeight(data.duration)
                val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
                binding.routeBriefLl.addView(briefBinding.root, params)
            }

            // 클릭 리스너 연결
            binding.root.setOnClickListener { onItemClick(item) }
            binding.routeDetailSelectBtn.setOnClickListener { onSelectClick(item) }
        }

        }
    }

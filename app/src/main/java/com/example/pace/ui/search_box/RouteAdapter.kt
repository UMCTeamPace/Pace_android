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
import com.example.pace.data.model.RouteResponse
import com.example.pace.databinding.ItemRouteBinding
import com.example.pace.databinding.ItemRouteDetailArrivalBinding
import com.example.pace.databinding.ItemRouteDetailBriefBinding
import com.example.pace.databinding.ItemRouteVehicleBinding

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
            // 기본 정보 세팅
            binding.routeTotalTv.text = "총 ${item.totalTime / 60}분 소요"

            // 시간 자르기 (2026-02-03T09:00:00 -> 09:00)
            val startTime = item.departureTime.split("T").last().take(5)
            val endTime = item.arrivalTime.split("T").last().take(5)
            binding.routeDepartureTimeTv.text = startTime
            binding.routeArrivalTimeTv.text = endTime

            var index = 0
            // 동적으로 데이터 가져오기
            item.routeDetailInfoResDTOList.forEach { data ->
                val briefBinding = ItemRouteDetailBriefBinding.inflate(LayoutInflater.from(context), binding.routeBriefLl, false)
                val vehicleBinding = ItemRouteVehicleBinding.inflate(LayoutInflater.from(context), binding.routeVehicleLl, false)

                when(data.transitDetail){
                    // 걷기
                    null -> {
                        if(data.sequence == 1){
                            briefBinding.itemRouteDetailBriefIv.setImageResource(R.drawable.ic_people)
                        }else{
                            briefBinding.itemRouteDetailBriefIv.visibility = View.GONE
                            briefBinding.itemRouteDetailBriefTv.updatePadding(0)
                        }
                        briefBinding.itemRouteDetailBriefTv.text = "${data.duration / 60}분"
                        briefBinding.itemRouteDetailBriefTv.setTextColor(context.resources.getColor(R.color.gray_600))

                        if(index == item.routeDetailInfoResDTOList.size - 1){
                            vehicleBinding.itemRouteVehicleIv.setImageResource(R.drawable.ic_route_item_arrival_icon)
                            vehicleBinding.itemRouteVehicleLineTv.text = "하차"
                            vehicleBinding.itemRouteVehicleLineTv.setTextColor(context.resources.getColor(R.color.black))
                            vehicleBinding.itemRouteVehicleView.visibility = View.GONE

                            binding.routeVehicleLl.addView(vehicleBinding.root)
                        }
                    }
                    // 대중교통
                    else -> {
                        // 아이콘 변경
                        val layoutDrawable = ContextCompat.getDrawable(context, R.drawable.ic_route_detail)?.mutate() as LayerDrawable
                        val iconColor = layoutDrawable.findDrawableByLayerId(R.id.ic_route_detail_color).mutate() as GradientDrawable
                        val bgColor = briefBinding.itemRouteDetailBriefTv.background.mutate() as GradientDrawable
                        val lineColor = Color.parseColor(data.transitDetail.lineColor)

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
                        vehicleBinding.itemRouteVehicleIv.setImageDrawable(layoutDrawable)
                        //vehicleBinding.itemRouteVehicleLineTv.text = data.transitDetail.shortName
                        vehicleBinding.itemRouteVehicleLineTv.setTextColor(lineColor)
                        vehicleBinding.itemRouteVehicleTv.text = data.transitDetail.departureStop

                        if(vehicleBinding.root.parent != null){
                            (vehicleBinding.root.parent as ViewGroup).removeView(vehicleBinding.root)
                        }
                        binding.routeVehicleLl.addView(vehicleBinding.root)
                    }
                }

                if(briefBinding.root.parent != null){
                    (briefBinding.root.parent as ViewGroup).removeView(briefBinding.root)
                }
                val weight = when{
                    data.duration <= 180 -> 1.0f
                    else -> 1.0f + (data.duration + 200) / 400f
                }
                val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
                binding.routeBriefLl.addView(briefBinding.root, params)

                index++
                Log.d("check/condition", (index == item.routeDetailInfoResDTOList.size -1).toString() )
            }


            binding.root.setOnClickListener { onItemClick(item) }
            binding.routeDetailSelectBtn.setOnClickListener {
                onSelectClick(item)
            }
        }
    }
}
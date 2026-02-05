package com.example.pace.ui.search_box

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.pace.data.db.RouteResponse
import com.example.pace.databinding.ItemRouteBinding

class RouteAdapter(
    private val items: List<RouteResponse>,
    private val onItemClick: (RouteResponse) -> Unit
) : RecyclerView.Adapter<RouteAdapter.RouteViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RouteViewHolder {
        val binding = ItemRouteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RouteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RouteViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class RouteViewHolder(private val binding: ItemRouteBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: RouteResponse) {
            // 1. 기본 정보 세팅
            binding.routeTotalTv.text = "총 ${item.totalTime / 60}분 소요"

            // 시간 자르기 (2026-02-03T09:00:00 -> 09:00)
            val startTime = item.departureTime.split("T").last().take(5)
            val endTime = item.arrivalTime.split("T").last().take(5)
            binding.routeDepartureTimeTv.text = startTime
            binding.routeArrivalTimeTv.text = endTime


            binding.root.setOnClickListener { onItemClick(item) }
        }
    }
}
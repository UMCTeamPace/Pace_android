package com.example.pace.ui.search_box

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.pace.R
import com.example.pace.databinding.ItemRouteDetailStationBinding

class RouteDetailStationAdapter(
    private val context: Context,
    private val stations: List<String>,
    private val lineColor: Int,
): RecyclerView.Adapter<RouteDetailStationAdapter.ViewHolder>() {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val binding = ItemRouteDetailStationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {
        holder.bind(stations[position])
    }

    override fun getItemCount(): Int = stations.size

    inner class ViewHolder(val binding: ItemRouteDetailStationBinding):RecyclerView.ViewHolder(binding.root){
        fun bind(station: String){
            binding.itemRouteDetailStationTv.text = station
            val icon = ContextCompat.getDrawable(context, R.drawable.ic_item_route_detail_station)?.mutate()
            if(icon is GradientDrawable){
                icon.setColor(lineColor)
            }
            binding.itemRouteDetailStationIv.setImageDrawable(icon)
        }
    }
}
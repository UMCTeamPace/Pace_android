package com.example.pace.ui.search_box

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.pace.data.model.RecentRoute
import com.example.pace.databinding.ItemRecentRouteBinding

class RecentRouteAdapter(
    private val onItemClick: (RecentRoute) -> Unit,
    private val onDeleteClick: (RecentRoute) -> Unit
) : ListAdapter<RecentRoute, RecentRouteAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecentRouteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemRecentRouteBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: RecentRoute) {
            binding.tvRouteStartText.text = item.startPlaceName
            binding.tvRouteEndText.text = item.endPlaceName

            binding.root.setOnClickListener { onItemClick(item) }
//            binding.ivDelete.setOnClickListener { onDeleteClick(item) }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<RecentRoute>() {
            override fun areItemsTheSame(oldItem: RecentRoute, newItem: RecentRoute): Boolean {
                return oldItem.startPlaceId == newItem.startPlaceId &&
                        oldItem.endPlaceId == newItem.endPlaceId
            }
            override fun areContentsTheSame(oldItem: RecentRoute, newItem: RecentRoute) = oldItem == newItem
        }
    }
}
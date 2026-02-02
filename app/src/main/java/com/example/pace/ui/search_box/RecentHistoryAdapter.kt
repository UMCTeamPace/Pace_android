package com.example.pace.ui.search_box

import com.example.pace.R
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.pace.data.model.RecentHistoryItem
import com.example.pace.databinding.ItemRecentHistoryBinding

class RecentHistoryAdapter(
    private val onItemClick: (RecentHistoryItem) -> Unit,
    private val onDeleteClick: (RecentHistoryItem) -> Unit
) : ListAdapter<RecentHistoryItem, RecentHistoryAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecentHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemRecentHistoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: RecentHistoryItem) {
            binding.tvHistoryText.text = item.mainText

            val iconRes = if (item.type == RecentHistoryItem.TYPE_SEARCH_TEXT) {
                R.drawable.ic_history_search// 시계 아이콘
            } else {
                R.drawable.ic_history_place // 위치 아이콘
            }
            binding.ivHistoryIcon.setImageResource(iconRes)

            binding.root.setOnClickListener { onItemClick(item) }
        }
    }

    companion object {
        private val DiffCallback = object : DiffUtil.ItemCallback<RecentHistoryItem>() {
            override fun areItemsTheSame(oldItem: RecentHistoryItem, newItem: RecentHistoryItem): Boolean {
                return (oldItem.mainText == newItem.mainText) && (oldItem.timestamp == newItem.timestamp)
            }
            override fun areContentsTheSame(oldItem: RecentHistoryItem, newItem: RecentHistoryItem) = oldItem == newItem
        }
    }
}
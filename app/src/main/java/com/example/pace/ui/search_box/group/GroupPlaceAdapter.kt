package com.example.pace.ui.search_box.group

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.pace.data.model.response.SavePlaceResponse
import com.example.pace.databinding.ItemRecentHistoryBinding

class GroupPlaceAdapter(
    initialItems: List<SavePlaceResponse>,
    private val onItemClick: (SavePlaceResponse) -> Unit
) : RecyclerView.Adapter<GroupPlaceAdapter.ViewHolder>() {
    private val items = initialItems.toMutableList()

    inner class ViewHolder(private val binding: ItemRecentHistoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: SavePlaceResponse) {
            binding.tvHistoryText.text = item.placeName

            binding.viewForeground.setOnClickListener {
                onItemClick(item)
            }

            binding.viewForeground.translationX = 0f
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecentHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<SavePlaceResponse>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}
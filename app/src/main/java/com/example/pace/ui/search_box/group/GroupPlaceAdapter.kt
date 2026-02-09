package com.example.pace.ui.search_box.group

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.pace.data.model.response.SavePlaceResponse
import com.example.pace.databinding.ItemRecentHistoryBinding

class GroupPlaceAdapter(
    private val items: List<SavePlaceResponse>,
    private val onItemClick: (SavePlaceResponse) -> Unit
) : RecyclerView.Adapter<GroupPlaceAdapter.ViewHolder>() {

    inner class ViewHolder(private val binding: ItemRecentHistoryBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: SavePlaceResponse) {
            binding.tvHistoryText.text = item.placeName

//            binding.ivHistoryIcon.setImageResource(com.example.pace.R.drawable.ic_history_place)

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
}
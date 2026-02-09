package com.example.pace.ui.search_box.group

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.pace.data.model.response.SavePlaceResponse
import com.example.pace.databinding.ItemGroupEditPlaceBinding

class GroupEditPlaceAdapter(
    private val places: List<SavePlaceResponse>,
    private val onSelectionChanged: (Int) -> Unit
) : RecyclerView.Adapter<GroupEditPlaceAdapter.ViewHolder>() {

    // 선택된 아이템의 포지션을 관리하는 Set
    val selectedPositions = mutableSetOf<Int>()

    inner class ViewHolder(val binding: ItemGroupEditPlaceBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemGroupEditPlaceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = places[position]

        holder.binding.tvPlaceName.text = item.placeName

        val isSelected = selectedPositions.contains(position)
        holder.binding.ivCbSelect.isSelected = isSelected
        holder.binding.root.isSelected = isSelected

        holder.binding.root.setOnClickListener {
            if (selectedPositions.contains(position)) {
                selectedPositions.remove(position)
            } else {
                selectedPositions.add(position)
            }
            notifyItemChanged(position)
            onSelectionChanged(selectedPositions.size)
        }
    }

    override fun getItemCount(): Int = places.size

    fun toggleAllSelection(isAllSelected: Boolean) {
        selectedPositions.clear()
        if (isAllSelected) {
            for (i in places.indices) selectedPositions.add(i)
        }
        notifyDataSetChanged()
        onSelectionChanged(selectedPositions.size)
    }
}
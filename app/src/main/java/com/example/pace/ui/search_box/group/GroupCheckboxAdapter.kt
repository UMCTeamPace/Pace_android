package com.example.pace.ui.search_box.group

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.pace.data.model.response.GroupItem
import com.example.pace.databinding.ItemBookmarkGroupAddBinding
import com.example.pace.databinding.ItemGroupCheckboxBinding

class GroupCheckboxAdapter(
    private var items: List<GroupItem>,
    private val onAddClick: () -> Unit,
    private val onSelectionChanged: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val selectedGroupIds = mutableSetOf<Long>()
    private var isInteractionEnabled = false

    companion object {
        private const val TYPE_ITEM = 0
        private const val TYPE_FOOTER = 1
    }

    override fun getItemCount() = items.size + 1

    override fun getItemViewType(position: Int): Int {
        return if (position == items.size) TYPE_FOOTER else TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_ITEM) {
            val binding = ItemGroupCheckboxBinding.inflate(inflater, parent, false)
            ItemViewHolder(binding)
        } else {
            val binding = ItemBookmarkGroupAddBinding.inflate(inflater, parent, false)
            FooterViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ItemViewHolder) {
            holder.bind(items[position])
        } else if (holder is FooterViewHolder) {
            holder.bind()
        }
    }

    inner class ItemViewHolder(private val binding: ItemGroupCheckboxBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: GroupItem) {
            binding.tvGroupName.text = item.groupName

            try {
                val themeColor = Color.parseColor(item.groupColor)
                binding.ivGroupIconLine.imageTintList = ColorStateList.valueOf(themeColor)
            } catch (e: Exception) {
            }

            binding.ivSelect.isSelected = selectedGroupIds.contains(item.groupId)
            binding.root.isEnabled = isInteractionEnabled
            binding.layoutGroupRow.isEnabled = isInteractionEnabled
            binding.ivSelect.isEnabled = isInteractionEnabled

            val listener = listener@{
                if (!isInteractionEnabled) return@listener
                val currentPos = bindingAdapterPosition
                if (currentPos == RecyclerView.NO_POSITION) return@listener

                if (selectedGroupIds.contains(item.groupId)) {
                    selectedGroupIds.remove(item.groupId)
                } else {
                    selectedGroupIds.add(item.groupId)
                }
                notifyItemChanged(currentPos)
                onSelectionChanged()
            }

            binding.root.setOnClickListener { listener() }
            binding.layoutGroupRow.setOnClickListener { listener() }
            binding.ivSelect.setOnClickListener { listener() }
        }
    }

    inner class FooterViewHolder(private val binding: ItemBookmarkGroupAddBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind() {
            binding.root.isEnabled = isInteractionEnabled
            binding.root.setOnClickListener {
                if (!isInteractionEnabled) return@setOnClickListener
                onAddClick()
            }
        }
    }

    fun updateItems(newItems: List<GroupItem>) {
        val availableIds = newItems.map { it.groupId }.toSet()
        selectedGroupIds.retainAll(availableIds)
        items = newItems
        notifyDataSetChanged()
        onSelectionChanged()
    }

    fun getSelectedGroupIds(): List<Long> = selectedGroupIds.toList()

    fun setSelectedGroupIds(groupIds: Set<Long>) {
        selectedGroupIds.clear()
        selectedGroupIds.addAll(groupIds)
        notifyDataSetChanged()
        onSelectionChanged()
    }

    fun setInteractionEnabled(enabled: Boolean) {
        if (isInteractionEnabled == enabled) return
        isInteractionEnabled = enabled
        notifyDataSetChanged()
    }
}

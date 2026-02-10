package com.example.pace.ui.search_box.group

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.pace.data.model.response.GroupItem
import com.example.pace.databinding.ItemBookmarkGroupAddBinding
import com.example.pace.databinding.ItemGroupRadioBinding

class GroupRadioAdapter(
    private var items: List<GroupItem>,
    private val onAddClick: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>(){
    private var selectedPosition = -1

    companion object {
        private const val TYPE_ITEM = 0
        private const val TYPE_FOOTER = 1
    }

    // 1. 아이템 개수 + 1 (푸터 포함)
    override fun getItemCount() = items.size + 1

    // 2. 위치에 따라 뷰 타입 결정
    override fun getItemViewType(position: Int): Int {
        return if (position == items.size) TYPE_FOOTER else TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_ITEM) {
            val binding = ItemGroupRadioBinding.inflate(inflater, parent, false)
            ItemViewHolder(binding)
        } else {
            val binding = ItemBookmarkGroupAddBinding.inflate(inflater, parent, false)
            FooterViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is ItemViewHolder) {
            holder.bind(items[position], position)
        } else if (holder is FooterViewHolder) {
            holder.bind()
        }
    }

    inner class ItemViewHolder(private val binding: ItemGroupRadioBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: GroupItem, position: Int) {
            binding.tvGroupName.text = item.groupName

            try {
                val themeColor = Color.parseColor(item.groupColor)
                binding.ivGroupIconLine.imageTintList = ColorStateList.valueOf(themeColor)
            } catch (e: Exception) { }

            binding.rbSelect.isChecked = (position == selectedPosition)

            val clickListener = {
                if (selectedPosition != position) {
                    val prevPos = selectedPosition
                    selectedPosition = position
                    notifyItemChanged(prevPos)
                    notifyItemChanged(selectedPosition)
                }
            }
            binding.root.setOnClickListener { clickListener() }
            binding.rbSelect.setOnClickListener { clickListener() }
        }
    }

    inner class FooterViewHolder(private val binding: ItemBookmarkGroupAddBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind() {
            binding.root.setOnClickListener {
                onAddClick() // 콜백 실행
            }
        }
    }
    fun updateItems(newItems: List<GroupItem>) {
        this.items = newItems
        notifyDataSetChanged()
    }

    // 선택된 그룹 ID 반환
    fun getSelectedGroupId(): Long {
        return if (selectedPosition != -1) items[selectedPosition].groupId else -1L
    }
}
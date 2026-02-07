package com.example.pace.ui.search_box

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.pace.data.model.response.GroupItem
import com.example.pace.databinding.ItemBookmarkGroupAddBinding
import com.example.pace.databinding.ItemBookmarkGroupBinding

class BookmarkGroupAdapter(
    private val onGroupClick: (GroupItem) -> Unit,
    private val onAddClick: () -> Unit
) : ListAdapter<GroupItem, RecyclerView.ViewHolder>(DiffCallback) {

    companion object {
        private const val TYPE_ITEM = 0
        private const val TYPE_ADD_BUTTON = 1

        private val DiffCallback = object : DiffUtil.ItemCallback<GroupItem>() {
            override fun areItemsTheSame(oldItem: GroupItem, newItem: GroupItem) = oldItem.groupId == newItem.groupId
            override fun areContentsTheSame(oldItem: GroupItem, newItem: GroupItem) = oldItem == newItem
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == itemCount - 1) TYPE_ADD_BUTTON else TYPE_ITEM
    }

    override fun getItemCount(): Int {
        return super.getItemCount() + 1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_ITEM) {
            val binding = ItemBookmarkGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            GroupViewHolder(binding)
        } else {
            val binding = ItemBookmarkGroupAddBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            AddViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is GroupViewHolder) {
            holder.bind(getItem(position))
        } else if (holder is AddViewHolder) {
            holder.bind()
        }
    }

    // 그룹 아이템 홀더
    inner class GroupViewHolder(private val binding: ItemBookmarkGroupBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: GroupItem) {
            binding.tvGroupName.text = item.groupName
            binding.tvPlaceCount.text = item.placeCount.toString()

            binding.root.setOnClickListener { onGroupClick(item) }
        }
    }

    // 새 그룹 추가 버튼 홀더
    inner class AddViewHolder(private val binding: ItemBookmarkGroupAddBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind() {
            binding.root.setOnClickListener { onAddClick() }
        }
    }
}
package com.example.pace.ui.search_box

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.pace.R
import com.example.pace.data.model.response.GroupItem
import com.example.pace.databinding.ItemBookmarkGroupAddBinding
import com.example.pace.databinding.ItemBookmarkGroupBinding

class BookmarkGroupAdapter(
    private val onGroupClick: (GroupItem) -> Unit,
    private val onEditClick: (GroupItem) -> Unit,
    private val onDeleteClick: (GroupItem) -> Unit,
    private val onAddClick: () -> Unit
) : ListAdapter<GroupItem, RecyclerView.ViewHolder>(DiffCallback) {

    private var touchHelper: CommonSwipeTouchHelper? = null

    fun setHelper(helper: CommonSwipeTouchHelper) {
        this.touchHelper = helper
    }

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
    inner class GroupViewHolder(private val binding: ItemBookmarkGroupBinding)
        : RecyclerView.ViewHolder(binding.root), SwipeableViewHolder {

        override fun setSwiped(isSwiped: Boolean) {
            // 필요 시 배경색 변경 등 처리
        }

        fun bind(item: GroupItem) {
            binding.tvGroupName.text = item.groupName
            binding.tvPlaceCount.text = item.placeCount.toString()

            try {
                val themeColor = Color.parseColor(item.groupColor)
                binding.ivGroupIcon.imageTintList = ColorStateList.valueOf(themeColor)
                // todo 특정 path 만 수정하도록 이미지 관리.
            } catch (e: Exception) { }

            binding.viewForeground.setOnClickListener {
                if (touchHelper?.hasSwipedItem() == true) {
                    touchHelper?.closeSwipedMenu()
                } else {
                    onGroupClick(item)
                }
            }

            binding.viewForeground.translationX = 0f
            binding.viewForeground.translationX = 0f

            // 2. 수정 버튼 클릭
            binding.btnEdit.setOnClickListener {
                touchHelper?.closeSwipedMenu()
                onEditClick(item)
            }

            // 3. 삭제 버튼 클릭
            binding.btnDelete.setOnClickListener {
                touchHelper?.closeSwipedMenu()
                onDeleteClick(item)
            }
        }
    }

    // 새 그룹 추가 버튼 홀더
    inner class AddViewHolder(private val binding: ItemBookmarkGroupAddBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind() {
            binding.root.setOnClickListener { onAddClick() }
        }
    }
}
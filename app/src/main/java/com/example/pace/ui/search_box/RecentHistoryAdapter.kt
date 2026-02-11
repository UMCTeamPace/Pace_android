package com.example.pace.ui.search_box

import com.example.pace.R
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.pace.data.model.RecentHistoryItem
import com.example.pace.databinding.ItemRecentHistoryBinding

class RecentHistoryAdapter(
    private val onItemClick: (RecentHistoryItem) -> Unit,
    private val onDeleteClick: (RecentHistoryItem) -> Unit
) : ListAdapter<RecentHistoryItem, RecentHistoryAdapter.ViewHolder>(DiffCallback) {

    private var touchHelper: CommonSwipeTouchHelper? = null

    fun setHelper(helper: CommonSwipeTouchHelper) {
        this.touchHelper = helper
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecentHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemRecentHistoryBinding)
        : RecyclerView.ViewHolder(binding.root), SwipeableViewHolder {
        override fun getSwipeView(): ConstraintLayout = binding.viewForeground
        override fun setSwiped(isSwiped: Boolean) {
            // 필요하면 여기에 배경색 변경 등 추가 로직 작성
        }

        fun bind(item: RecentHistoryItem) {
            binding.tvHistoryText.text = item.mainText

            val iconRes = if (item.type == RecentHistoryItem.TYPE_SEARCH_TEXT) {
                R.drawable.ic_history_search// 시계 아이콘
            } else {
                R.drawable.ic_history_place // 위치 아이콘
            }
            binding.ivHistoryIcon.setImageResource(iconRes)

            binding.viewForeground.translationX = 0f

            binding.viewForeground.setOnClickListener {
                val recyclerView = itemView.parent as? RecyclerView ?: return@setOnClickListener

                // [리팩토링] 메뉴가 열려있다면 닫고, 닫혀있다면 클릭 이벤트 수행
                if (touchHelper?.isAnyMenuOpened(recyclerView) == true) {
                    touchHelper?.closeAllMenus(recyclerView)
                } else {
                    onItemClick(item)
                }
            }

            binding.ivDelete.setOnClickListener {
                val recyclerView = itemView.parent as? RecyclerView ?: return@setOnClickListener

                touchHelper?.closeAllMenus(recyclerView)
                onDeleteClick(item)
            }
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
package com.example.pace.ui.search_box

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.pace.data.model.RecentRoute
import com.example.pace.databinding.ItemRecentRouteBinding

class RecentRouteAdapter(
    private val onItemClick: (RecentRoute) -> Unit,
    private val onDeleteClick: (RecentRoute) -> Unit
) : ListAdapter<RecentRoute, RecentRouteAdapter.ViewHolder>(DiffCallback) {
    private var touchHelper: CommonSwipeTouchHelper? = null

    fun setHelper(helper: CommonSwipeTouchHelper) {
        this.touchHelper = helper
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecentRouteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemRecentRouteBinding)
        : RecyclerView.ViewHolder(binding.root), SwipeableViewHolder {
        override fun setSwiped(isSwiped: Boolean) { }
        override fun getSwipeView(): ConstraintLayout = binding.viewForeground

        fun bind(item: RecentRoute) {
            binding.tvRouteStartText.text = item.startPlaceName
            binding.tvRouteEndText.text = item.endPlaceName

            binding.viewForeground.translationX = 0f

            binding.viewForeground.setOnClickListener {
                val recyclerView = itemView.parent as? RecyclerView ?: return@setOnClickListener

                // 1. 만약 스와이프 메뉴가 열려있다면 닫기만 수행
                if (touchHelper?.isAnyMenuOpened(recyclerView) == true) {
                    touchHelper?.closeAllMenus(recyclerView)
                } else {
                    // 2. 닫혀있는 상태라면 아이템 클릭 이벤트 실행
                    onItemClick(item)
                }
            }

            binding.ivDelete.setOnClickListener {
                val recyclerView = itemView.parent as? RecyclerView ?: return@setOnClickListener

                // 메뉴를 닫으면서 삭제 실행
                touchHelper?.closeAllMenus(recyclerView)
                onDeleteClick(item)
            }
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
package com.example.pace.ui.search_box

import android.view.LayoutInflater
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.daimajia.swipe.SwipeLayout
import com.daimajia.swipe.adapters.RecyclerSwipeAdapter
import com.daimajia.swipe.util.Attributes
import com.daimajia.swipe.SimpleSwipeListener
import com.example.pace.R
import com.example.pace.data.model.RecentHistoryItem
import com.example.pace.databinding.ItemRecentHistoryBinding

class RecentHistoryAdapter(
    private val onItemClick: (RecentHistoryItem) -> Unit,
    private val onDeleteClick: (RecentHistoryItem) -> Unit,
    private val onSwipeStart: () -> Unit = {}
) : RecyclerSwipeAdapter<RecentHistoryAdapter.ViewHolder>() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var items: List<RecentHistoryItem> = emptyList()

    fun submitList(newItems: List<RecentHistoryItem>) {
        val diffResult = DiffUtil.calculateDiff(
            object : DiffUtil.Callback() {
                override fun getOldListSize(): Int = items.size

                override fun getNewListSize(): Int = newItems.size

                override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                    val oldItem = items[oldItemPosition]
                    val newItem = newItems[newItemPosition]
                    return oldItem.mainText == newItem.mainText &&
                        oldItem.timestamp == newItem.timestamp
                }

                override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                    return items[oldItemPosition] == newItems[newItemPosition]
                }
            }
        )

        items = newItems.toList()
        diffResult.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecentHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)

        mItemManger.bindView(holder.itemView, position)
        setMode(Attributes.Mode.Single)

        holder.binding.ivDelete.setOnClickListener {
            mItemManger.closeItem(position)
            mainHandler.postDelayed({
                onDeleteClick(item)
            }, 150)
        }

        holder.binding.viewForeground.setOnClickListener {
            if (holder.binding.root.openStatus == SwipeLayout.Status.Close) {
                onItemClick(item)
            } else {
                mItemManger.closeItem(position)
            }
        }
    }

    override fun getItemCount(): Int = items.size

    override fun getSwipeLayoutResourceId(position: Int): Int = R.id.item_recent_history

    inner class ViewHolder(val binding: ItemRecentHistoryBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.showMode = SwipeLayout.ShowMode.LayDown
            binding.root.addDrag(SwipeLayout.DragEdge.Right, binding.rightBottomWrapper)
            binding.root.addSwipeListener(object : SimpleSwipeListener() {
                override fun onStartOpen(layout: SwipeLayout?) {
                    onSwipeStart()
                }
            })
        }

        fun bind(item: RecentHistoryItem) {
            binding.root.close(false)
            binding.tvHistoryText.text = item.mainText
            val iconRes = if (item.type == RecentHistoryItem.TYPE_SEARCH_TEXT) {
                R.drawable.ic_history_search
            } else {
                R.drawable.ic_history_place
            }
            binding.ivHistoryIcon.setImageResource(iconRes)
        }
    }
}

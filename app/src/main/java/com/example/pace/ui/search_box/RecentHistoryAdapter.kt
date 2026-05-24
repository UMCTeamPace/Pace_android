package com.example.pace.ui.search_box

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
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
    private var savedStarColorsByPlaceId: Map<String, String> = emptyMap()

    fun submitList(newItems: List<RecentHistoryItem>) {
        mItemManger.closeAllItems()
        items = newItems.toList()
        notifyDataSetChanged()
    }

    fun updateSavedStarColors(colorsByPlaceId: Map<String, String>) {
        savedStarColorsByPlaceId = colorsByPlaceId
        notifyDataSetChanged()
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
            val currentPosition = holder.bindingAdapterPosition
            if (currentPosition == RecyclerView.NO_POSITION) return@setOnClickListener

            val currentItem = items[currentPosition]
            mItemManger.closeItem(currentPosition)
            mainHandler.postDelayed({
                removeItem(currentItem)
                onDeleteClick(currentItem)
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

    private fun removeItem(item: RecentHistoryItem) {
        items = items.filterNot { it == item }
        notifyDataSetChanged()
    }

    inner class ViewHolder(val binding: ItemRecentHistoryBinding) : RecyclerView.ViewHolder(binding.root) {
        private var suppressSwipeCallback = false

        init {
            binding.root.showMode = SwipeLayout.ShowMode.LayDown
            binding.root.addDrag(SwipeLayout.DragEdge.Right, binding.rightBottomWrapper)
            binding.root.addSwipeListener(object : SimpleSwipeListener() {
                override fun onStartOpen(layout: SwipeLayout?) {
                    if (!suppressSwipeCallback) {
                        onSwipeStart()
                    }
                }
            })
        }

        fun bind(item: RecentHistoryItem) {
            suppressSwipeCallback = true
            binding.root.close(false)
            suppressSwipeCallback = false
            binding.tvHistoryText.text = item.mainText
            bindHistoryIcon(item)
        }

        private fun bindHistoryIcon(item: RecentHistoryItem) {
            binding.ivHistoryIcon.imageTintList = null
            binding.ivSavedStarLine.imageTintList = null
            binding.ivSavedStarBg.visibility = View.GONE
            binding.ivSavedStarLine.visibility = View.GONE
            binding.ivHistoryIcon.visibility = View.VISIBLE

            if (item.type == RecentHistoryItem.TYPE_SEARCH_TEXT) {
                binding.ivHistoryIcon.setImageResource(R.drawable.ic_history_search)
                return
            }

            val placeId = item.placeEntity?.placeId
            val groupColor = placeId?.let { savedStarColorsByPlaceId[it] }
            if (groupColor.isNullOrBlank()) {
                binding.ivHistoryIcon.setImageResource(R.drawable.ic_history_place)
                return
            }

            try {
                binding.ivHistoryIcon.visibility = View.GONE
                binding.ivSavedStarBg.visibility = View.VISIBLE
                binding.ivSavedStarLine.visibility = View.VISIBLE
                binding.ivSavedStarLine.imageTintList = ColorStateList.valueOf(Color.parseColor(groupColor))
            } catch (e: Exception) {
                binding.ivSavedStarBg.visibility = View.GONE
                binding.ivSavedStarLine.visibility = View.GONE
                binding.ivSavedStarLine.imageTintList = null
                binding.ivHistoryIcon.visibility = View.VISIBLE
                binding.ivHistoryIcon.setImageResource(R.drawable.ic_history_place)
            }
        }
    }
}

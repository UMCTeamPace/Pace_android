package com.example.pace.ui.search_box

import android.view.LayoutInflater
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.daimajia.swipe.SwipeLayout
import com.daimajia.swipe.SimpleSwipeListener
import com.daimajia.swipe.adapters.RecyclerSwipeAdapter
import com.daimajia.swipe.util.Attributes
import com.example.pace.R
import com.example.pace.data.model.RecentRoute
import com.example.pace.databinding.ItemRecentRouteBinding

data class RecentRouteUiItem(
    val route: RecentRoute,
    val startPlaceName: String,
    val endPlaceName: String
)

class RecentRouteAdapter(
    private val onItemClick: (RecentRouteUiItem) -> Unit,
    private val onDeleteClick: (RecentRoute) -> Unit,
    private val onSwipeStart: () -> Unit = {}
) : RecyclerSwipeAdapter<RecentRouteAdapter.ViewHolder>() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var items: List<RecentRouteUiItem> = emptyList()

    fun submitList(newItems: List<RecentRouteUiItem>) {
        mItemManger.closeAllItems()
        items = newItems.toList()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecentRouteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
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
                onDeleteClick(item.route)
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

    override fun getSwipeLayoutResourceId(position: Int): Int = R.id.item_recent_route

    inner class ViewHolder(val binding: ItemRecentRouteBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.showMode = SwipeLayout.ShowMode.LayDown
            binding.root.addDrag(SwipeLayout.DragEdge.Right, binding.rightBottomWrapper)
            binding.root.addSwipeListener(object : SimpleSwipeListener() {
                override fun onStartOpen(layout: SwipeLayout?) {
                    onSwipeStart()
                }
            })
        }

        fun bind(item: RecentRouteUiItem) {
            binding.root.close(false)
            binding.tvRouteStartText.text = item.startPlaceName
            binding.tvRouteEndText.text = item.endPlaceName
        }
    }
}

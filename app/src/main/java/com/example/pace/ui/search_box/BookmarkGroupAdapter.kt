package com.example.pace.ui.search_box

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.daimajia.swipe.SwipeLayout
import com.daimajia.swipe.SimpleSwipeListener
import com.daimajia.swipe.adapters.RecyclerSwipeAdapter
import com.daimajia.swipe.util.Attributes
import com.example.pace.R
import com.example.pace.data.model.response.GroupItem
import com.example.pace.databinding.ItemBookmarkGroupAddBinding
import com.example.pace.databinding.ItemBookmarkGroupBinding

class BookmarkGroupAdapter(
    private val onGroupClick: (GroupItem) -> Unit,
    private val onEditClick: (GroupItem) -> Unit,
    private val onDeleteClick: (GroupItem) -> Unit,
    private val onAddClick: () -> Unit,
    private val onSwipeStart: () -> Unit = {}
) : RecyclerSwipeAdapter<RecyclerView.ViewHolder>() {

    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val TYPE_ITEM = 0
        private const val TYPE_ADD_BUTTON = 1
    }

    private var items: List<GroupItem> = emptyList()

    fun submitList(newItems: List<GroupItem>) {
        val diffResult = DiffUtil.calculateDiff(
            object : DiffUtil.Callback() {
                override fun getOldListSize(): Int = items.size + 1

                override fun getNewListSize(): Int = newItems.size + 1

                override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                    if (oldItemPosition == items.size && newItemPosition == newItems.size) return true
                    if (oldItemPosition == items.size || newItemPosition == newItems.size) return false
                    return items[oldItemPosition].groupId == newItems[newItemPosition].groupId
                }

                override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                    if (oldItemPosition == items.size && newItemPosition == newItems.size) return true
                    if (oldItemPosition == items.size || newItemPosition == newItems.size) return false
                    return items[oldItemPosition] == newItems[newItemPosition]
                }
            }
        )

        items = newItems.toList()
        diffResult.dispatchUpdatesTo(this)
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == items.size) TYPE_ADD_BUTTON else TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_ITEM) {
            GroupViewHolder(ItemBookmarkGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        } else {
            AddViewHolder(ItemBookmarkGroupAddBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is GroupViewHolder) {
            val item = items[position]
            holder.bind(item)

            mItemManger.bindView(holder.itemView, position)
            setMode(Attributes.Mode.Single)

            holder.binding.btnEdit.setOnClickListener {
                mItemManger.closeItem(position)
                mainHandler.postDelayed({
                    onEditClick(item)
                }, 150)
            }

            holder.binding.btnDelete.setOnClickListener {
                mItemManger.closeItem(position)
                mainHandler.postDelayed({
                    onDeleteClick(item)
                }, 150)
            }

            holder.binding.viewForeground.setOnClickListener {
                if (holder.binding.root.openStatus == SwipeLayout.Status.Close) {
                    onGroupClick(item)
                } else {
                    mItemManger.closeItem(position)
                }
            }
        } else if (holder is AddViewHolder) {
            holder.bind()
        }
    }

    override fun getItemCount(): Int = items.size + 1

    override fun getSwipeLayoutResourceId(position: Int): Int {
        return if (getItemViewType(position) == TYPE_ITEM) R.id.item_bookmark_group else 0
    }

    inner class GroupViewHolder(val binding: ItemBookmarkGroupBinding) :
        RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.showMode = SwipeLayout.ShowMode.LayDown
            binding.root.addDrag(SwipeLayout.DragEdge.Right, binding.rightBottomWrapper)
            binding.root.addSwipeListener(object : SimpleSwipeListener() {
                override fun onStartOpen(layout: SwipeLayout?) {
                    onSwipeStart()
                }
            })
        }

        fun bind(item: GroupItem) {
            binding.root.close(false)
            binding.tvGroupName.text = item.groupName
            binding.tvPlaceCount.text = item.placeCount.toString()

            try {
                binding.ivGroupIconLine.imageTintList = ColorStateList.valueOf(Color.parseColor(item.groupColor))
            } catch (_: Exception) {
            }
        }
    }

    inner class AddViewHolder(private val binding: ItemBookmarkGroupAddBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind() {
            binding.root.setOnClickListener { onAddClick() }
        }
    }
}

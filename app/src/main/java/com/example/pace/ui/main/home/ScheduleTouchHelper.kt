package com.example.pace.ui.main.home

import android.content.ClipData
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

interface MySwipeListener{
    fun onScheduleSwiped(position: Int, direction: Int)
}
class ScheduleTouchHelper(
    private val listener: MySwipeListener
): ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
    // 어떤 방향 및 상태에서 허용할 것인가
    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        val swipeFlags = ItemTouchHelper.RIGHT or ItemTouchHelper.LEFT
        return makeMovementFlags(0, swipeFlags)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        return false
    }

    override fun onSwiped(
        viewHolder: RecyclerView.ViewHolder,
        direction: Int
    ) {
        val position = viewHolder.bindingAdapterPosition
        listener.onScheduleSwiped(position, direction)
    }
}
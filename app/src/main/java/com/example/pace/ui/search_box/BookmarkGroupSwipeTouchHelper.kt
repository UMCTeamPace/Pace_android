package com.example.pace.ui.search_box

import android.content.res.Resources
import android.graphics.Canvas
import android.util.DisplayMetrics
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.max

class BookmarkGroupSwipeTouchHelper(
    private val adapter: RecyclerView.Adapter<*>,
    private val clampWidthDp: Int = 120
) : ItemTouchHelper.Callback() {

    private val clampWidth = dpToPx(clampWidthDp)
    private var currentScrollX = 0f

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        if (viewHolder !is SwipeableViewHolder) return makeMovementFlags(0, 0)

        val isOtherSwiped = (0 until recyclerView.childCount).any { i ->
            val child = recyclerView.getChildAt(i)
            val childViewHolder = recyclerView.getChildViewHolder(child)
            if (childViewHolder != viewHolder && childViewHolder is SwipeableViewHolder) {
                childViewHolder.getSwipeView().translationX != 0f
            } else {
                false
            }
        }

        if (isOtherSwiped) return makeMovementFlags(0, 0)

        return makeMovementFlags(0, ItemTouchHelper.LEFT)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        if (actionState != ItemTouchHelper.ACTION_STATE_SWIPE || viewHolder !is SwipeableViewHolder) {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            return
        }

        val swipeView = viewHolder.getSwipeView()

        if (isCurrentlyActive) {
            val translationX = if (dX < 0) max(dX, -clampWidth) else 0f
            currentScrollX = translationX
            swipeView.translationX = translationX
            return
        }

        val position = viewHolder.bindingAdapterPosition
        if (position == RecyclerView.NO_POSITION || position >= adapter.itemCount) {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            return
        }

        val translationX = if (currentScrollX <= -clampWidth / 2) {
            viewHolder.setSwiped(true)
            -clampWidth
        } else {
            viewHolder.setSwiped(false)
            0f
        }
        swipeView.translationX = translationX
    }

    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float = 2f

    fun closeAllMenus(recyclerView: RecyclerView) {
        for (i in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(i)
            val holder = recyclerView.getChildViewHolder(child)
            if (holder is SwipeableViewHolder) {
                val swipeView = holder.getSwipeView()
                if (swipeView.translationX != 0f) {
                    swipeView.animate().translationX(0f).setDuration(200).start()
                    holder.setSwiped(false)
                }
            }
        }
        currentScrollX = 0f
    }

    fun isAnyMenuOpened(recyclerView: RecyclerView): Boolean {
        for (i in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(i)
            val holder = recyclerView.getChildViewHolder(child)
            if (holder is SwipeableViewHolder && holder.getSwipeView().translationX != 0f) {
                return true
            }
        }
        return false
    }

    private fun dpToPx(dp: Int): Float {
        return dp * Resources.getSystem().displayMetrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT
    }
}

package com.example.pace.ui.search_box

import android.content.res.Resources
import android.graphics.Canvas
import android.util.DisplayMetrics
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.pace.R
import kotlin.math.max

class CommonSwipeTouchHelper(
    private val clampWidthDp: Int = 60
): ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT){
    private val deleteButtonWidth = dpToPx(clampWidthDp)
    private var currentScrollX = 0f
    private var currentSwipedViewHolder: RecyclerView.ViewHolder? = null
    private var lastInteractedViewHolder: RecyclerView.ViewHolder? = null

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        if (viewHolder !is SwipeableViewHolder) {
            return makeMovementFlags(0, 0)
        }
        return super.getMovementFlags(recyclerView, viewHolder)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {

    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            val viewForeground = viewHolder.itemView.findViewById<ConstraintLayout>(R.id.view_foreground) ?: return
            var translationX: Float

            if (isCurrentlyActive) {
                lastInteractedViewHolder = viewHolder

                if (currentSwipedViewHolder != null && currentSwipedViewHolder != viewHolder) {
                    closeSwipedMenu()
                }

                // 왼쪽으로만 밀리게 제한
                translationX = if (dX < 0) max(dX, -deleteButtonWidth) else 0f
                currentScrollX = translationX
                viewForeground.translationX = translationX
            } else {
                if (viewHolder == lastInteractedViewHolder) {
                    val isSwipedEnough = currentScrollX < -deleteButtonWidth / 2

                    if (isSwipedEnough) {
                        // 고정 (열림)
                        translationX = -deleteButtonWidth
                        currentSwipedViewHolder = viewHolder
                        if (viewHolder is SwipeableViewHolder) viewHolder.setSwiped(true)
                    } else {
                        // 원위치 (닫힘)
                        translationX = 0f
                        if (currentSwipedViewHolder == viewHolder) {
                            currentSwipedViewHolder = null
                        }
                        if (viewHolder is SwipeableViewHolder) viewHolder.setSwiped(false)
                    }
                    viewForeground.translationX = translationX
                }
            }
        }
    }

    fun closeSwipedMenu() {
        currentSwipedViewHolder?.let { holder ->
            val viewForeground = holder.itemView.findViewById<ConstraintLayout>(R.id.view_foreground)
            viewForeground?.animate()?.translationX(0f)?.setDuration(200)?.start()

            if (holder is SwipeableViewHolder) {
                holder.setSwiped(false)
            }
        }
        currentSwipedViewHolder = null
        currentScrollX = 0f
    }

    fun hasSwipedItem(): Boolean {
        return currentSwipedViewHolder != null
    }

    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float = 0.5f

    private fun dpToPx(dp: Int): Float {
        return dp * Resources.getSystem().displayMetrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT
    }
}
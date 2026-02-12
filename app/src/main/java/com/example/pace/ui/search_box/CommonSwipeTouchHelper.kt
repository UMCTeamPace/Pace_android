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
    private val adapter: RecyclerView.Adapter<*>,
    private val clampWidthDp: Int = 60
): ItemTouchHelper.Callback() {

    private val clampWidth = dpToPx(clampWidthDp)
    private var currentScrollX = 0f

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        // SwipeableViewHolder를 상속받지 않은 홀더는 무시
        if (viewHolder !is SwipeableViewHolder) return makeMovementFlags(0, 0)

        // 1. 다른 아이템이 이미 열려 있는지 확인 (ScheduleTouchHelper 로직)
        val isOtherSwiped = (0 until recyclerView.childCount).any { i ->
            val child = recyclerView.getChildAt(i)
            val childViewHolder = recyclerView.getChildViewHolder(child)
            if (childViewHolder != viewHolder && childViewHolder is SwipeableViewHolder) {
                childViewHolder.getSwipeView().translationX != 0f
            } else false
        }

        // 다른 게 열려있으면 내꺼 스와이프 금지
        if (isOtherSwiped) return makeMovementFlags(0, 0)

        // 왼쪽으로만 밀기 (ItemTouchHelper.LEFT)
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
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && viewHolder is SwipeableViewHolder) {
            val swipeView = viewHolder.getSwipeView()
            var translationX: Float

            if (isCurrentlyActive) {
                // 사용자가 밀고 있는 중
                translationX = if (dX < 0) max(dX, -clampWidth) else 0f
                currentScrollX = translationX
                swipeView.translationX = translationX
            } else {
                // 손을 뗐을 때 고정 로직
                val position = viewHolder.bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION || position >= adapter.itemCount) {
                    super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
                    return
                }

                // 절반 이상 밀었으면 고정, 아니면 원위치
                translationX = if (currentScrollX <= -clampWidth / 2) {
                    viewHolder.setSwiped(true)
                    -clampWidth
                } else {
                    viewHolder.setSwiped(false)
                    0f
                }
                swipeView.translationX = translationX
            }
        }
    }

    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float = 2f

    // 외부에서 메뉴를 닫을 때 사용
    fun closeSwipedMenu(recyclerView: RecyclerView) {
        for (i in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(i)
            val holder = recyclerView.getChildViewHolder(child)
            if (holder is SwipeableViewHolder) {
                holder.getSwipeView().translationX = 0f
                holder.setSwiped(false)
            }
        }
        currentScrollX = 0f
    }

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
package com.example.pace.ui.main.home

import android.content.res.Resources
import android.graphics.Canvas
import android.util.DisplayMetrics
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.pace.R

class ScheduleTouchHelper(
    private val adapter: RecyclerView.Adapter<*>
): ItemTouchHelper.Callback() {
    // 스와이프 범위
    private val leftWidth = dpToPx(70)
    private val rightWidth = dpToPx(120)
    private var currentScrollX = 0f

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        val currentViewTop = viewHolder.itemView.findViewById<ConstraintLayout>(R.id.schedule_view_top)

        // 모든 아이템을 조사해 swipe된 거 확인하기
        val isOtherSwiped = (0 until recyclerView.childCount).any{ i ->
            val child = recyclerView.getChildAt(i)
            val childViewHolder = recyclerView.getChildViewHolder(child)

            if(childViewHolder != viewHolder){
                val viewTop = child.findViewById<ConstraintLayout>(R.id.schedule_view_top)
                viewTop?.let{ it.translationX != 0f} ?: false
            }else{
                false
            }
        }
        // 자신이 아니면 스와이프 X
        if(isOtherSwiped){
            return makeMovementFlags(0, 0)
        }
        // 현재 아이템이 없다면 스와이프 X
        if(currentViewTop == null){
            return makeMovementFlags(0, 0)
        }

        return makeMovementFlags(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT)
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

    }

    // 스와이프 코드 구현
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
            // 상단 뷰 찾기
            val viewTop = viewHolder.itemView.findViewById<ConstraintLayout>(R.id.schedule_view_top)
            var translationX: Float
            when(isCurrentlyActive){
                // 사용자가 스와이프 중일 때
                true -> {
                    when(dX){
                        0f -> {
                            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
                        }
                        else -> {
                            // dX < 0: 좌측, dX >0: 우측 스크롤
                            // 각자 해당하는 너비만큼 스크롤 제한
                            translationX = if(dX < 0){
                                Math.max(dX, -rightWidth)
                            }else{
                                Math.min(dX, leftWidth)
                            }
                            // 현재 위치 저장 및 상단 뷰에 적용
                            currentScrollX = translationX
                            viewTop.translationX = translationX
                        }
                    }
                }
                // 스와이프 중이 아닐 때, 스와이프 고정
                false -> {
                    val position = viewHolder.bindingAdapterPosition
                    if (position == RecyclerView.NO_POSITION || position >= adapter.itemCount) {
                        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
                        return
                    }

                    when(dX){
                        0f -> {
                            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
                        }
                        else -> {
                            // 좌측 스크롤 임계점 도달
                            if(dX < 0 && currentScrollX <= -rightWidth / 2){
                                translationX = -rightWidth
                            }
                            // 우측 스크롤 임계점 도달
                            else if(dX > 0 && currentScrollX == leftWidth){
                                translationX = leftWidth
                            }
                            else{
                                translationX = 0f
                            }
                            // 상단 뷰 가로 위치 고정
                            viewTop.translationX = translationX
                        }
                    }
                }
            }
        }
    }

    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float = 2f

    // 스와이프 메뉴 닫는 함수
    fun closeSwipedMenu(viewHolder: RecyclerView.ViewHolder){
        val viewTop = viewHolder.itemView.findViewById<ConstraintLayout>(R.id.schedule_view_top)
        viewTop?.translationX = 0f
        currentScrollX = 0f
    }

    private fun dpToPx(int: Int): Float {
        return int * Resources.getSystem().displayMetrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT
    }
}

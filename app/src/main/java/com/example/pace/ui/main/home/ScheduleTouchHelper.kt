package com.example.pace.ui.main.home

import android.content.res.Resources
import android.graphics.Canvas
import android.util.DisplayMetrics
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.pace.R

class ScheduleTouchHelper(
    private val adapter: ScheduleRVAdapter
): ItemTouchHelper.Callback() {
    // 스와이프 범위
    private val leftWidth = dpToPx(70)
    private val rightWidth = dpToPx(120)
    private var currentScrollX = 0f
    private var swipedViewHolder: RecyclerView.ViewHolder? = null
    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        if(swipedViewHolder != null && swipedViewHolder != viewHolder){
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
                    val schedule = adapter.getScheduleAt(position)

                    when(dX){
                        0f -> {
                            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
                        }
                        else -> {
                            if(dX < 0 && currentScrollX <= -rightWidth / 2){
                                schedule.isSwiped = true
                                swipedViewHolder = viewHolder
                                translationX = -rightWidth
                            }
                            // 우측 스크롤 및 임계점 도달
                            else if(dX > 0 && currentScrollX == leftWidth){
                                schedule.isSwiped = true
                                swipedViewHolder = viewHolder
                                translationX = leftWidth
                            }
                            else{
                                schedule.isSwiped = false
                                translationX = 0f
                                //swipedPos = -1
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
    fun closeSwipedMenu(){
        val viewTop = swipedViewHolder?.itemView?.findViewById<ConstraintLayout>(R.id.schedule_view_top)
        viewTop?.translationX = 0f
        if (swipedViewHolder != null) {
            val position = swipedViewHolder!!.bindingAdapterPosition
            if (position != RecyclerView.NO_POSITION && position < adapter.itemCount) {
                adapter.getScheduleAt(position).isSwiped = false
            }
        }
        currentScrollX = 0f
        swipedViewHolder = null
    }

    fun hasSwipedItem(): Boolean {
        return swipedViewHolder != null
    }

    private fun dpToPx(int: Int): Float {
        return int * Resources.getSystem().displayMetrics.densityDpi.toFloat() / DisplayMetrics.DENSITY_DEFAULT
    }
}

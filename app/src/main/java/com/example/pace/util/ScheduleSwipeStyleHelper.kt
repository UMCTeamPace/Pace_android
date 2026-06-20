package com.example.pace.util

import android.view.View
import com.daimajia.swipe.SimpleSwipeListener
import com.daimajia.swipe.SwipeLayout
import com.example.pace.R

object ScheduleSwipeStyleHelper {
    fun attach(swipeLayout: SwipeLayout, surfaceView: View) {
        swipeLayout.addSwipeListener(object : SimpleSwipeListener() {
            override fun onUpdate(layout: SwipeLayout?, leftOffset: Int, topOffset: Int) {
                surfaceView.setBackgroundResource(
                    when {
                        leftOffset > 0 -> R.drawable.box_schedule_item_left_square
                        leftOffset < 0 -> R.drawable.box_schedule_item_right_square
                        else -> R.drawable.box_schedule_item
                    }
                )
            }

            override fun onClose(layout: SwipeLayout?) {
                reset(surfaceView)
            }
        })
    }

    fun reset(surfaceView: View) {
        surfaceView.setBackgroundResource(R.drawable.box_schedule_item)
    }
}

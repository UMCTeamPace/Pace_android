package com.example.pace.ui.search_box

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max

class SwipeOnTouchListener(
    private val menuWidth: Float,
    private val onClick: () -> Unit
) : View.OnTouchListener {

    private var initialX = 0f
    private var dx = 0f
    private var isSwiping = false
    private val clickThreshold = 10f

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(view: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = event.rawX
                dx = 0f
                isSwiping = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                dx = event.rawX - initialX

                if (dx < 0 || view.translationX < 0) {
                    val newX = if (dx < 0) max(dx, -menuWidth) else 0f
                    view.translationX = newX
                    if (abs(dx) > clickThreshold) {
                        isSwiping = true
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!isSwiping && abs(dx) < clickThreshold) {
                    if (view.translationX == 0f) {
                        onClick()
                    } else {
                        closeMenu(view)
                    }
                } else {
                    if (view.translationX <= -menuWidth / 2) {
                        openMenu(view)
                    } else {
                        closeMenu(view)
                    }
                }
                isSwiping = false
            }
        }
        return false
    }

    private fun openMenu(view: View) {
        view.animate().translationX(-menuWidth).setDuration(200).start()
    }

    fun closeMenu(view: View) {
        view.animate().translationX(0f).setDuration(200).start()
    }
}
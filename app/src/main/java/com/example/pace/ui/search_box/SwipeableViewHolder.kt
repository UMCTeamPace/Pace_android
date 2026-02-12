package com.example.pace.ui.search_box

import androidx.constraintlayout.widget.ConstraintLayout

interface SwipeableViewHolder {
    fun setSwiped(isSwiped: Boolean)
    fun getSwipeView(): ConstraintLayout
}
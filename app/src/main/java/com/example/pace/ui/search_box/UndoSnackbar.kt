package com.example.pace.ui.search_box

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.view.updateLayoutParams
import com.example.pace.R
import com.google.android.material.snackbar.Snackbar

object UndoSnackbar {
    fun show(anchor: View, message: String, onUndo: () -> Unit) {
        val snackbar = Snackbar.make(anchor, "", Snackbar.LENGTH_LONG).apply {
            duration = 3000
            animationMode = Snackbar.ANIMATION_MODE_SLIDE
        }

        val layout = snackbar.view as ViewGroup
        layout.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        layout.setPadding(0, 0, 0, 0)

        layout.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)?.visibility = View.INVISIBLE
        layout.findViewById<TextView>(com.google.android.material.R.id.snackbar_action)?.visibility = View.GONE

        val customView = LayoutInflater.from(anchor.context)
            .inflate(R.layout.layout_undo_snackbar, layout, false)

        val horizontalMargin = anchor.resources.getDimensionPixelSize(R.dimen.undo_snackbar_horizontal_margin)
        val bottomInset = anchor.resources.getDimensionPixelSize(R.dimen.undo_snackbar_bottom_margin)
        customView.updateLayoutParams<FrameLayout.LayoutParams> {
            width = FrameLayout.LayoutParams.MATCH_PARENT
            height = FrameLayout.LayoutParams.WRAP_CONTENT
            gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
            leftMargin = horizontalMargin
            rightMargin = horizontalMargin
            bottomMargin = bottomInset
        }

        customView.findViewById<TextView>(R.id.tv_message).text = message
        customView.findViewById<TextView>(R.id.btn_undo).setOnClickListener {
            onUndo()
            snackbar.dismiss()
        }
        customView.findViewById<ImageButton>(R.id.btn_close).setOnClickListener {
            snackbar.dismiss()
        }

        layout.removeAllViews()
        layout.addView(customView)
        snackbar.show()
    }
}

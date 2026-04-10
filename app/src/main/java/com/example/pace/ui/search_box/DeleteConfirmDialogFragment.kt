package com.example.pace.ui.search_box

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.example.pace.R

class DeleteConfirmDialogFragment(
    private val message: String,
    private val onConfirm: () -> Unit
) : DialogFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return inflater.inflate(R.layout.dialog_delete_confirm, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvMessage = view.findViewById<TextView>(R.id.tv_delete_dialog_message)
        val btnCancel = view.findViewById<Button>(R.id.btn_delete_cancel)
        val btnConfirm = view.findViewById<Button>(R.id.btn_delete_confirm)

        tvMessage.text = message

        btnCancel.setOnClickListener {
            dismiss()
        }

        btnConfirm.setOnClickListener {
            onConfirm()
            dismiss()
        }
    }

    override fun onResume() {
        super.onResume()
//        val params: ViewGroup.LayoutParams? = dialog?.window?.attributes
//        val deviceWidth = resources.displayMetrics.widthPixels
//        params?.width = (deviceWidth * 0.75).toInt()
//        dialog?.window?.attributes = params as WindowManager.LayoutParams
        val window = dialog?.window
        if (window != null) {
            val params = window.attributes

            val marginPx = (60 * resources.displayMetrics.density).toInt()
            params.width = resources.displayMetrics.widthPixels - marginPx


            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            window.attributes = params

            window.setBackgroundDrawableResource(android.R.color.transparent)
        }
    }
}
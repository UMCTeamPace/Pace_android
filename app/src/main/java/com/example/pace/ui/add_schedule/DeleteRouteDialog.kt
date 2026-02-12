package com.example.pace.ui.add_schedule

import android.R
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import com.example.pace.databinding.DialogDeleteRouteBinding

class DeleteRouteDialog(
    context: Context,
    val deleteRoute: () -> Unit
): Dialog(context) {
    lateinit var binding: DialogDeleteRouteBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogDeleteRouteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.deleteRouteCancelBtn.setOnClickListener {
            dismiss()
        }
        binding.deleteRouteDeleteBtn.setOnClickListener {
            deleteRoute()
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.setDimAmount(0.3f)
        // 크기 지정
        window?.apply {
            val params = attributes
            params.width = (300 * context.resources.displayMetrics.density).toInt()
            params.height = (148 * context.resources.displayMetrics.density).toInt()
            attributes = params
        }
    }
}
package com.example.pace.ui.add_schedule

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import com.example.pace.databinding.DialogRouteScheduleCountLimitBinding

class RouteScheduleCountLimitDialog(
    context: Context,
    private val onConfirm: () -> Unit
) : Dialog(context) {
    private lateinit var binding: DialogRouteScheduleCountLimitBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogRouteScheduleCountLimitBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.routeScheduleCountLimitCancelBtn.setOnClickListener { dismiss() }
        binding.routeScheduleCountLimitConfirmBtn.setOnClickListener {
            dismiss()
            onConfirm()
        }
    }

    override fun onStart() {
        super.onStart()
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.setDimAmount(0.3f)
        window?.apply {
            val params = attributes
            params.width = (300 * context.resources.displayMetrics.density).toInt()
            params.height = (212 * context.resources.displayMetrics.density).toInt()
            attributes = params
        }
    }
}

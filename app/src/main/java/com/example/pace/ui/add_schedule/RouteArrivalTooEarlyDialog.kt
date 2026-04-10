package com.example.pace.ui.add_schedule

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import com.example.pace.databinding.DialogRouteArrivalTooEarlyBinding

class RouteArrivalTooEarlyDialog(
    context: Context,
    private val onRetry: () -> Unit
) : Dialog(context) {
    private lateinit var binding: DialogRouteArrivalTooEarlyBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogRouteArrivalTooEarlyBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.routeArrivalTooEarlyCancelBtn.setOnClickListener { dismiss() }
        binding.routeArrivalTooEarlyRetryBtn.setOnClickListener {
            dismiss()
            onRetry()
        }
    }

    override fun onStart() {
        super.onStart()
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.setDimAmount(0.3f)
        window?.apply {
            val params = attributes
            params.width = (300 * context.resources.displayMetrics.density).toInt()
            params.height = (196 * context.resources.displayMetrics.density).toInt()
            attributes = params
        }
    }
}

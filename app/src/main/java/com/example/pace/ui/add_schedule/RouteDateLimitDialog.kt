package com.example.pace.ui.add_schedule

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import com.example.pace.databinding.DialogRouteDateLimitBinding

class RouteDateLimitDialog(
    context: Context
) : Dialog(context) {
    private lateinit var binding: DialogRouteDateLimitBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogRouteDateLimitBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.routeDateLimitConfirmBtn.setOnClickListener { dismiss() }
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

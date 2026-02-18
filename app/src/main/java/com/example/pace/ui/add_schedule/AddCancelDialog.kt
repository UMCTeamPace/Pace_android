package com.example.pace.ui.add_schedule

import android.R
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import com.example.pace.databinding.DialogAddCancelBinding
import com.example.pace.databinding.DialogDeleteRouteBinding

class AddCancelDialog(
    context: Context,
    val cancelAdding: () -> Unit
): Dialog(context) {
    lateinit var binding: DialogAddCancelBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogAddCancelBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.addCancelCancelBtn.setOnClickListener {
            dismiss()
        }
        binding.addCancelConfirmBtn.setOnClickListener {
            cancelAdding()
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
            params.height = (172 * context.resources.displayMetrics.density).toInt()
            attributes = params
        }
    }
}
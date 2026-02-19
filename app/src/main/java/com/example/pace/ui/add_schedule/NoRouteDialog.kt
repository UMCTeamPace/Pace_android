package com.example.pace.ui.add_schedule

import android.R
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import com.example.pace.databinding.DialogAddCancelBinding
import com.example.pace.databinding.DialogDeleteRouteBinding

class NoRouteDialog(
    context: Context,
    val setToTheNormal: () -> Unit
): Dialog(context) {
    lateinit var binding: DialogAddCancelBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogAddCancelBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.addCancelTv.text = "경로가 설정되지 않았습니다\n일반 일정으로 진행하시겠습니까?"
        binding.addCancelCancelBtn.setOnClickListener {
            dismiss()
        }
        binding.addCancelConfirmBtn.setOnClickListener {
            setToTheNormal()
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
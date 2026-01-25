package com.example.pace.ui.settings

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import com.example.pace.databinding.DialogSignoutBinding

class SignoutDialog(context: Context): Dialog(context) {
    lateinit var binding: DialogSignoutBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogSignoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.signoutCancelBtn.setOnClickListener {
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()

        // 다이얼로그 크기 및 기본 배경 투명화
        window?.apply{
            val params = attributes
            params.width = (300 * context.resources.displayMetrics.density).toInt()
            params.height = (156 * context.resources.displayMetrics.density).toInt()
        }
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

    }
}
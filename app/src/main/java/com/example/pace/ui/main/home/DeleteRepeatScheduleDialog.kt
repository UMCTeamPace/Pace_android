package com.example.pace.ui.main.home

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import com.example.pace.databinding.DialogDeleteRepeatScheduleBinding

class DeleteRepeatScheduleDialog(context: Context) : Dialog(context) {
    private lateinit var binding: DialogDeleteRepeatScheduleBinding
    private var onOptionSelectedListener: ((String) -> Unit)? = null

    fun setOnOptionSelectedListener(listener: (String) -> Unit) {
        this.onOptionSelectedListener = listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogDeleteRepeatScheduleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. 선택 일정만 삭제 (ONLY_THIS)
        binding.deleteScheduleSelectedBtn.setOnClickListener {
            onOptionSelectedListener?.invoke("ONLY_THIS")
            dismiss()
        }

        // 2. 모든 일정 반복 삭제 (ALL)
        binding.deleteScheduleAllBtn.setOnClickListener {
            onOptionSelectedListener?.invoke("ALL")
            dismiss()
        }

        // 3. 취소 (cancel_btn2)
        binding.deleteScheduleCancelBtn2.setOnClickListener {
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        // 다이얼로그 모서리를 둥글게(background drawable) 보이게 하기 위해 배경을 투명하게 설정
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }
}
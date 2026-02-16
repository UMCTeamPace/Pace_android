package com.example.pace.ui.main.home

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import com.example.pace.databinding.DialogDeleteScheduleBinding
import com.example.pace.databinding.DialogModalCaseBinding

class DeleteScheduleDialog(context: Context) : Dialog(context) {
    lateinit var binding: DialogDeleteScheduleBinding

    // 1. 확인 버튼 클릭 시 실행될 콜백 함수 정의
    private var onConfirmListener: (() -> Unit)? = null

    // 2. 외부(Fragment)에서 리스너를 설정해줄 수 있는 함수
    fun setOnConfirmListener(listener: (() -> Unit)) {
        this.onConfirmListener = listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogDeleteScheduleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 취소 버튼 (ID: delete_schedule_cancel_btn)
        binding.deleteScheduleCancelBtn.setOnClickListener {
            dismiss()
        }

        // 삭제 버튼 (ID: delete_schedule_confirm_btn)
        binding.deleteScheduleConfirmBtn.setOnClickListener {
            // ViewModel 삭제 로직 실행
            onConfirmListener?.invoke()
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }
}
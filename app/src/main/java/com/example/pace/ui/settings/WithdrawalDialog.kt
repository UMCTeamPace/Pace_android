package com.example.pace.ui.settings

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import com.example.pace.databinding.DialogSignoutBinding
import com.example.pace.databinding.DialogWithdrawalBinding

class WithdrawalDialog(context: Context) : Dialog(context) {
    private lateinit var binding: DialogWithdrawalBinding
    private var okClick: (() -> Unit)? = null

    // 프래그먼트에서 '로그아웃' 버튼 클릭 시 실행할 동작을 넘겨받는 함수
    fun setOnOkClickListener(listener: () -> Unit) {
        this.okClick = listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogWithdrawalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 배경 투명하게 (XML의 둥근 모서리가 보이게 설정)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // 취소 버튼
        binding.withdrawalCancelBtn.setOnClickListener {
            dismiss()
        }

        // 확인(로그아웃) 버튼
        binding.withdrawalOkBtn.setOnClickListener {
            android.util.Log.d("DIALOG_TEST", "확인 버튼 클릭됨")

            // 1. 리스너가 제대로 연결되었는지 확인
            if (okClick == null) {
                android.util.Log.e("DIALOG_TEST", "리스너(okClick)가 null입니다!")
            } else {
                okClick?.invoke()
            }

            // 2. 다이얼로그 닫기 (이게 있어야 창이 사라집니다)
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()

        // 다이얼로그의 너비와 높이를 고정 (dp 단위 계산)
        window?.apply {
            val params = attributes
            params.width = (300 * context.resources.displayMetrics.density).toInt()
            //params.height = (156 * context.resources.displayMetrics.density).toInt()
            attributes = params
        }
    }

}
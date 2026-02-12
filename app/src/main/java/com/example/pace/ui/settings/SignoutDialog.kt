package com.example.pace.ui.settings

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import com.example.pace.databinding.DialogSignoutBinding

class SignoutDialog(context: Context) : Dialog(context) {
    private lateinit var binding: DialogSignoutBinding
    private var okClick: (() -> Unit)? = null

    // 프래그먼트에서 '로그아웃' 버튼 클릭 시 실행할 동작을 넘겨받는 함수
    fun setOnOkClickListener(listener: () -> Unit) {
        this.okClick = listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogSignoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 배경 투명하게 (XML의 둥근 모서리가 보이게 설정)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        // 취소 버튼
        binding.signoutCancelBtn.setOnClickListener {
            dismiss()
        }

        // 확인(로그아웃) 버튼
        binding.signoutOkBtn.setOnClickListener {
            okClick?.invoke() // 설정한 로그아웃 로직 실행
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()

        // 다이얼로그의 너비와 높이를 고정 (dp 단위 계산)
        window?.apply {
            val params = attributes
            params.width = (300 * context.resources.displayMetrics.density).toInt()
            params.height = (156 * context.resources.displayMetrics.density).toInt()
            attributes = params
        }
    }
}
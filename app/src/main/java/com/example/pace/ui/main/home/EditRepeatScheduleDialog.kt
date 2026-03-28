package com.example.pace.ui.main.home

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import com.example.pace.databinding.DialogEditRepeatScheduleBinding

class EditRepeatScheduleDialog(context: Context) : Dialog(context) {
    private lateinit var binding: DialogEditRepeatScheduleBinding
    private var onOptionSelectedListener: ((String) -> Unit)? = null

    fun setOnOptionSelectedListener(listener: (String) -> Unit) {
        onOptionSelectedListener = listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogEditRepeatScheduleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.editScheduleSelectedBtn.setOnClickListener {
            onOptionSelectedListener?.invoke("ONLY_THIS")
            dismiss()
        }

        binding.editScheduleAllBtn.setOnClickListener {
            onOptionSelectedListener?.invoke("ALL")
            dismiss()
        }

        binding.editScheduleCancelBtn.setOnClickListener {
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }
}

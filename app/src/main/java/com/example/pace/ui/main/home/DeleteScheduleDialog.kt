package com.example.pace.ui.main.home

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import com.example.pace.databinding.DialogDeleteScheduleBinding
import com.example.pace.databinding.DialogModalCaseBinding

class DeleteScheduleDialog(context: Context): Dialog(context) {
    lateinit var binding: DialogDeleteScheduleBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogDeleteScheduleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.deleteScheduleCancelBtn.setOnClickListener {
            dismiss()
        }
        binding.deleteScheduleCancelBtn2.setOnClickListener {
            dismiss()
        }
    }
    override fun onStart() {
        super.onStart()
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }
}
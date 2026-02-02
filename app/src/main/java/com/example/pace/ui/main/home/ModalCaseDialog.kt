package com.example.pace.ui.main.home

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import com.example.pace.data.model.Schedule
import com.example.pace.databinding.DialogModalCaseBinding

class ModalCaseDialog(
    context: Context,
    private val scheduleList: List<Schedule>,
    private val position: Int
): Dialog(context) {
    lateinit var binding: DialogModalCaseBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogModalCaseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //binding.modalCaseTv.text = scheduleList.date
        binding.modalCaseVp.adapter = ModalVPAdapter(scheduleList)
        binding.modalCaseVp.setCurrentItem(position, false)
        binding.modalCaseCi.setViewPager(binding.modalCaseVp)
    }

    override fun onStart() {
        super.onStart()
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }
}
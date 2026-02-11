package com.example.pace.ui.main.home

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import com.example.pace.data.model.Schedule
import com.example.pace.databinding.DialogModalCaseBinding
import com.example.pace.ui.main.calendar.ScheduleViewModel
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

class ModalCaseDialog(
    context: Context,
    private val scheduleList: List<Schedule>,
    private val position: Int,
    private var date: LocalDate,
    private val viewModel: ScheduleViewModel
): Dialog(context) {

    lateinit var binding: DialogModalCaseBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogModalCaseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.modalCaseTv.text = date.year.toString() + "년 " + date.monthValue.toString() + "월 " + date.dayOfMonth.toString() + "일 " + date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN)
        binding.modalCaseVp.adapter = ModalVPAdapter(context, scheduleList, viewModel)
        binding.modalCaseVp.setCurrentItem(position, false)
        binding.modalCaseCi.setViewPager(binding.modalCaseVp)
    }

    override fun onStart() {
        super.onStart()
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.setDimAmount(0.3f)
    }
}
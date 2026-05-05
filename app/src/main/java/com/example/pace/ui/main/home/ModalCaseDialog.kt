package com.example.pace.ui.main.home

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.pace.data.model.Schedule
import com.example.pace.data.model.response.RouteOnlyScheduleData
import com.example.pace.data.model.response.ScheduleDetailResponse
import com.example.pace.databinding.DialogModalCaseBinding
import com.example.pace.data.viewmodel.ScheduleViewModel
import com.example.pace.ui.add_schedule.AddScheduleActivity
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

class ModalCaseDialog(
    context: Context,
    private val scheduleList: List<Schedule>,
    private val position: Int,
    private var date: LocalDate,
    private val viewModel: ScheduleViewModel,
    private val lifecycleOwner: LifecycleOwner,
    private val onRouteScheduleClick: (RouteOnlyScheduleData) -> Unit
): Dialog(context) {

    lateinit var binding: DialogModalCaseBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogModalCaseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                if (isShowing) dismiss()
                lifecycleOwner.lifecycle.removeObserver(this)
            }
        })

        val adapter = ModalVPAdapter(context, scheduleList) { routeSchedule ->
            onRouteScheduleClick(routeSchedule)
        }
        binding.modalCaseTv.text = date.year.toString() + "년 " + date.monthValue.toString() + "월 " + date.dayOfMonth.toString() + "일 " + date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN)
        binding.modalCaseVp.adapter = adapter
        binding.modalCaseVp.setCurrentItem(position, false)
        binding.modalCaseCi.setViewPager(binding.modalCaseVp)


        // 각 일정에 대한 상세 정보 호출
        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){
                scheduleList.filter { it.type == "ROUTE" }.forEach { schedule ->
                    viewModel.getScheduleDetail(schedule.id)
                }
                viewModel.scheduleDetailInfoMap.collect {
                    adapter.getScheduleDetails(it)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.setDimAmount(0.3f)
    }
}

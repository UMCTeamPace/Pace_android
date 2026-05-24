package com.example.pace.ui.main.home

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.pace.data.model.Schedule
import com.example.pace.data.model.response.RouteOnlyScheduleData
import com.example.pace.databinding.DialogModalCaseBinding
import com.example.pace.data.viewmodel.ScheduleViewModel
import com.example.pace.util.ScheduleSortUtils
import kotlinx.coroutines.Job
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
    private val onRouteScheduleClick: (RouteOnlyScheduleData) -> Unit,
    private val onScheduleEditClick: (Schedule) -> Unit
): Dialog(context) {

    lateinit var binding: DialogModalCaseBinding
    private var observeJob: Job? = null

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

        val adapter = ModalVPAdapter(
            context = context,
            scheduleList = scheduleList,
            onRouteScheduleClick = { routeSchedule -> onRouteScheduleClick(routeSchedule) },
            onScheduleEditClick = onScheduleEditClick
        )
        binding.modalCaseTv.text = date.year.toString() + "년 " + date.monthValue.toString() + "월 " + date.dayOfMonth.toString() + "일 " + date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN)
        binding.modalCaseVp.adapter = adapter
        binding.modalCaseVp.setCurrentItem(position, false)
        binding.modalCaseCi.setViewPager(binding.modalCaseVp)


        // 각 일정에 대한 상세 정보 호출
        observeJob = lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){
                launch {
                    viewModel.scheduleMap.collect { map ->
                        val updatedSchedules = map[date]
                            ?.sortedWith(ScheduleSortUtils.displayComparator())
                            ?: emptyList()

                        if (updatedSchedules.isEmpty()) {
                            dismiss()
                            return@collect
                        }

                        val currentScheduleId = adapter.getScheduleIdAt(binding.modalCaseVp.currentItem)
                        adapter.updateSchedules(updatedSchedules)

                        updatedSchedules.filter { it.type == "ROUTE" }.forEach { schedule ->
                            viewModel.getScheduleDetail(schedule.id)
                        }

                        val nextPosition = currentScheduleId
                            ?.let { adapter.indexOfSchedule(it) }
                            ?.takeIf { it != -1 }
                            ?: binding.modalCaseVp.currentItem.coerceAtMost(updatedSchedules.lastIndex)

                        binding.modalCaseVp.setCurrentItem(nextPosition, false)
                        binding.modalCaseCi.setViewPager(binding.modalCaseVp)
                    }
                }

                launch {
                    viewModel.scheduleDetailInfoMap.collect {
                        adapter.getScheduleDetails(it)
                    }
                }
            }
        }
    }

    override fun dismiss() {
        observeJob?.cancel()
        observeJob = null
        super.dismiss()
    }

    override fun onStart() {
        super.onStart()
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.setDimAmount(0.3f)
    }
}

package com.example.pace.ui.main.home

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import com.example.pace.data.model.Schedule
import com.example.pace.databinding.ItemModalBinding
import com.example.pace.R
import com.example.pace.data.model.response.RouteDetailResponse
import com.example.pace.data.model.response.RouteInfo
import com.example.pace.databinding.ItemRouteDetailBriefBinding
import com.example.pace.databinding.ItemRouteVehicleBinding
import com.example.pace.ui.RouteCalculator
import com.example.pace.ui.main.calendar.ScheduleViewModel
import dagger.hilt.android.qualifiers.ActivityContext

class ModalVPAdapter(
    private val context: Context,
    private val scheduleList:List<Schedule>,
    private val viewModel: ScheduleViewModel
): RecyclerView.Adapter<ModalVPAdapter.ViewHolder>() {
    lateinit var binding: ItemModalBinding
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        binding = ItemModalBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {
        holder.bind(scheduleList[position])
    }

    override fun getItemCount(): Int = scheduleList.size

    inner class ViewHolder(val binding: ItemModalBinding): RecyclerView.ViewHolder(binding.root) {
        fun bind(schedule: Schedule) {

            // 기본 정보
            val categoryIv = ContextCompat.getDrawable(context, R.drawable.ic_schedule_category)
                .mutate() as GradientDrawable
            if (schedule.eventColor != null && schedule.eventColor != 0) {
                categoryIv.setColor(schedule.eventColor)
            } else if (schedule.calendarColor != null && schedule.calendarColor != 0) {
                categoryIv.setColor(schedule.calendarColor)
            } else {
                categoryIv.setColor(context.resources.getColor(R.color.schedule_18))
            }
            binding.scheduleCategoryIv.setImageDrawable(categoryIv)
            binding.scheduleTitleTv.text = schedule.title ?: "제목 없음"
            binding.modalTimeTv.text = if(schedule.isAllDay){
                "하루 종일"
            }else{
                schedule.startTime + " -> " + schedule.endTime
            }

            // 반복 일정
            if (schedule.repeatRule == null) {
                binding.modalRepeatIv.visibility = View.GONE
                binding.modalRepeatTv.visibility = View.GONE
            } else {
                binding.modalRepeatIv.visibility = View.VISIBLE
                binding.modalRepeatTv.visibility = View.VISIBLE
                binding.modalRepeatTv.text = "반복 설정됨"
            }

            // 일반 일정 & 장소 일정 구분
            when (schedule.type) {
                // 일반 일정일 때
                "NORMAL" -> {
                    binding.modalRouteLocationLl.visibility = View.INVISIBLE
                    binding.modalRouteBriefView.visibility = View.INVISIBLE
                    binding.modalRouteBriefLl.visibility = View.INVISIBLE
                    binding.modalRouteVehicleLl.visibility = View.INVISIBLE
                    if (schedule.location.isNullOrEmpty()) {
                        binding.modalNormalLocationLl.visibility = View.GONE
                    } else {
                        binding.modalNormalLocationLl.visibility = View.VISIBLE
                        binding.modalNormalLocationTv.text = schedule.location
                    }
                    // 일정 알림
                    if (schedule.reminders.isNotEmpty()) {
                        Log.d("reminder/schedule", schedule.reminders.toString())
                        val reminder = mutableListOf<String>()
                        val sortedList = schedule.reminders.sorted()
                        sortedList.forEach {
                            when{
                                it == 0 -> reminder.add("일정 시작 시간")
                                it < 60 -> reminder.add(" " + it.toString() + "분 전")
                                it > 60 && it < 1440  -> reminder.add(" " + (it / 60).toString() + "시간 전")
                                else -> reminder.add(" " + (it/1440).toString() + "일 전")
                            }
                        }
                        binding.modalScheduleReminderTv.text = reminder.joinToString(",")
                    } else {
                        binding.modalScheduleReminderTv.text = "안함"
                    }

                    // 출발 알림
                    binding.modalDepartureReminderLl.visibility = View.INVISIBLE
                }
                // 장소 일정일 때
                "ROUTE" -> {
                    // 경로 일정 얻어오기
                    viewModel.getScheduleDetail(schedule.id)
                    val routeSchedule = viewModel.scheduleDetailInfo.value
                    val route = routeSchedule?.route
                    val reminders = routeSchedule?.reminders

                    binding.modalNormalLocationLl.visibility = View.GONE
                    binding.modalRouteBriefView.visibility = View.VISIBLE
                    binding.modalRouteLocationLl.visibility = View.VISIBLE
                    binding.modalRouteBriefLl.visibility = View.VISIBLE
                    binding.modalRouteVehicleLl.visibility = View.VISIBLE

                    // 데이터 바인딩
                    // 기본 정보 세팅
                    binding.modalRouteTv.text = route?.originName + " -> " + route?.destName
                    val startTime = RouteCalculator.convertUtcToKst(route?.departureTime)
                    val endTime = RouteCalculator.convertUtcToKst(route?.arrivalTime)
                    binding.modalRouteTimeTv.text = startTime + " -> " + endTime
                    binding.modalTotalTimeTv.text = "총 ${route?.totalTime?.div(60)}분 소요"

                    // 기존 경로 데이터 삭제
                    binding.modalRouteBriefLl.removeAllViews()
                    binding.modalRouteVehicleLl.removeAllViews()

                    var index = 0
                    // 동적으로 데이터 가져오기
                    route?.routeDetails?.forEach { data ->
                        val briefBinding = ItemRouteDetailBriefBinding.inflate(LayoutInflater.from(context), binding.modalRouteBriefLl, false)
                        val vehicleBinding = ItemRouteVehicleBinding.inflate(LayoutInflater.from(context), binding.modalRouteVehicleLl, false)

                        when(data.transitType){
                            // 걷기
                            null -> {
                                if(data.sequence == 1){
                                    briefBinding.itemRouteDetailBriefIv.setImageResource(R.drawable.ic_people)
                                }else{
                                    briefBinding.itemRouteDetailBriefIv.visibility = View.GONE
                                    briefBinding.itemRouteDetailBriefTv.updatePadding(0)
                                }
                                briefBinding.itemRouteDetailBriefTv.text = "${data.duration / 60}분"
                                briefBinding.itemRouteDetailBriefTv.setTextColor(context.resources.getColor(R.color.gray_600))

                                if(index == route.routeDetails.size - 1){
                                    vehicleBinding.itemRouteVehicleIv.setImageResource(R.drawable.ic_route_item_arrival_icon)
                                    vehicleBinding.itemRouteVehicleLineTv.text = "하차"
                                    vehicleBinding.itemRouteVehicleLineTv.setTextColor(context.resources.getColor(R.color.black))
                                    vehicleBinding.itemRouteVehicleView.visibility = View.GONE

                                    binding.modalRouteVehicleLl.addView(vehicleBinding.root)
                                }
                            }
                            // 대중교통
                            else -> {
                                // 아이콘 변경
                                val layoutDrawable = ContextCompat.getDrawable(context, R.drawable.ic_route_detail)?.mutate() as LayerDrawable
                                val iconColor = layoutDrawable.findDrawableByLayerId(R.id.ic_route_detail_color).mutate() as GradientDrawable
                                val bgColor = briefBinding.itemRouteDetailBriefTv.background.mutate() as GradientDrawable
                                val lineColor = Color.parseColor(data.lineColor)

                                when(data.transitType){
                                    "BUS" -> {
                                        val busDrawable = ContextCompat.getDrawable(context, R.drawable.ic_bus)
                                        layoutDrawable.setDrawableByLayerId(R.id.ic_route_detail_vehicle, busDrawable)
                                    }
                                    "SUBWAY" -> {
                                        val subwayDrawable = ContextCompat.getDrawable(context, R.drawable.ic_subway)
                                        layoutDrawable.setDrawableByLayerId(R.id.ic_route_detail_vehicle, subwayDrawable)
                                    }
                                }
                                iconColor.setColor(lineColor)
                                bgColor.setColor(lineColor)

                                // 일직선 정보
                                briefBinding.itemRouteDetailBriefIv.setImageDrawable(layoutDrawable)
                                briefBinding.itemRouteDetailBriefTv.text = "${data.duration / 60}분"

                                // 대중교통 정보
                                vehicleBinding.itemRouteVehicleIv.setImageDrawable(layoutDrawable)
                                vehicleBinding.itemRouteVehicleLineTv.text = data.shortName
                                vehicleBinding.itemRouteVehicleLineTv.setTextColor(lineColor)
                                vehicleBinding.itemRouteVehicleTv.text = data.departureStop

                                if(vehicleBinding.root.parent != null){
                                    (vehicleBinding.root.parent as ViewGroup).removeView(vehicleBinding.root)
                                }
                                binding.modalRouteVehicleLl.addView(vehicleBinding.root)
                            }
                        }

                        if(briefBinding.root.parent != null){
                            (briefBinding.root.parent as ViewGroup).removeView(briefBinding.root)
                        }
                        val weight = RouteCalculator.calculateWeight(data.duration)
                        val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
                        binding.modalRouteBriefLl.addView(briefBinding.root, params)

                        index++
                        Log.d("check/condition", (index == route.routeDetails.size -1).toString() )
                    }

                    // 경로 일정 알림
                    if(reminders.isNullOrEmpty()){
                        binding.modalScheduleReminderTv.text = "안함"
                        binding.modalDepartureReminderTv.text = "안함"
                    }else{
                        reminders?.forEach { reminder ->
                            when(reminder.reminderType){
                                // 일정 알림
                                "EVENT" -> {
                                    binding.modalScheduleReminderTv.text = when{
                                        reminder.minutesBefore == 0 ->  "일정 시작 시간"
                                        reminder.minutesBefore < 60 -> reminder.minutesBefore.toString() + "분 전"
                                        reminder.minutesBefore > 60 && reminder.minutesBefore < 1440 -> (reminder.minutesBefore/60).toString() + "시간 전"
                                        else -> (reminder.minutesBefore/1440).toString() + "일 전"
                                    }
                                }
                                // 출발 알림
                                "DEPARTURE" -> {
                                    if(reminder.minutesBefore < 60){
                                        binding.modalDepartureReminderTv.text = reminder.minutesBefore.toString() + "분 전"
                                    }else{
                                        binding.modalDepartureReminderTv.text = (reminder.minutesBefore/60).toString() + "시간 전"
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 메모
            if (schedule.memo.isNullOrEmpty()) {
                binding.modalMemoTv.visibility = View.INVISIBLE
            } else {
                binding.modalMemoTv.visibility = View.VISIBLE
                binding.modalMemoTv.text = schedule.memo
            }
        }
    }
}
package com.example.pace.ui.main.home

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import com.example.pace.data.model.Schedule
import com.example.pace.databinding.ItemModalBinding
import com.example.pace.R
import com.example.pace.data.model.response.RouteOnlyScheduleData
import com.example.pace.data.model.response.RouteDetailResponse
import com.example.pace.data.model.response.RouteInfo
import com.example.pace.data.model.response.ScheduleInfo
import com.example.pace.data.model.response.ScheduleDetailResponse
import com.example.pace.databinding.ItemRouteDetailBriefBinding
import com.example.pace.databinding.ItemRouteVehicleBinding
import com.example.pace.ui.RouteCalculator
import com.example.pace.data.viewmodel.ScheduleViewModel
import com.example.pace.ui.add_schedule.AddScheduleActivity
import dagger.hilt.android.qualifiers.ActivityContext
import com.google.gson.Gson
import com.example.pace.util.ScheduleDisplayTextUtils

class ModalVPAdapter(
    private val context: Context,
    private val scheduleList:List<Schedule>,
    private val onRouteScheduleClick: (RouteOnlyScheduleData) -> Unit
): RecyclerView.Adapter<ModalVPAdapter.ViewHolder>() {
    lateinit var binding: ItemModalBinding
    private var scheduleDetailInfo: Map<Long, ScheduleDetailResponse> = emptyMap()
    private val gson = Gson()
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
        val schedule = scheduleList[position]
        holder.bind(schedule)
        if(schedule.type == "ROUTE"){
            val openRouteSchedule = View.OnClickListener {
                buildRouteScheduleData(schedule)?.let(onRouteScheduleClick)
            }
            holder.binding.modalRouteLocationLl.setOnClickListener(openRouteSchedule)
            holder.binding.modalRouteBriefLl.setOnClickListener(openRouteSchedule)
            holder.binding.modalRouteVehicleLl.setOnClickListener(openRouteSchedule)
        } else {
            holder.binding.modalRouteLocationLl.setOnClickListener(null)
            holder.binding.modalRouteBriefLl.setOnClickListener(null)
            holder.binding.modalRouteVehicleLl.setOnClickListener(null)
        }
        holder.binding.root.setOnClickListener {
            val intent = Intent(context, AddScheduleActivity::class.java).apply {
                putExtra("isEdit", true)
                putExtra("SCHEDULE_ID", schedule.id)
                putExtra("OCCURRENCE_DATE", schedule.startDate)
                putExtra("SCHEDULE_TYPE", schedule.type) // ⭐ 타입 명시 (ROUTE 또는 GENERAL)

                if (schedule.type == "ROUTE") {
                    putExtra("OPEN_ROUTE_TAB", true)
                }
            }
            context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = scheduleList.size

    fun getScheduleDetails(newMap: Map<Long, ScheduleDetailResponse>){
        scheduleDetailInfo = newMap
        notifyDataSetChanged()
    }

    private fun buildRouteScheduleData(schedule: Schedule): RouteOnlyScheduleData? {
        val route = scheduleDetailInfo[schedule.id]?.route ?: schedule.routeJson?.let { routeJson ->
            runCatching { gson.fromJson(routeJson, RouteInfo::class.java) }.getOrNull()
        } ?: return null

        val color = schedule.eventColor ?: schedule.calendarColor
        val colorHex = color?.let { String.format("#%06X", 0xFFFFFF and it) }

        return RouteOnlyScheduleData(
            scheduleId = schedule.id,
            scheduleInfo = ScheduleInfo(
                title = ScheduleDisplayTextUtils.titleOrDefault(schedule.title),
                isAllDay = schedule.isAllDay,
                startDate = schedule.startDate,
                endDate = schedule.endDate,
                startTime = schedule.startTime,
                endTime = schedule.endTime,
                memo = schedule.memo,
                isPathIncluded = true,
                color = colorHex,
                calendarId = schedule.calendarId.toString()
            ),
            route = route
        )
    }

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
            binding.scheduleTitleTv.text = ScheduleDisplayTextUtils.titleOrDefault(schedule.title)
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
                    val routeSchedule = scheduleDetailInfo[schedule.id]
                    val route = routeSchedule?.route
                    val reminders = routeSchedule?.reminders

                    binding.modalNormalLocationLl.visibility = View.GONE
                    binding.modalRouteBriefView.visibility = View.VISIBLE
                    binding.modalRouteLocationLl.visibility = View.VISIBLE
                    binding.modalRouteBriefLl.visibility = View.VISIBLE
                    binding.modalRouteVehicleLl.visibility = View.VISIBLE

                    // 데이터 바인딩
                    if(route != null){
                        // 기본 정보 세팅
                        binding.modalRouteTv.text = route.originName + " -> " + route.destName
                        val startTime = RouteCalculator.convertUtcToKst(route.departureTime)
                        val endTime = RouteCalculator.convertUtcToKst(route.arrivalTime)
                        binding.modalRouteTimeTv.text = startTime + " -> " + endTime
                        binding.modalTotalTimeTv.text =  if(route.totalTime / 3600L > 0 ){
                            val time = route.totalTime % 3600L
                            if(time / 60L > 0){
                                "${route.totalTime / 3600L}시간 ${time / 60L}분"
                            }else{
                                "${route.totalTime / 3600L}시간"
                            }
                        }else{
                            "${route.totalTime / 60L}분"
                        }

                        // 기존 경로 데이터 삭제
                        binding.modalRouteBriefLl.removeAllViews()
                        binding.modalRouteVehicleLl.removeAllViews()

                        route.routeDetails?.forEachIndexed { index, data ->
                            val briefBinding = ItemRouteDetailBriefBinding.inflate(LayoutInflater.from(context))

                            // 3-1. 걷기 (TransitDetail이 null인 경우)
                            if (data.transitDetail == null) {
                                if (data.sequence == 1) { // 첫 번째 순서면 사람 아이콘
                                    briefBinding.itemRouteDetailBriefIv.setImageResource(R.drawable.ic_people)
                                } else {
                                    briefBinding.itemRouteDetailBriefIv.visibility = View.GONE
                                    briefBinding.itemRouteDetailBriefTv.updatePadding(0)
                                }
                                briefBinding.itemRouteDetailBriefTv.text = "${data.duration / 60}분"
                                briefBinding.itemRouteDetailBriefTv.setTextColor(ContextCompat.getColor(context, R.color.gray_600))

                                // 마지막 단계(하차) 처리
                                // 리스트의 마지막 인덱스인지 확인
                                if (index == route.routeDetails.size - 1) {
                                    val vehicleBinding = ItemRouteVehicleBinding.inflate(LayoutInflater.from(context), binding.modalRouteVehicleLl, false)
                                    vehicleBinding.itemRouteVehicleIv.setImageResource(R.drawable.ic_route_item_arrival_icon)
                                    vehicleBinding.itemRouteVehicleLineTv.text = "도착"
                                    vehicleBinding.itemRouteVehicleLineTv.setTextColor(ContextCompat.getColor(context, R.color.black))
                                    vehicleBinding.itemRouteVehicleView.visibility = View.GONE
                                    vehicleBinding.itemRouteVehicleTv.text = route.destName

                                    binding.modalRouteVehicleLl.addView(vehicleBinding.root)
                                }
                            }
                            // 3-2. 대중교통 (버스, 지하철)
                            else {
                                val vehicleBinding = ItemRouteVehicleBinding.inflate(LayoutInflater.from(context))

                                // 아이콘 및 색상 설정
                                val layoutDrawable = ContextCompat.getDrawable(context, R.drawable.ic_route_detail)?.mutate() as LayerDrawable
                                val iconShape = layoutDrawable.findDrawableByLayerId(R.id.ic_route_detail_color).mutate() as GradientDrawable
                                val briefBg = briefBinding.itemRouteDetailBriefTv.background.mutate() as GradientDrawable

                                // 색상 파싱 (서버에서 #RRGGBB 형태로 온다고 가정, 실패 시 기본값 검정)
                                val lineColorCode = try {
                                    Color.parseColor(data.transitDetail.lineColor ?: "#000000")
                                } catch (e: Exception) {
                                    Color.BLACK
                                }

                                // 교통 수단별 아이콘 변경
                                when (data.transitDetail.transitType) {
                                    "BUS" -> {
                                        val busDrawable = ContextCompat.getDrawable(context, R.drawable.ic_bus)
                                        layoutDrawable.setDrawableByLayerId(R.id.ic_route_detail_vehicle, busDrawable)
                                    }
                                    "SUBWAY" -> {
                                        val subwayDrawable = ContextCompat.getDrawable(context, R.drawable.ic_subway)
                                        layoutDrawable.setDrawableByLayerId(R.id.ic_route_detail_vehicle, subwayDrawable)
                                    }
                                }

                                // 색상 적용
                                iconShape.setColor(lineColorCode)
                                briefBg.setColor(lineColorCode)

                                // [상단 바] 정보 설정
                                briefBinding.itemRouteDetailBriefIv.setImageDrawable(layoutDrawable)
                                briefBinding.itemRouteDetailBriefTv.text = "${data.duration / 60}분"

                                // [하단 리스트] 상세 정보 설정
                                vehicleBinding.itemRouteVehicleIv.setImageDrawable(layoutDrawable)
                                vehicleBinding.itemRouteVehicleLineTv.text = data.transitDetail.lineName // shortName -> lineName (데이터 모델 확인 필요)
                                vehicleBinding.itemRouteVehicleLineTv.setTextColor(lineColorCode)
                                vehicleBinding.itemRouteVehicleTv.text = "${data.transitDetail.departureStop} 승차"

                                binding.modalRouteVehicleLl.addView(vehicleBinding.root)
                            }

                            // 상단 바(Brief) 뷰 추가 (Weight 적용)
                            val weight = RouteCalculator.calculateWeight(data.duration)
                            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
                            binding.modalRouteBriefLl.addView(briefBinding.root, params)
                        }
                    }

                    // 경로 일정 알림
                    if(reminders.isNullOrEmpty()){
                        binding.modalScheduleReminderTv.text = "안함"
                        binding.modalDepartureReminderTv.text = "안함"
                    }else{
                        val scheduleReminder = mutableListOf<String>()
                        val departureReminder = mutableListOf<String>()
                        reminders.forEach { reminder ->
                            when(reminder.reminderType){
                                // 일정 알림
                                "EVENT" -> {
                                    when{
                                        reminder.minutesBefore == 0 -> scheduleReminder.add("일정 시작 시간")
                                        reminder.minutesBefore < 60 -> scheduleReminder.add(reminder.minutesBefore.toString() + "분 전")
                                        reminder.minutesBefore > 60 && reminder.minutesBefore < 1440 -> scheduleReminder.add((reminder.minutesBefore/60).toString() + "시간 전")
                                        else -> scheduleReminder.add((reminder.minutesBefore/1440).toString() + "일 전")
                                    }
                                }
                                // 출발 알림
                                "DEPARTURE" -> {
                                    if(reminder.minutesBefore < 60){
                                        departureReminder.add(reminder.minutesBefore.toString() + "분 전")
                                    }else{
                                        departureReminder.add((reminder.minutesBefore/60).toString() + "시간 전")
                                    }
                                }
                            }
                        }
                        // 정렬 후 매핑
                        val sortedScheduleReminder = scheduleReminder.sorted()
                        val sortedDepartureReminder = departureReminder.sorted()
                        binding.modalScheduleReminderTv.text = sortedScheduleReminder.joinToString(", ")
                        binding.modalDepartureReminderTv.text = sortedDepartureReminder.joinToString(", ")
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

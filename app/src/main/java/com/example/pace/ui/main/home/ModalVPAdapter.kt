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
import com.example.pace.data.model.response.RouteDetail
import com.example.pace.data.model.response.RouteDetailResponse
import com.example.pace.data.model.response.RouteInfo
import com.example.pace.databinding.ItemRouteDetailBriefBinding
import com.example.pace.databinding.ItemRouteVehicleBinding
import com.example.pace.ui.WeightCalculator

class ModalVPAdapter(
    private val context: Context,
    private val scheduleList:List<Schedule>,
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
            binding.modalTimeTv.text = schedule.startTime + " -> " + schedule.endTime

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
                    binding.modalDepartureReminderTv.text = "안함"
                }
                // 장소 일정일 때
                // todo: API에서 실제 데이터 받아오기
                "ROUTE" -> {
                    binding.modalNormalLocationLl.visibility = View.GONE
                    binding.modalRouteBriefView.visibility = View.VISIBLE
                    binding.modalRouteLocationLl.visibility = View.VISIBLE
                    binding.modalRouteBriefLl.visibility = View.VISIBLE
                    binding.modalRouteVehicleLl.visibility = View.VISIBLE

                    // 데이터 바인딩
                    // 기본 정보 세팅
                    binding.modalRouteTv.text = route.originName + " -> " + route.destName
                    // todo: 비니에게 추가 요청
//                    val startTime = route.departureTime.split("T").last().take(5)
//                    val endTime = route.arrivalTime.split("T").last().take(5)
//                    binding.modalRouteTimeTv.text = startTime + " -> " + endTime
                    binding.modalTotalTimeTv.text = "총 ${route.totalTime / 60}분 소요"

                    var index = 0
                    // 동적으로 데이터 가져오기
                    route.routeDetails.forEach { data ->
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
                        val weight = WeightCalculator.forRouteDetailBrief(data.duration)
                        val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
                        binding.modalRouteBriefLl.addView(briefBinding.root, params)

                        index++
                        Log.d("check/condition", (index == route.routeDetails.size -1).toString() )
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

            // 알람 여부
            if (schedule.reminders.isNotEmpty()) {
                Log.d("reminder/schedule", schedule.reminders.toString())
                val reminder = mutableListOf<String>()
                schedule.reminders.forEach {
                    if (it < 60) {
                        reminder.add(" " + it.toString() + "분 전")
                    } else {
                        reminder.add(" " + (it / 60).toString() + "시간 전")
                    }
                }
                binding.modalScheduleReminderTv.text = reminder.joinToString(",")
            } else {
                binding.modalScheduleReminderTv.text = "안함"
            }
        }
    }
}

// 더미 데이터
val route = RouteInfo(
    originName = "서울역",
    originLat = 37.5546,
    originLng = 126.9706,
    destName = "롯데월드타워",
    destLat = 37.5133,
    destLng = 127.1028,
    totalTime = 3600,
    totalDistance = 15500,
    routeDetails = listOf(
        RouteDetailResponse(
            sequence = 1,
            duration = 1200,
            distance = 10200,
            description = "지하철 4호선 승차 후 동작역 이동",
            startLat = 37.5546,
            startLng = 126.9706,
            endLat = 37.5029,
            endLng = 126.9793,
            transitType = "SUBWAY",
            lineName = "4호선",
            lineColor = "#00a2d1",
            stopCount = 6,
            departureStop = "서울역",
            arrivalStop = "동작역",
            shortName = "4"
        ),
        RouteDetailResponse(
            sequence = 2,
            duration = 1500,
            distance = 4800,
            description = "350번 버스로 환승하여 잠실역 이동",
            startLat = 37.5029,
            startLng = 126.9793,
            endLat = 37.5133,
            endLng = 127.1001,
            transitType = "BUS",
            lineName = "350",
            lineColor = "#33cc99",
            stopCount = 12,
            departureStop = "동작역하수처리장",
            arrivalStop = "잠실역.롯데월드",
            shortName = "350"
        ),
        RouteDetailResponse(
            sequence = 3,
            duration = 900,
            distance = 500,
            description = "롯데월드타워까지 도보 이동",
            startLat = 37.5133,
            startLng = 127.1001,
            endLat = 37.5133,
            endLng = 127.1028,
            transitType = null,
            lineName = null,
            lineColor = "#cccccc",
            stopCount = 0,
            departureStop = "잠실역 2번출구",
            arrivalStop = "롯데월드타워",
            shortName = "WALK"
        )
    )
)
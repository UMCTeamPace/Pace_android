package com.example.pace.ui.main.home

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.pace.data.model.Schedule
import com.example.pace.databinding.ItemModalBinding
import com.example.pace.R
import kotlin.collections.get

class ModalVPAdapter(
    private val context: Context,
    private val scheduleList:List<Schedule>
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

    inner class ViewHolder(val binding: ItemModalBinding): RecyclerView.ViewHolder(binding.root){
        // schedule.repeatRule 한국어 변환 Map
        private val repeatFreqMap = mapOf<String, String>(
            "DAILY" to "일", "WEEKLY" to "주", "MONTHLY" to "월", "YEARLY" to "년"
        )
        private val repeatByDayMap = mapOf<String, String>(
            "SU" to "일요일", "MO" to "월요일", "TU" to "화요일", "WE" to "수요일", "TH" to "목요일", "FR" to "금요일", "SA" to "토요일"
        )

        fun bind(schedule: Schedule){
            // 기본 정보
            val categoryIv = ContextCompat.getDrawable(context, R.drawable.ic_schedule_category).mutate() as GradientDrawable
            if(schedule.eventColor != null && schedule.eventColor != 0){
                categoryIv.setColor(schedule.eventColor)
            }else if(schedule.calendarColor != null && schedule.calendarColor != 0){
                categoryIv.setColor(schedule.calendarColor)
            }else{
                categoryIv.setColor(context.resources.getColor(R.color.schedule_18))
            }
            binding.scheduleCategoryIv.setImageDrawable(categoryIv)
            binding.scheduleTitleTv.text = schedule.title ?: "제목 없음"
            binding.modalTimeTv.text = schedule.startTime + " -> " + schedule.endTime

            // 반복 일정
            if(schedule.repeatRule == null){
                binding.modalRepeatIv.visibility = View.GONE
                binding.modalRepeatTv.visibility = View.GONE
            }else{
                binding.modalRepeatIv.visibility = View.VISIBLE
                binding.modalRepeatTv.visibility = View.VISIBLE
                binding.modalRepeatTv.text = parseRRuleToKorean(schedule.repeatRule)
            }

            // 일반 일정 & 장소 일정 구분
            when(schedule.type){
                // 일반 일정일 때
                "NORMAL" -> {
                    binding.modalRouteLocationLl.visibility = View.INVISIBLE
                    binding.modalRouteView.visibility = View.INVISIBLE
                    if(schedule.location.isNullOrEmpty()){
                        binding.modalNormalLocationLl.visibility = View.GONE
                    }else{
                        binding.modalNormalLocationLl.visibility = View.VISIBLE
                        binding.modalNormalLocationTv.text = schedule.location
                    }
                    binding.modalDepartureReminderTv.text = "안함"
                }
                // 장소 일정일 때
                "ROUTE" -> {
                    binding.modalRouteLocationLl.visibility = View.VISIBLE
                    binding.modalRouteView.visibility = View.VISIBLE
                    // todo: route 가지고 데이터 바인딩
                }
            }
            // 메모
            if(schedule.memo.isNullOrEmpty()){
                binding.modalMemoTv.visibility = View.INVISIBLE
            }else{
                binding.modalMemoTv.visibility = View.VISIBLE
                binding.modalMemoTv.text = schedule.memo
            }

            // 알람 여부
            if(schedule.reminders.isNotEmpty()){
                Log.d("reminder/schedule", schedule.reminders.toString())
                val reminder = mutableListOf<String>()
                schedule.reminders.forEach {
                    if(it < 60){
                        reminder.add(" " + it.toString() + "분 전")
                    }
                    else{
                        reminder.add(" " + (it/60).toString() + "시간 전")
                    }
                }
                binding.modalScheduleReminderTv.text = reminder.joinToString(",")
            }else{
                binding.modalScheduleReminderTv.text = "안함"
            }
        }

        // schedule.repeatRule 한국어로 변환
        fun parseRRuleToKorean(rrule: String): String{
            // RRule 값 파싱
            val rule = rrule.removePrefix("RRULE:")
            val newRRule =  rule.split(";").associate{
                it.substringBefore("=") to it.substringAfter("=", "")
            }.filterKeys { it.isNotEmpty() }

            // 값 추출
            val freq = newRRule["FREQ"]
            val interval = newRRule["INTERVAL"]?.toIntOrNull() ?: 1
            val byDay = newRRule["BYDAY"]

            // 간격 처리
            val intervalText = if(interval == 1) "매${repeatFreqMap[freq]}" else "${interval}${repeatFreqMap[freq]}마다"
            // BYDAY 숫자 및 요일  처리
            val dayText = byDay?.let{
                val digit = it.filter { char -> char.isDigit() || char == '-' }
                val dayKey = it.filter { char -> char.isLetter() }
                val day = repeatByDayMap[dayKey] ?: ""

                if(digit.isNotEmpty()){
                    val order = if (digit == "-1") "마지막" else "${digit}번째"
                    "$order $day"
                }
                else{
                    day
                }
            } ?: ""

            // 최종 조합
            return when {
                freq == "WEEKLY" && dayText.isNotEmpty() -> "$intervalText $dayText"
                freq == "MONTHLY" && dayText.isNotEmpty() -> "$intervalText $dayText"
                else -> intervalText // 기본값 (매일, 매년 등)
            }
        }
    }

}
package com.example.pace.ui.main.calendar

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.pace.R
import com.example.pace.data.model.Schedule
import com.example.pace.databinding.ItemDateHeaderBinding
import com.example.pace.databinding.ItemScheduleBinding

class ScheduleAdapter(
    private var items: List<ScheduleListItem>,
    private val onPinClick: (Schedule) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_DATE_HEADER = 0
        private const val TYPE_SCHEDULE_ITEM = 1
    }

    fun updateData(newItems: List<ScheduleListItem>) {
        this.items = newItems
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is ScheduleListItem.DateHeader -> TYPE_DATE_HEADER
            is ScheduleListItem.ScheduleItem -> TYPE_SCHEDULE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_DATE_HEADER -> {
                val binding = ItemDateHeaderBinding.inflate(inflater, parent, false)
                DateHeaderViewHolder(binding)
            }
            TYPE_SCHEDULE_ITEM -> {
                val binding = ItemScheduleBinding.inflate(inflater, parent, false)
                ScheduleItemViewHolder(binding)
            }
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is ScheduleListItem.DateHeader -> (holder as DateHeaderViewHolder).bind(item)
            is ScheduleListItem.ScheduleItem -> (holder as ScheduleItemViewHolder).bind(item)
        }
    }

    override fun getItemCount(): Int = items.size

    // ViewHolder for Date Header
    inner class DateHeaderViewHolder(private val binding: ItemDateHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(header: ScheduleListItem.DateHeader) {
            binding.dateHeaderTv.text = header.date
        }
    }

    // ViewHolder for Schedule Item
    inner class ScheduleItemViewHolder(private val binding: ItemScheduleBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ScheduleListItem.ScheduleItem) {
            val schedule = item.schedule
            binding.scheduleTitleTv.text = schedule.title ?: "제목 없음"

            // 색상 설정 로직 (기존 유지)
            val color = when {
                schedule.eventColor != 0 && schedule.eventColor != null -> schedule.eventColor
                schedule.calendarColor != 0 && schedule.calendarColor != null -> schedule.calendarColor
                else -> android.graphics.Color.parseColor("#A2BD3B")
            }
            binding.scheduleCategoryIv.imageTintList = android.content.res.ColorStateList.valueOf(color)

            // --- [추가 및 수정] 시간/기간 표시 로직 ---
            val dateUpdateFormatter = java.time.format.DateTimeFormatter.ofPattern("M월 d일", java.util.Locale.KOREAN)
            val timeFormatter = java.time.format.DateTimeFormatter.ofPattern("a hh:mm", java.util.Locale.KOREAN)

            binding.scheduleTimeTv.text = try {
                val startLocalDate = java.time.LocalDate.parse(schedule.startDate)
                var endLocalDate = java.time.LocalDate.parse(schedule.endDate)

                // [보정] 하루 종일 일정인데 종료일이 다음날로 잡혀있다면 하루를 뺌
                if (schedule.isAllDay && endLocalDate.isAfter(startLocalDate)) {
                    endLocalDate = endLocalDate.minusDays(1)
                }

                when {
                    // CASE 1: 하루 종일 + 기간 (보정 후에도 날짜가 다를 때) -> "2월 9일 - 2월 11일"
                    schedule.isAllDay && startLocalDate != endLocalDate -> {
                        "${startLocalDate.format(dateUpdateFormatter)} - ${endLocalDate.format(dateUpdateFormatter)}"
                    }

                    // CASE 2: 하루 종일 + 당일 (보정 후 날짜가 같아짐) -> "하루 종일"
                    schedule.isAllDay -> {
                        "하루 종일"
                    }

                    // CASE 3: 일반 일정 + 기간 -> 시간 포함 표시
                    startLocalDate != endLocalDate -> {
                        val startTime = java.time.LocalTime.parse(schedule.startTime)
                        val endTime = java.time.LocalTime.parse(schedule.endTime)
                        "${startLocalDate.format(dateUpdateFormatter)} ${startTime.format(timeFormatter)} - ${endLocalDate.format(dateUpdateFormatter)} ${endTime.format(timeFormatter)}"
                    }

                    // CASE 4: 일반 일정 + 당일 -> 시간만 표시
                    else -> {
                        val startTime = java.time.LocalTime.parse(schedule.startTime)
                        val endTime = java.time.LocalTime.parse(schedule.endTime)
                        "${startTime.format(timeFormatter)} - ${endTime.format(timeFormatter)}"
                    }
                }
            } catch (e: Exception) {
                "${schedule.startDate} - ${schedule.endDate}"
            }
            binding.schedulePinnedIv.visibility = View.GONE
            binding.scheduleAlertTv.visibility = View.GONE
            binding.scheduleCheckbox.visibility = View.GONE
            // Pin 아이콘 리스너 및 상태 변경
            binding.schedulePinIv.setOnClickListener { onPinClick(schedule) }


            if (!schedule.location.isNullOrEmpty()) {
                binding.scheduleNormalLocationLl.visibility = ViewGroup.VISIBLE
                binding.scheduleNormalLocationTv.text = schedule.location
                binding.scheduleRouteLocationLl.visibility = ViewGroup.GONE
            } else {
                binding.scheduleNormalLocationLl.visibility = ViewGroup.GONE
                binding.scheduleRouteLocationLl.visibility = if (schedule.withRoute) ViewGroup.VISIBLE else ViewGroup.GONE
            }
        }
    }
}

// Sealed class for the list items
sealed class ScheduleListItem {
    data class DateHeader(val date: String) : ScheduleListItem()
    data class ScheduleItem(val schedule: Schedule) : ScheduleListItem()
}
package com.example.pace.ui.main.calendar

import android.view.LayoutInflater
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

            // Pin 아이콘 리스너 및 상태 변경
            binding.schedulePinIv.setOnClickListener { onPinClick(schedule) }
            // isPinned 상태에 따라 pin 아이콘의 src를 변경할 수 있습니다.
            // 예: binding.schedulePinIv.setImageResource(if (schedule.isPinned) R.drawable.ic_pin_filled else R.drawable.ic_pin)
            // (ic_pin_filled 라는 drawable이 있다고 가정)

            if (schedule.isAllDay) {
                binding.scheduleTimeTv.text = "하루 종일"
            } else {
                binding.scheduleTimeTv.text = "${schedule.startTime} - ${schedule.endTime}"
            }

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
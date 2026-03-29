package com.example.pace.ui.main.calendar

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.example.pace.R
import com.example.pace.data.model.Schedule
import com.example.pace.data.model.response.RouteInfo
import com.example.pace.databinding.ItemDateHeaderBinding
import com.example.pace.databinding.ItemScheduleBinding
import com.example.pace.ui.RouteCalculator
import com.example.pace.ui.main.home.DeleteScheduleDialog
import com.example.pace.ui.main.home.ScheduleTouchHelper
import com.google.gson.Gson

class ScheduleAdapter(
    private val context: Context,
    private var items: List<ScheduleListItem>,
    private val onPinClick: (Schedule) -> Unit,
    private val onEditSelect: (Long) -> Unit,
    private var routeInfoMap: Map<Long, RouteInfo> = emptyMap()
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val gson = Gson()

    lateinit var scheduleTouchHelper: ScheduleTouchHelper
    private var isEditMode = false
    private var selectedIds = setOf<Long>()

    companion object {
        private const val TYPE_DATE_HEADER = 0
        private const val TYPE_SCHEDULE_ITEM = 1
    }

    fun setEditMode(enabled: Boolean) {
        isEditMode = enabled
        notifyDataSetChanged()
    }

    fun updateSelectedIds(ids: Set<Long>) {
        selectedIds = ids
        notifyDataSetChanged()
    }

    fun updateData(newItems: List<ScheduleListItem>) {
        items = newItems
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
            TYPE_DATE_HEADER -> DateHeaderViewHolder(
                ItemDateHeaderBinding.inflate(inflater, parent, false)
            )

            TYPE_SCHEDULE_ITEM -> ScheduleItemViewHolder(
                ItemScheduleBinding.inflate(inflater, parent, false)
            )

            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        holder.itemView.findViewById<ConstraintLayout>(R.id.schedule_view_top)?.translationX = 0f

        when (val item = items[position]) {
            is ScheduleListItem.DateHeader -> (holder as DateHeaderViewHolder).bind(item)
            is ScheduleListItem.ScheduleItem -> (holder as ScheduleItemViewHolder).bind(item, holder)
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateRouteInfo(newRouteMap: Map<Long, RouteInfo>) {
        routeInfoMap = newRouteMap
        notifyDataSetChanged()
    }

    inner class DateHeaderViewHolder(private val binding: ItemDateHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(header: ScheduleListItem.DateHeader) {
            binding.dateHeaderTv.text = header.date
        }
    }

    inner class ScheduleItemViewHolder(private val binding: ItemScheduleBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ScheduleListItem.ScheduleItem, holder: RecyclerView.ViewHolder) {
            val schedule = item.schedule

            if (isEditMode) {
                binding.scheduleCheckbox.visibility = View.VISIBLE
                binding.scheduleCheckbox.isChecked = selectedIds.contains(schedule.id)
                binding.schedulePinIv.visibility = View.GONE
                binding.root.setOnClickListener { onEditSelect(schedule.id) }
                binding.scheduleCheckbox.setOnClickListener { onEditSelect(schedule.id) }
            } else {
                binding.scheduleCheckbox.visibility = View.GONE
                binding.schedulePinnedIv.visibility = if (schedule.isPinned) View.VISIBLE else View.GONE
                binding.root.setOnClickListener { }
                binding.schedulePinIv.setOnClickListener {
                    onPinClick(schedule)
                    scheduleTouchHelper.closeSwipedMenu(holder)
                }
                binding.scheduleDeleteIv.setOnClickListener {
                    DeleteScheduleDialog(context).show()
                }
            }

            binding.scheduleTitleTv.text = schedule.title ?: "제목 없음"

            val colorResId = when {
                schedule.eventColor != null && schedule.eventColor != 0 -> schedule.eventColor
                schedule.calendarColor != null && schedule.calendarColor != 0 -> schedule.calendarColor
                else -> Color.parseColor("#A2BD3B")
            }
            binding.scheduleCategoryIv.imageTintList = ColorStateList.valueOf(colorResId)

            binding.scheduleTimeTv.text =
                if (schedule.isAllDay) "하루 종일" else "${schedule.startTime} - ${schedule.endTime}"

            if (!schedule.repeatRule.isNullOrEmpty()) {
                binding.scheduleRepeatIv.visibility = View.VISIBLE
                binding.scheduleRepeatTv.visibility = View.VISIBLE
                binding.scheduleRepeatTv.text = "반복 일정"
            } else {
                binding.scheduleRepeatIv.visibility = View.GONE
                binding.scheduleRepeatTv.visibility = View.GONE
            }

            val serverRouteInfo = routeInfoMap[schedule.id]
            val localRouteInfo = schedule.routeJson?.let {
                runCatching { gson.fromJson(it, RouteInfo::class.java) }.getOrNull()
            }

            if (schedule.type == "ROUTE") {
                binding.scheduleNormalLocationLl.visibility = View.GONE
                binding.scheduleRouteLocationLl.visibility = View.VISIBLE
                binding.scheduleRouteNameTv.text = buildRouteName(localRouteInfo, serverRouteInfo, schedule)
                binding.scheduleRouteRangeTv.text = buildRouteRange(localRouteInfo, serverRouteInfo)
                binding.scheduleRouteDurationTv.text = buildRouteDuration(localRouteInfo, serverRouteInfo)
            } else {
                binding.scheduleRouteLocationLl.visibility = View.GONE
                if (!schedule.location.isNullOrEmpty()) {
                    binding.scheduleNormalLocationLl.visibility = View.VISIBLE
                    binding.scheduleNormalLocationIv.visibility = View.VISIBLE
                    binding.scheduleNormalLocationTv.text = schedule.location
                } else {
                    binding.scheduleNormalLocationLl.visibility = View.GONE
                }
            }
        }

        private fun buildRouteName(
            localRouteInfo: RouteInfo?,
            serverRouteInfo: RouteInfo?,
            schedule: Schedule
        ): String {
            val serverName = serverRouteInfo?.takeIf {
                it.originName.isNotBlank() && it.destName.isNotBlank()
            }?.let {
                "${it.originName} -> ${it.destName}"
            }
            val localName = localRouteInfo?.takeIf {
                it.originName.isNotBlank() && it.destName.isNotBlank()
            }?.let {
                "${it.originName} -> ${it.destName}"
            }

            return serverName ?: localName ?: schedule.location ?: "경로 정보 없음"
        }

        private fun buildRouteRange(
            localRouteInfo: RouteInfo?,
            serverRouteInfo: RouteInfo?
        ): String {
            val serverRange = buildTimeRange(
                serverRouteInfo?.departureTime?.let(::formatRouteTime),
                serverRouteInfo?.arrivalTime?.let(::formatRouteTime)
            )
            val localRouteRange = buildTimeRange(
                localRouteInfo?.departureTime?.let(::formatRouteTime),
                localRouteInfo?.arrivalTime?.let(::formatRouteTime)
            )

            return serverRange ?: localRouteRange ?: "계산 중..."
        }

        private fun buildRouteDuration(
            localRouteInfo: RouteInfo?,
            serverRouteInfo: RouteInfo?
        ): String {
            val totalSeconds = serverRouteInfo?.totalTime?.takeIf { it > 0 }
                ?: localRouteInfo?.totalTime?.takeIf { it > 0 }
                ?: return "시간 정보 없음"

            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60

            return if (hours > 0) {
                "${hours}시간 ${minutes}분"
            } else {
                "${minutes}분"
            }
        }

        private fun buildTimeRange(startTime: String?, endTime: String?): String? {
            return if (!startTime.isNullOrBlank() && !endTime.isNullOrBlank()) {
                "$startTime - $endTime"
            } else {
                null
            }
        }

        private fun formatRouteTime(rawTime: String): String? {
            return RouteCalculator.convertUtcToKst(rawTime).ifBlank { null }
        }
    }
}

sealed class ScheduleListItem {
    data class DateHeader(val date: String) : ScheduleListItem()
    data class ScheduleItem(val schedule: Schedule) : ScheduleListItem()
}

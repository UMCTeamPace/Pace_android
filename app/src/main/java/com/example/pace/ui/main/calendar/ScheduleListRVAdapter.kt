package com.example.pace.ui.main.calendar

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.fonts.FontStyle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.daimajia.swipe.SwipeLayout
import com.daimajia.swipe.adapters.RecyclerSwipeAdapter
import com.example.pace.R
import com.example.pace.data.model.Schedule
import com.example.pace.data.model.response.RouteInfo
import com.example.pace.databinding.ItemDateHeaderBinding
import com.example.pace.databinding.ItemScheduleBinding
import com.example.pace.ui.RouteCalculator
import com.google.gson.Gson
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class ScheduleListRVAdapter(
    private val context: Context,
    private val onPinClick: (Schedule) -> Unit,
    private val onDeleteClick: (Schedule) -> Unit,
    private val onEditClick: (Schedule) -> Unit,
    private val onSelectionToggle: (Schedule) -> Unit,
    private val onItemClick: (Schedule) -> Unit
) : RecyclerSwipeAdapter<RecyclerView.ViewHolder>() {

    private val gson = Gson()

    private var items = mutableListOf<ScheduleListItem>()
    private var isEditMode = false
    private var selectedKeys = setOf<String>()
    private var routeInfoMap: Map<Long, RouteInfo> = emptyMap()
    private val primaryTextColor by lazy { ContextCompat.getColor(context, R.color.text_primary) }
    private val secondaryTextColor by lazy { ContextCompat.getColor(context, R.color.text_secondary) }
    private val disabledTextColor by lazy { ContextCompat.getColor(context, R.color.text_disabled) }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }

    fun updateData(newItems: List<ScheduleListItem>, newRouteMap: Map<Long, RouteInfo> = emptyMap()) {
        val diffResult = DiffUtil.calculateDiff(
            ScheduleListDiffCallback(
                oldItems = items,
                newItems = newItems,
                oldRouteMap = routeInfoMap,
                newRouteMap = newRouteMap
            )
        )
        routeInfoMap = newRouteMap
        items = newItems.toMutableList()
        diffResult.dispatchUpdatesTo(this)
    }

    fun setEditMode(enabled: Boolean) {
        isEditMode = enabled
        notifyDataSetChanged()
    }

    fun updateSelectedKeys(keys: Set<String>) {
        selectedKeys = keys
        notifyDataSetChanged()
    }

    fun updateRouteMap(newRouteMap: Map<Long, RouteInfo>) {
        if (routeInfoMap == newRouteMap) return
        routeInfoMap = newRouteMap

        items.forEachIndexed { index, item ->
            val scheduleItem = item as? ScheduleListItem.ScheduleItem ?: return@forEachIndexed
            if (scheduleItem.schedule.type == "ROUTE") {
                notifyItemChanged(index)
            }
        }
    }

    fun getSelectedSchedules(keys: Set<String>): List<Schedule> {
        return items.mapNotNull { item ->
            (item as? ScheduleListItem.ScheduleItem)?.schedule
        }.filter { selectionKey(it) in keys }
    }

    private fun selectionKey(schedule: Schedule): String = "${schedule.id}|${schedule.startDate}"

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is ScheduleListItem.DateHeader -> TYPE_HEADER
            is ScheduleListItem.ScheduleItem -> TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderViewHolder(ItemDateHeaderBinding.inflate(inflater, parent, false))
        } else {
            ItemViewHolder(ItemScheduleBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        if (holder is HeaderViewHolder && item is ScheduleListItem.DateHeader) {
            holder.bind(item)
        } else if (holder is ItemViewHolder && item is ScheduleListItem.ScheduleItem) {
            holder.bind(item.schedule)
        }
    }

    override fun getItemCount(): Int = items.size

    override fun getSwipeLayoutResourceId(position: Int): Int = R.id.item_schedule

    private class ScheduleListDiffCallback(
        private val oldItems: List<ScheduleListItem>,
        private val newItems: List<ScheduleListItem>,
        private val oldRouteMap: Map<Long, RouteInfo>,
        private val newRouteMap: Map<Long, RouteInfo>
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = oldItems.size

        override fun getNewListSize(): Int = newItems.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldItems[oldItemPosition]
            val newItem = newItems[newItemPosition]

            return when {
                oldItem is ScheduleListItem.DateHeader && newItem is ScheduleListItem.DateHeader ->
                    oldItem.date == newItem.date

                oldItem is ScheduleListItem.ScheduleItem && newItem is ScheduleListItem.ScheduleItem ->
                    oldItem.schedule.id == newItem.schedule.id &&
                        oldItem.schedule.startDate == newItem.schedule.startDate

                else -> false
            }
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldItem = oldItems[oldItemPosition]
            val newItem = newItems[newItemPosition]

            if (oldItem != newItem) return false

            return if (oldItem is ScheduleListItem.ScheduleItem && newItem is ScheduleListItem.ScheduleItem) {
                oldRouteMap[oldItem.schedule.id] == newRouteMap[newItem.schedule.id]
            } else {
                true
            }
        }
    }

    inner class HeaderViewHolder(private val binding: ItemDateHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(header: ScheduleListItem.DateHeader) {
            binding.dateHeaderTv.text = header.date
            val isToday =
                LocalDate.parse(
                    header.date,
                    DateTimeFormatter.ofPattern("yyyy년 MM월 dd일 (E)", Locale.KOREAN)
                ) == LocalDate.now()

            binding.dateHeaderTv.setTextAppearance(
                if (isToday) R.style.TextAppearance_App_BodyMd_SemiBold
                else R.style.TextAppearance_App_BodyMd_Medium
            )

            binding.dateHeaderTv.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (isToday) R.color.text_primary else R.color.text_tertiary
                )
            )
        }
    }

    inner class ItemViewHolder(private val binding: ItemScheduleBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(schedule: Schedule) {
            binding.scheduleViewTop.translationX = 0f
            binding.root.setSwipeEnabled(!isEditMode)

            binding.scheduleTitleTv.text = schedule.title ?: "제목 없음"
            binding.scheduleTimeTv.text =
                if (schedule.isAllDay) "하루 종일" else "${schedule.startTime} - ${schedule.endTime}"

            val colorResId = schedule.eventColor.takeIf { it != null && it != 0 }
                ?: schedule.calendarColor.takeIf { it != null && it != 0 }
                ?: Color.parseColor("#A2BD3B")
            applyTodayScheduleColors(schedule, colorResId)

            if (isEditMode) {
                binding.scheduleCheckbox.visibility = View.VISIBLE
                binding.scheduleCheckbox.isChecked = selectedKeys.contains(selectionKey(schedule))
                binding.scheduleCheckbox.isClickable = false
                binding.schedulePinnedIv.visibility = if (schedule.isPinned) View.VISIBLE else View.GONE
            } else {
                binding.scheduleCheckbox.visibility = View.GONE
                binding.schedulePinnedIv.visibility = if (schedule.isPinned) View.VISIBLE else View.GONE
            }

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
                    binding.scheduleNormalLocationTv.text = schedule.location
                } else {
                    binding.scheduleNormalLocationLl.visibility = View.GONE
                }
            }

            binding.schedulePinIv.setOnClickListener {
                onPinClick(schedule)
                mItemManger.closeItem(bindingAdapterPosition)
            }
            binding.scheduleEditIv.setOnClickListener {
                onEditClick(schedule)
                mItemManger.closeItem(bindingAdapterPosition)
            }
            binding.scheduleDeleteIv.setOnClickListener {
                onDeleteClick(schedule)
                mItemManger.closeItem(bindingAdapterPosition)
            }
            binding.scheduleViewTop.setOnClickListener {
                if (isEditMode) {
                    onSelectionToggle(schedule)
                } else {
                    onItemClick(schedule)
                }
            }
            binding.scheduleCheckbox.setOnClickListener {
                if (isEditMode) {
                    onSelectionToggle(schedule)
                }
            }

            binding.root.showMode = SwipeLayout.ShowMode.LayDown
            binding.root.addDrag(SwipeLayout.DragEdge.Left, binding.scheduleLeftBottomWrapper)
            binding.root.addDrag(SwipeLayout.DragEdge.Right, binding.scheduleRightBottomWrapper)

            mItemManger.bindView(itemView, bindingAdapterPosition)
        }

        private fun applyTodayScheduleColors(schedule: Schedule, scheduleColor: Int) {
            val today = LocalDate.now()
            val scheduleDate = runCatching { LocalDate.parse(schedule.startDate.take(10)) }.getOrNull()
            val isToday = scheduleDate == today
            val isPastTimedSchedule = isToday &&
                !schedule.isAllDay &&
                runCatching { LocalTime.parse(schedule.endTime) }.getOrNull()?.isBefore(LocalTime.now()) == true

            val titleColor = when {
                isToday && schedule.isAllDay -> primaryTextColor
                isPastTimedSchedule -> disabledTextColor
                else -> primaryTextColor
            }
            val secondaryColor = if (isPastTimedSchedule) disabledTextColor else secondaryTextColor
            val accentColor = if (isPastTimedSchedule) disabledTextColor else secondaryTextColor
            val categoryColor = if (isPastTimedSchedule) {
                (scheduleColor and 0x00FFFFFF) or (0x80 shl 24)
            } else {
                scheduleColor
            }

            binding.scheduleTitleTv.setTextColor(titleColor)
            binding.scheduleTimeTv.setTextColor(secondaryColor)
            binding.scheduleRepeatTv.setTextColor(secondaryColor)
            binding.scheduleNormalLocationTv.setTextColor(secondaryColor)
            binding.scheduleRouteNameTv.setTextColor(titleColor)
            binding.scheduleRouteRangeTv.setTextColor(secondaryColor)
            binding.scheduleRouteDurationTv.setTextColor(secondaryColor)

            binding.scheduleCategoryIv.imageTintList = ColorStateList.valueOf(categoryColor)
            binding.scheduleRepeatIv.imageTintList = ColorStateList.valueOf(accentColor)
            binding.scheduleNormalLocationIv.imageTintList = ColorStateList.valueOf(accentColor)
            binding.scheduleRouteLocationIv.imageTintList = ColorStateList.valueOf(accentColor)
            binding.schedulePinnedIv.imageTintList =
                if (isPastTimedSchedule) ColorStateList.valueOf(disabledTextColor) else null
        }

        private fun buildRouteName(
            localRouteInfo: RouteInfo?,
            serverRouteInfo: RouteInfo?,
            schedule: Schedule
        ): String {
            val serverName = serverRouteInfo?.let { info ->
                if (info.originName.isNotBlank() && info.destName.isNotBlank()) {
                    "${info.originName} -> ${info.destName}"
                } else {
                    null
                }
            }
            val localName = localRouteInfo?.let { info ->
                if (info.originName.isNotBlank() && info.destName.isNotBlank()) {
                    "${info.originName} -> ${info.destName}"
                } else {
                    null
                }
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

package com.example.pace.ui.main.calendar

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
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
import com.example.pace.util.SearchTextMatcher
import com.google.gson.Gson

class SearchAdapter(
    private val context: Context,
    private var query: String = "",
    private val onPinClick: (Schedule) -> Unit,
    private val onDeleteClick: (Schedule) -> Unit,
    private val onEditClick: (Schedule) -> Unit,
    private val onEditSelect: (Long) -> Unit,
    private var routeInfoMap: Map<Long, RouteInfo> = emptyMap()
) : RecyclerSwipeAdapter<RecyclerView.ViewHolder>() {

    private val gson = Gson()
    private var items = mutableListOf<ScheduleListItem>()
    private var selectedIds = setOf<Long>()

    companion object {
        private const val TYPE_DATE_HEADER = 0
        private const val TYPE_SCHEDULE_ITEM = 1
    }

    fun updateItemPinStatus(scheduleId: Long, isPinned: Boolean) {
        val updatedItems = items.map { item ->
            if (item is ScheduleListItem.ScheduleItem && item.schedule.id == scheduleId) {
                item.copy(schedule = item.schedule.copy(isPinned = isPinned))
            } else {
                item
            }
        }

        val finalItems = mutableListOf<ScheduleListItem>()
        val tempDayItems = mutableListOf<ScheduleListItem.ScheduleItem>()

        for (item in updatedItems) {
            when (item) {
                is ScheduleListItem.DateHeader -> {
                    if (tempDayItems.isNotEmpty()) {
                        finalItems.addAll(
                            tempDayItems.sortedWith(
                                compareBy(
                                    { !it.schedule.isPinned },
                                    { !it.schedule.isAllDay },
                                    { it.schedule.startTime }
                                )
                            )
                        )
                        tempDayItems.clear()
                    }
                    finalItems.add(item)
                }

                is ScheduleListItem.ScheduleItem -> tempDayItems.add(item)
            }
        }

        if (tempDayItems.isNotEmpty()) {
            finalItems.addAll(
                tempDayItems.sortedWith(
                    compareBy(
                        { !it.schedule.isPinned },
                        { !it.schedule.isAllDay },
                        { it.schedule.startTime }
                    )
                )
            )
        }

        updateData(finalItems, routeInfoMap)
    }

    fun updateSelectedIds(ids: Set<Long>) {
        selectedIds = ids
        notifyDataSetChanged()
    }

    fun submitList(newItems: List<ScheduleListItem>) {
        updateData(newItems, routeInfoMap)
    }

    fun updateRouteInfo(newRouteMap: Map<Long, RouteInfo>) {
        if (routeInfoMap == newRouteMap) return
        routeInfoMap = newRouteMap

        items.forEachIndexed { index, item ->
            val scheduleItem = item as? ScheduleListItem.ScheduleItem ?: return@forEachIndexed
            if (scheduleItem.schedule.type == "ROUTE") {
                notifyItemChanged(index)
            }
        }
    }

    fun updateQuery(newQuery: String) {
        query = newQuery
        notifyDataSetChanged()
    }

    private fun updateData(newItems: List<ScheduleListItem>, newRouteMap: Map<Long, RouteInfo>) {
        val diffResult = DiffUtil.calculateDiff(
            SearchListDiffCallback(
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

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is ScheduleListItem.DateHeader -> TYPE_DATE_HEADER
        is ScheduleListItem.ScheduleItem -> TYPE_SCHEDULE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_DATE_HEADER -> DateHeaderViewHolder(
                ItemDateHeaderBinding.inflate(inflater, parent, false)
            )

            TYPE_SCHEDULE_ITEM -> SearchItemViewHolder(
                ItemScheduleBinding.inflate(inflater, parent, false)
            )

            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is ScheduleListItem.DateHeader -> (holder as DateHeaderViewHolder).bind(item.date)
            is ScheduleListItem.ScheduleItem -> (holder as SearchItemViewHolder).bind(item.schedule, query)
        }
    }

    override fun getItemCount(): Int = items.size

    override fun getSwipeLayoutResourceId(position: Int): Int {
        return if (getItemViewType(position) == TYPE_SCHEDULE_ITEM) R.id.item_schedule else 0
    }

    private class SearchListDiffCallback(
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

    inner class DateHeaderViewHolder(private val binding: ItemDateHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(date: String) {
            binding.dateHeaderTv.text = date
        }
    }

    inner class SearchItemViewHolder(private val binding: ItemScheduleBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(schedule: Schedule, query: String) {
            val title = schedule.title ?: "제목 없음"
            if (query.isBlank()) {
                binding.scheduleTitleTv.text = title
            } else {
                val matchRange = SearchTextMatcher.findMatchRange(title, query)
                if (title.contains("ㅓ")) {
                    android.util.Log.d(
                        "SearchDebug",
                        "highlight query='${query}' title='${title}' range=${matchRange?.start}..${matchRange?.endExclusive}"
                    )
                }
                if (matchRange != null) {
                    val spannable = SpannableString(title)
                    spannable.setSpan(
                        ForegroundColorSpan(ContextCompat.getColor(context, R.color.semantic_info)),
                        matchRange.start,
                        matchRange.endExclusive,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    binding.scheduleTitleTv.text = spannable
                } else {
                    binding.scheduleTitleTv.text = title
                }
            }

            val colorResId = schedule.eventColor ?: schedule.calendarColor ?: Color.parseColor("#A2BD3B")
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
                binding.scheduleNormalLocationLl.visibility =
                    if (schedule.location.isNullOrEmpty()) View.GONE else View.VISIBLE
                binding.scheduleNormalLocationTv.text = schedule.location ?: ""
            }

            binding.schedulePinnedIv.visibility = if (schedule.isPinned) View.VISIBLE else View.GONE
            binding.scheduleCheckbox.visibility = View.INVISIBLE

            binding.root.showMode = SwipeLayout.ShowMode.LayDown
            binding.root.addDrag(SwipeLayout.DragEdge.Left, binding.scheduleLeftBottomWrapper)
            binding.root.addDrag(SwipeLayout.DragEdge.Right, binding.scheduleRightBottomWrapper)
            mItemManger.bindView(itemView, bindingAdapterPosition)

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
            val localRange = buildTimeRange(
                localRouteInfo?.departureTime?.let(::formatRouteTime),
                localRouteInfo?.arrivalTime?.let(::formatRouteTime)
            )

            return serverRange ?: localRange ?: "계산 중.."
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

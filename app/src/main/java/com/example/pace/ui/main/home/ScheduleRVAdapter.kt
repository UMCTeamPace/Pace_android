package com.example.pace.ui.main.home

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.daimajia.swipe.SwipeLayout
import com.daimajia.swipe.adapters.RecyclerSwipeAdapter
import com.example.pace.R
import com.example.pace.data.model.Schedule
import com.example.pace.data.model.response.RouteInfo
import com.example.pace.databinding.ItemScheduleBinding
import com.example.pace.ui.RouteCalculator
import com.google.gson.Gson

class ScheduleRVAdapter(
    private var scheduleList: MutableList<Schedule>,
    private val context: Context,
    private val onPinClick: (Schedule) -> Unit,
) : RecyclerSwipeAdapter<ScheduleRVAdapter.ViewHolder>() {

    private val gson = Gson()

    lateinit var mOnClickListener: MyOnClickListener

    private var routeInfoMap: Map<Long, RouteInfo> = emptyMap()

    interface MyOnClickListener {
        fun showModalCase(scheduleList: List<Schedule>, position: Int)
        fun onEdit(schedule: Schedule)
        fun onDelete(schedule: Schedule)
    }

    fun setMyOnClickListener(myOnClickListener: MyOnClickListener) {
        mOnClickListener = myOnClickListener
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateData(newSchedules: List<Schedule>, newRouteMap: Map<Long, RouteInfo> = emptyMap()) {
        routeInfoMap = newRouteMap
        scheduleList.clear()
        scheduleList.addAll(newSchedules)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemScheduleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val schedule = scheduleList[position]
        val viewTop = holder.binding.scheduleViewTop

        holder.bind(schedule)

        holder.binding.root.showMode = SwipeLayout.ShowMode.LayDown
        holder.binding.root.addDrag(SwipeLayout.DragEdge.Left, holder.binding.scheduleLeftBottomWrapper)
        holder.binding.root.addDrag(SwipeLayout.DragEdge.Right, holder.binding.scheduleRightBottomWrapper)

        mItemManger.bindView(holder.itemView, position)

        holder.binding.schedulePinIv.setOnClickListener {
            onPinClick(schedule)
            mItemManger.closeItem(position)
        }

        holder.binding.scheduleEditIv.setOnClickListener {
            mOnClickListener.onEdit(schedule)
            mItemManger.closeItem(position)
        }

        holder.binding.scheduleDeleteIv.setOnClickListener {
            mOnClickListener.onDelete(schedule)
            mItemManger.closeItem(position)
        }

        viewTop.setOnClickListener {
            if (viewTop.translationX == 0f) {
                mOnClickListener.showModalCase(scheduleList, position)
            }
        }
    }

    override fun getItemCount(): Int = scheduleList.size

    override fun getSwipeLayoutResourceId(position: Int): Int = R.id.item_schedule

    inner class ViewHolder(val binding: ItemScheduleBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(schedule: Schedule) {
            binding.scheduleCheckbox.visibility = View.GONE
            binding.scheduleTitleTv.text = schedule.title ?: "제목 없음"

            val colorResId = when {
                schedule.eventColor != null && schedule.eventColor != 0 -> schedule.eventColor
                schedule.calendarColor != null && schedule.calendarColor != 0 -> schedule.calendarColor
                else -> Color.parseColor("#A2BD3B")
            }
            binding.scheduleCategoryIv.imageTintList = ColorStateList.valueOf(colorResId)

            binding.scheduleTimeTv.text =
                if (schedule.isAllDay) "하루 종일" else "${schedule.startTime} - ${schedule.endTime}"

            binding.schedulePinnedIv.visibility = if (schedule.isPinned) View.VISIBLE else View.GONE
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

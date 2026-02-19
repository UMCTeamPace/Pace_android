package com.example.pace.ui.main.calendar

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.pace.R
import com.example.pace.data.model.Schedule
import com.example.pace.data.model.response.RouteInfo // 추가
import com.example.pace.databinding.ItemDateHeaderBinding
import com.example.pace.databinding.ItemScheduleBinding
import com.example.pace.ui.main.home.ScheduleTouchHelper

class ScheduleListRVAdapter(
    private val context: Context,
    private val onPinClick: (Schedule) -> Unit,
    private val onDeleteClick: (Schedule) -> Unit,
    private val onEditClick: (Schedule) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var items = mutableListOf<ScheduleListItem>()
    private var isEditMode = false
    private var selectedIds = setOf<Long>()
    private var routeInfoMap: Map<Long, RouteInfo> = emptyMap() // 경로 정보 맵 추가
    lateinit var scheduleTouchHelper: ScheduleTouchHelper

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }

    // 데이터 업데이트 시 경로 정보도 함께 받을 수 있도록 수정
    fun updateData(newItems: List<ScheduleListItem>, newRouteMap: Map<Long, RouteInfo> = emptyMap()) {
        this.routeInfoMap = newRouteMap
        this.items = newItems.toMutableList()
        notifyDataSetChanged()
    }

    fun setEditMode(enabled: Boolean) {
        this.isEditMode = enabled
        notifyDataSetChanged()
    }

    fun updateSelectedIds(ids: Set<Long>) {
        this.selectedIds = ids
        notifyDataSetChanged()
    }

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

    inner class HeaderViewHolder(private val binding: ItemDateHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(header: ScheduleListItem.DateHeader) {
            binding.dateHeaderTv.text = header.date
        }
    }

    inner class ItemViewHolder(private val binding: ItemScheduleBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(schedule: Schedule) {
            binding.scheduleViewTop.translationX = 0f

            // 1. 기본 텍스트 및 시간 설정
            binding.scheduleTitleTv.text = schedule.title ?: "제목 없음"
            binding.scheduleTimeTv.text = if (schedule.isAllDay) "하루 종일" else "${schedule.startTime} - ${schedule.endTime}"

            // 2. 색상 설정 (기존 로직 유지)
            val colorResId = schedule.eventColor.takeIf { it != null && it != 0 }
                ?: schedule.calendarColor.takeIf { it != null && it != 0 }
                ?: Color.parseColor("#A2BD3B")
            binding.scheduleCategoryIv.imageTintList = ColorStateList.valueOf(colorResId)

            // 3. 편집 모드 및 핀/반복 표시
            if (isEditMode) {
                binding.scheduleCheckbox.visibility = View.VISIBLE
                binding.scheduleCheckbox.isChecked = selectedIds.contains(schedule.id)
                binding.schedulePinnedIv.visibility = View.GONE
            } else {
                binding.scheduleCheckbox.visibility = View.GONE
                binding.schedulePinnedIv.visibility = if (schedule.isPinned) View.VISIBLE else View.GONE
            }

            // 반복 아이콘 처리
            if (!schedule.repeatRule.isNullOrEmpty()) {
                binding.scheduleRepeatIv.visibility = View.VISIBLE
                binding.scheduleRepeatTv.visibility = View.VISIBLE
                binding.scheduleRepeatTv.text = "반복 설정됨"
            } else {
                binding.scheduleRepeatIv.visibility = View.GONE
                binding.scheduleRepeatTv.visibility = View.GONE
            }

            // 4. 경로(ROUTE) vs 일반 일정 분기 처리 ⭐핵심 추가 부분
            val routeDetail = routeInfoMap[schedule.id]

            if (schedule.type == "ROUTE") {
                binding.scheduleNormalLocationLl.visibility = View.GONE
                binding.scheduleRouteLocationLl.visibility = View.VISIBLE

                if (routeDetail != null) {
                    binding.scheduleRouteNameTv.text = "${routeDetail.originName} → ${routeDetail.destName}"

                    val formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm")

                    val startTime = routeDetail.departureTime?.let {
                        java.time.LocalDateTime.parse(it).plusHours(9).format(formatter)
                    } ?: "00:00"

                    val endTime = routeDetail.arrivalTime?.let {
                        java.time.LocalDateTime.parse(it).plusHours(9).format(formatter)
                    } ?: "00:00"

                    binding.scheduleRouteRangeTv.text = "$startTime - $endTime"

                    val totalSeconds = routeDetail.totalTime
                    val hours = totalSeconds / 3600
                    val minutes = (totalSeconds % 3600) / 60
                    binding.scheduleRouteDurationTv.text = "${hours}시간 ${minutes}분"
                } else {
                    binding.scheduleRouteNameTv.text = schedule.location ?: "경로를 불러오는 중..."
                    binding.scheduleRouteRangeTv.text = "${schedule.startTime} - ${schedule.endTime}"
                    binding.scheduleRouteDurationTv.text = "0시간 0분"
                }
            } else {
                binding.scheduleRouteLocationLl.visibility = View.GONE
                if (!schedule.location.isNullOrEmpty()) {
                    binding.scheduleNormalLocationLl.visibility = View.VISIBLE
                    binding.scheduleNormalLocationTv.text = schedule.location
                } else {
                    binding.scheduleNormalLocationLl.visibility = View.GONE
                }
            }

            // 5. 버튼 리스너
            binding.schedulePinIv.setOnClickListener {
                onPinClick(schedule)
                scheduleTouchHelper.closeSwipedMenu(this)
            }
            binding.scheduleEditIv.setOnClickListener {
                onEditClick(schedule)
                scheduleTouchHelper.closeSwipedMenu(this)
            }
            binding.scheduleDeleteIv.setOnClickListener {
                onDeleteClick(schedule)
                scheduleTouchHelper.closeSwipedMenu(this)
            }
        }
    }
}
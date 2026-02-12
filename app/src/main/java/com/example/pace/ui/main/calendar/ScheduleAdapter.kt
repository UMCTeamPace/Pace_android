package com.example.pace.ui.main.calendar

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.example.pace.R
import com.example.pace.data.model.Schedule
import com.example.pace.databinding.ItemDateHeaderBinding
import com.example.pace.databinding.ItemScheduleBinding
import com.example.pace.ui.main.home.DeleteScheduleDialog
import com.example.pace.ui.main.home.ScheduleTouchHelper

class ScheduleAdapter(
    private val context: Context,
    private var items: List<ScheduleListItem>,
    private val onPinClick: (Schedule) -> Unit,
    private val onEditSelect: (Long) -> Unit // 추가: 아이템 선택 시 호출될 콜백
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    lateinit var scheduleTouchHelper: ScheduleTouchHelper
    private var isEditMode = false
    private var selectedIds = setOf<Long>()


    companion object {
        private const val TYPE_DATE_HEADER = 0
        private const val TYPE_SCHEDULE_ITEM = 1
    }
    // 편집 모드 전환 설정
    fun setEditMode(enabled: Boolean) {
        this.isEditMode = enabled
        notifyDataSetChanged()
    }

    // 선택된 ID 목록 갱신
    fun updateSelectedIds(ids: Set<Long>) {
        this.selectedIds = ids
        notifyDataSetChanged()
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
        // 데이터 갱신 시 swipe된 거 초기화
        val viewTop = holder.itemView.findViewById<ConstraintLayout>(R.id.schedule_view_top)
        if(viewTop != null){
            viewTop.translationX = 0f
        }

        when (val item = items[position]) {
            is ScheduleListItem.DateHeader -> (holder as DateHeaderViewHolder).bind(item)
            is ScheduleListItem.ScheduleItem -> (holder as ScheduleItemViewHolder).bind(item, holder)
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

        fun bind(item: ScheduleListItem.ScheduleItem, holder: RecyclerView.ViewHolder) {
            val schedule = item.schedule

            // 편집 모드 여부에 따른 가시성 제어
            if (isEditMode) {
                binding.scheduleCheckbox.visibility = View.VISIBLE
                binding.scheduleCheckbox.isChecked = selectedIds.contains(schedule.id)
                binding.schedulePinIv.visibility = View.GONE // 편집 모드에서는 고정 아이콘 숨김

                // 클릭 시 선택 상태 토글
                binding.root.setOnClickListener { onEditSelect(schedule.id) }
                // 체크박스 클릭 시에도 선택 상태 토글 (중복 호출 방지 위해 체크)
                binding.scheduleCheckbox.setOnClickListener { onEditSelect(schedule.id) }

            } else {
                // 일반 모드일 때
                binding.scheduleCheckbox.visibility = View.GONE // 일반 모드에서는 체크박스 숨김
                binding.schedulePinnedIv.visibility = if (schedule.isPinned) View.VISIBLE else View.GONE // 고정 여부에 따라 아이콘 표시

                binding.root.setOnClickListener { /* TODO: 상세보기 등 기존 로직 */ } // 일반 모드에서 아이템 클릭 리스너
                binding.schedulePinIv.setOnClickListener {
                    onPinClick(schedule)
                    scheduleTouchHelper.closeSwipedMenu(holder)
                }
                binding.scheduleDeleteIv.setOnClickListener {
                    val deleteScheduleDialog = DeleteScheduleDialog(context)
                    deleteScheduleDialog.show()
                }
            }

            // 1. 이름 (Title)
            binding.scheduleTitleTv.text = schedule.title ?: "제목 없음"

            // 2. 색상 설정
            val colorResId = when {
                schedule.eventColor != null && schedule.eventColor != 0 -> schedule.eventColor
                schedule.calendarColor != null && schedule.calendarColor != 0 -> schedule.calendarColor
                else -> android.graphics.Color.parseColor("#A2BD3B") // 기본 색상 (원하는 색상으로 변경 가능)
            }
            binding.scheduleCategoryIv.imageTintList = android.content.res.ColorStateList.valueOf(colorResId)

            // 3. 시간 표시
            if (schedule.isAllDay) {
                binding.scheduleTimeTv.text = "하루 종일"
            } else {
                binding.scheduleTimeTv.text = "${schedule.startTime} - ${schedule.endTime}"
            }

            // 4. 반복 문자열 표시
            if (!schedule.repeatRule.isNullOrEmpty()) {
                binding.scheduleRepeatIv.visibility = View.VISIBLE
                binding.scheduleRepeatTv.visibility = View.VISIBLE
                // TODO: 스케줄 객체에 사람이 읽을 수 있는 반복 문자열 필드가 있다면 그것을 사용.
                // 현재는 rrule 문자열만 있으므로, "반복 설정됨"으로 표시.
                // ScheduleRepeatFragment에서 생성한 "selectedRepeat" 값을 Schedule 객체에 저장해서 사용하는 것을 권장.
                binding.scheduleRepeatTv.text = "반복 설정됨"
            } else {
                binding.scheduleRepeatIv.visibility = View.GONE
                binding.scheduleRepeatTv.visibility = View.GONE
            }

            // 5. 장소 표시
            if (!schedule.location.isNullOrEmpty()) {
                binding.scheduleNormalLocationLl.visibility = View.VISIBLE
                binding.scheduleNormalLocationIv.visibility = View.VISIBLE
                binding.scheduleNormalLocationTv.text = schedule.location
                binding.scheduleRouteLocationLl.visibility = View.GONE // 일반 일정에서는 경로 위치 숨김
            } else {
                binding.scheduleNormalLocationLl.visibility = View.GONE
                binding.scheduleNormalLocationIv.visibility = View.GONE
                binding.scheduleNormalLocationTv.text = "" // 텍스트도 비워둠
                // 경로 일정이 withRoute 플래그를 사용하는 경우를 위해 추가 확인
                binding.scheduleRouteLocationLl.visibility = if (schedule.type == "ROUTE" && schedule.withRoute) View.VISIBLE else View.GONE
            }
        }
    }
}

// Sealed class for the list items
sealed class ScheduleListItem {
    data class DateHeader(val date: String) : ScheduleListItem()
    data class ScheduleItem(val schedule: Schedule) : ScheduleListItem()
}
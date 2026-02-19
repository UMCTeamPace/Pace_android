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
import androidx.recyclerview.widget.RecyclerView
import com.example.pace.R
import com.example.pace.data.model.Schedule
import com.example.pace.data.model.response.RouteInfo
import com.example.pace.databinding.ItemDateHeaderBinding
import com.example.pace.databinding.ItemScheduleBinding
import com.example.pace.ui.main.home.DeleteScheduleDialog
import com.example.pace.ui.main.home.ScheduleTouchHelper

class SearchAdapter(
    private val context: Context,
    private var query: String = "",
    private val onPinClick: (Schedule) -> Unit,       // 핀 클릭
    private val onDeleteClick: (Schedule) -> Unit,    // 삭제 클릭
    private val onEditClick: (Schedule) -> Unit,      // 수정 클릭
    private val onEditSelect: (Long) -> Unit,         // 편집모드 선택
    private var routeInfoMap: Map<Long, RouteInfo> = emptyMap()
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var items = listOf<ScheduleListItem>()
    lateinit var scheduleTouchHelper: ScheduleTouchHelper
    private var selectedIds = setOf<Long>() // 선택된 아이템 ID들을 저장
    companion object {
        private const val TYPE_DATE_HEADER = 0
        private const val TYPE_SCHEDULE_ITEM = 1
    }
    // SearchAdapter.kt 내부 수정
    fun updateItemPinStatus(scheduleId: Long, isPinned: Boolean) {
        // 1. 상태 업데이트
        val updatedItems = items.map { item ->
            if (item is ScheduleListItem.ScheduleItem && item.schedule.id == scheduleId) {
                item.copy(schedule = item.schedule.copy(isPinned = isPinned))
            } else {
                item
            }
        }

        // 2. 날짜 그룹별 재정렬 로직 실행
        val finalItems = mutableListOf<ScheduleListItem>()
        var currentHeader: ScheduleListItem.DateHeader? = null
        val tempDayItems = mutableListOf<ScheduleListItem.ScheduleItem>()

        // 기존 리스트를 순회하며 날짜 그룹별로 다시 정렬
        for (item in updatedItems) {
            when (item) {
                is ScheduleListItem.DateHeader -> {
                    // 이전 날짜 그룹 정렬해서 넣기
                    if (tempDayItems.isNotEmpty()) {
                        finalItems.addAll(tempDayItems.sortedWith(
                            compareBy({ !it.schedule.isPinned }, { !it.schedule.isAllDay }, { it.schedule.startTime })
                        ))
                        tempDayItems.clear()
                    }
                    finalItems.add(item)
                }
                is ScheduleListItem.ScheduleItem -> {
                    tempDayItems.add(item)
                }
            }
        }
        // 마지막 그룹 처리
        if (tempDayItems.isNotEmpty()) {
            finalItems.addAll(tempDayItems.sortedWith(
                compareBy({ !it.schedule.isPinned }, { !it.schedule.isAllDay }, { it.schedule.startTime })
            ))
        }

        this.items = finalItems
        notifyDataSetChanged()
    }
    fun updateSelectedIds(ids: Set<Long>) {
        this.selectedIds = ids
        notifyDataSetChanged() // 체크박스 상태를 새로고침
    }

    fun submitList(newItems: List<ScheduleListItem>) {
        this.items = newItems
        notifyDataSetChanged()
    }

    fun updateRouteInfo(newRouteMap: Map<Long, RouteInfo>) {
        this.routeInfoMap = newRouteMap
        notifyDataSetChanged()
    }

    fun updateQuery(newQuery: String) {
        this.query = newQuery
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is ScheduleListItem.DateHeader -> TYPE_DATE_HEADER
        is ScheduleListItem.ScheduleItem -> TYPE_SCHEDULE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_DATE_HEADER -> DateHeaderViewHolder(ItemDateHeaderBinding.inflate(inflater, parent, false))
            TYPE_SCHEDULE_ITEM -> SearchItemViewHolder(ItemScheduleBinding.inflate(inflater, parent, false))
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        // 스와이프 레이아웃 초기화
        holder.itemView.findViewById<View>(R.id.schedule_view_top)?.translationX = 0f

        when (val item = items[position]) {
            is ScheduleListItem.DateHeader -> (holder as DateHeaderViewHolder).bind(item.date)
            is ScheduleListItem.ScheduleItem -> (holder as SearchItemViewHolder).bind(item.schedule, query, holder)
        }
    }

    override fun getItemCount(): Int = items.size

    inner class DateHeaderViewHolder(private val binding: ItemDateHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(date: String) {
            binding.dateHeaderTv.text = date
        }
    }

    inner class SearchItemViewHolder(private val binding: ItemScheduleBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(schedule: Schedule, query: String, holder: RecyclerView.ViewHolder) {
            // 1. 제목 및 하이라이트 (기존 로직 유지)
            val title = schedule.title ?: "제목 없음"
            if (query.isBlank()) {
                binding.scheduleTitleTv.text = title
            } else {
                val start = title.indexOf(query, ignoreCase = true)
                if (start >= 0) {
                    val spannable = SpannableString(title)
                    spannable.setSpan(
                        ForegroundColorSpan(Color.parseColor("#8BC34A")),
                        start, start + query.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    binding.scheduleTitleTv.text = spannable
                } else {
                    binding.scheduleTitleTv.text = title
                }
            }

            // 2. 기본 색상 설정
            val colorResId = schedule.eventColor ?: schedule.calendarColor ?: Color.parseColor("#A2BD3B")
            binding.scheduleCategoryIv.imageTintList = ColorStateList.valueOf(colorResId)
            binding.scheduleTimeTv.text = if (schedule.isAllDay) "하루 종일" else "${schedule.startTime} - ${schedule.endTime}"
            // 3. [반복 설정 반영]
            if (!schedule.repeatRule.isNullOrEmpty()) {
                binding.scheduleRepeatIv.visibility = View.VISIBLE
                binding.scheduleRepeatTv.visibility = View.VISIBLE
                binding.scheduleRepeatTv.text = "반복 설정됨"
            } else {
                binding.scheduleRepeatIv.visibility = View.GONE
                binding.scheduleRepeatTv.visibility = View.GONE
            }

            // 4. 경로 상세 데이터 바인딩 및 시간 표시 분기
            val routeDetail = routeInfoMap[schedule.id]

            if (schedule.type == "ROUTE") {
                // --- [경로 일정 모드] ---
                binding.scheduleNormalLocationLl.visibility = View.GONE
                binding.scheduleRouteLocationLl.visibility = View.VISIBLE

                if (routeDetail != null) {
                    // A. 출발지 → 도착지 명칭
                    binding.scheduleRouteNameTv.text = "${routeDetail.originName} → ${routeDetail.destName}"

                    // B. 실제 출발-도착 시간 포맷팅 (ISO-8601 -> HH:mm)
                    val formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
                    // 서버 시간이 UTC라면 .plusHours(9)를 사용하세요. 로컬이면 제외합니다.
                    val startTime = try {
                        java.time.LocalDateTime.parse(routeDetail.departureTime).plusHours(9).format(formatter)
                    } catch (e: Exception) { schedule.startTime }

                    val endTime = try {
                        java.time.LocalDateTime.parse(routeDetail.arrivalTime).plusHours(9).format(formatter)
                    } catch (e: Exception) { schedule.endTime }

                    binding.scheduleRouteRangeTv.text = "$startTime - $endTime"  // 경로 전용 시간

                    // C. 소요 시간 계산
                    val totalSeconds = routeDetail.totalTime
                    binding.scheduleRouteDurationTv.text = "${totalSeconds / 3600}시간 ${(totalSeconds % 3600) / 60}분"
                } else {
                    // 데이터를 아직 불러오지 못한 경우
                    binding.scheduleRouteNameTv.text = "경로 정보를 불러오는 중..."
                    binding.scheduleTimeTv.text = "${schedule.startTime} - ${schedule.endTime}"
                    binding.scheduleRouteRangeTv.text = "${schedule.startTime} - ${schedule.endTime}"
                    binding.scheduleRouteDurationTv.text = "계산 중..."
                }
            } else {
                // --- [일반 일정 모드] ---
                binding.scheduleRouteLocationLl.visibility = View.GONE
                binding.scheduleNormalLocationLl.visibility = if (schedule.location.isNullOrEmpty()) View.GONE else View.VISIBLE
                binding.scheduleNormalLocationTv.text = schedule.location ?: ""

                // 일반 시간 표시
                binding.scheduleTimeTv.text = if (schedule.isAllDay) "하루 종일" else "${schedule.startTime} - ${schedule.endTime}"
            }

            // 5. 클릭 및 스와이프 리스너 (기존 유지)
            binding.schedulePinnedIv.visibility = if (schedule.isPinned) View.VISIBLE else View.GONE
            binding.scheduleCheckbox.visibility = View.INVISIBLE

            binding.schedulePinIv.setOnClickListener {
                onPinClick(schedule)
                scheduleTouchHelper.closeSwipedMenu(holder)
            }

            // 수정 버튼 (스와이프 메뉴 내부)
            binding.scheduleEditIv.setOnClickListener {
                onEditClick(schedule)
                scheduleTouchHelper.closeSwipedMenu(holder)
            }

            // 삭제 버튼 (전용 다이얼로그 호출)
            binding.scheduleDeleteIv.setOnClickListener {
                onDeleteClick(schedule) // Fragment에서 정의한 showDeleteDialog가 실행됨
                scheduleTouchHelper.closeSwipedMenu(holder)
            }
        }
    }
}
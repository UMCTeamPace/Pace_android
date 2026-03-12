package com.example.pace.ui.main.home

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.daimajia.swipe.SwipeLayout
import com.daimajia.swipe.adapters.RecyclerSwipeAdapter
import com.example.pace.R
import com.example.pace.data.model.Schedule
import com.example.pace.data.model.response.RouteInfo
import com.example.pace.databinding.ItemScheduleBinding

class ScheduleRVAdapter(
    private var scheduleList: MutableList<Schedule>,
    private val context: Context,
    private val onPinClick: (Schedule) -> Unit,
) : RecyclerSwipeAdapter<ScheduleRVAdapter.ViewHolder>() {

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
        this.routeInfoMap = newRouteMap // 상세 정보 맵 업데이트
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

        // --- 스와이프 구현 ---
        holder.binding.root.showMode = SwipeLayout.ShowMode.LayDown
        holder.binding.root.addDrag(SwipeLayout.DragEdge.Left, holder.binding.scheduleLeftBottomWrapper)
        holder.binding.root.addDrag(SwipeLayout.DragEdge.Right, holder.binding.scheduleRightBottomWrapper)

        mItemManger.bindView(holder.itemView, position)


        // --- 이벤트 리스너 설정 ---
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
    override fun getSwipeLayoutResourceId(p0: Int): Int = R.id.item_schedule


    inner class ViewHolder(val binding: ItemScheduleBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(schedule: Schedule) {
            binding.scheduleCheckbox.visibility = View.GONE
            binding.scheduleTitleTv.text = schedule.title ?: "제목 없음"

            // 1. 색상 설정
            val colorResId = when {
                schedule.eventColor != null && schedule.eventColor != 0 -> schedule.eventColor
                schedule.calendarColor != null && schedule.calendarColor != 0 -> schedule.calendarColor
                else -> Color.parseColor("#A2BD3B")
            }
            binding.scheduleCategoryIv.imageTintList = ColorStateList.valueOf(colorResId)

            // 2. 시간 표시
            binding.scheduleTimeTv.text = if (schedule.isAllDay) "하루 종일" else "${schedule.startTime} - ${schedule.endTime}"

            // 3. 고정 및 반복 표시
            binding.schedulePinnedIv.visibility = if (schedule.isPinned) View.VISIBLE else View.GONE
            if (!schedule.repeatRule.isNullOrEmpty()) {
                binding.scheduleRepeatIv.visibility = View.VISIBLE
                binding.scheduleRepeatTv.visibility = View.VISIBLE
                binding.scheduleRepeatTv.text = "반복 설정됨"
            } else {
                binding.scheduleRepeatIv.visibility = View.GONE
                binding.scheduleRepeatTv.visibility = View.GONE
            }

            val routeDetail = routeInfoMap[schedule.id]

            if (schedule.type == "ROUTE") {
                binding.scheduleNormalLocationLl.visibility = View.GONE
                binding.scheduleRouteLocationLl.visibility = View.VISIBLE

                if (routeDetail != null) {
                    // 1. 위치 정보: 출발지 -> 목적지
                    binding.scheduleRouteNameTv.text = "${routeDetail.originName} → ${routeDetail.destName}"

                    // 1. 시간 계산 (9시간 더하기)
                    val formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm")

                    // 출발 시간 처리
                    val startTime = routeDetail.departureTime?.let {
                        java.time.LocalDateTime.parse(it) // ISO_DATE_TIME 파싱
                            .plusHours(9)                // 9시간 더하기
                            .format(formatter)           // HH:mm 포맷팅
                    } ?: "00:00"

                    // 도착 시간 처리
                    val endTime = routeDetail.arrivalTime?.let {
                        java.time.LocalDateTime.parse(it)
                            .plusHours(9)
                            .format(formatter)
                    } ?: "00:00"

                    binding.scheduleRouteRangeTv.text = "$startTime - $endTime"

                    // 3. 소요 시간 가공 (0시간 00분 형식)
                    val totalSeconds = routeDetail.totalTime
                    val hours = totalSeconds / 3600
                    val minutes = (totalSeconds % 3600) / 60

                    // 항상 "0시간 00분" 형식을 유지하도록 설정
                    val durationFormatted = "${hours}시간 ${minutes}분"
                    binding.scheduleRouteDurationTv.text = durationFormatted

                } else {
                    // 데이터 로딩 중 Placeholder
                    binding.scheduleRouteNameTv.text = schedule.location ?: "경로를 불러오는 중..."
                    binding.scheduleRouteRangeTv.text = "${schedule.startTime} - ${schedule.endTime}"
                    binding.scheduleRouteDurationTv.text = "0시간 0분"
                }
            } else {
                // 일반 일정 처리 (기존과 동일)
                binding.scheduleRouteLocationLl.visibility = View.GONE
                if (!schedule.location.isNullOrEmpty()) {
                    binding.scheduleNormalLocationLl.visibility = View.VISIBLE
                    binding.scheduleNormalLocationTv.text = schedule.location
                } else {
                    binding.scheduleNormalLocationLl.visibility = View.GONE
                }
            }
        }
    }
}
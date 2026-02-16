package com.example.pace.ui.main.home

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.example.pace.data.model.Schedule
import com.example.pace.databinding.ItemScheduleBinding
import java.time.LocalDate
import com.example.pace.R
class ScheduleRVAdapter(
    private var scheduleList: MutableList<Schedule>,
    private val context: Context,
    private val onPinClick: (Schedule) -> Unit,
): RecyclerView.Adapter<ScheduleRVAdapter.ViewHolder>() {
    lateinit var mOnClickListener: MyOnClickListener
    lateinit var scheduleTouchHelper: ScheduleTouchHelper

    interface MyOnClickListener{
        fun showModalCase(scheduleList: List<Schedule>, position: Int)
        fun onEdit(schedule: Schedule)
        fun onDelete(schedule: Schedule)
    }

    fun setMyOnClickListener(myOnClickListener: MyOnClickListener){
        mOnClickListener = myOnClickListener
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateData(newSchedules: List<Schedule>) {
        scheduleList.clear()
        scheduleList.addAll(newSchedules)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val binding = ItemScheduleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        // 데이터 갱신 시 swipe된 거 초기화
        val viewTop = holder.itemView.findViewById<ConstraintLayout>(R.id.schedule_view_top)
        viewTop.translationX = 0f

        val schedule = scheduleList[position]
        holder.bind(schedule)

        holder.binding.schedulePinIv.setOnClickListener {
            holder.binding.schedulePinnedIv.visibility = View.VISIBLE
            onPinClick(schedule)
            scheduleTouchHelper.closeSwipedMenu(holder)
        }

        // 수정 버튼 (이름 유지: mOnClickListener 사용)
        holder.binding.scheduleEditIv.setOnClickListener {
            mOnClickListener.onEdit(schedule)
            scheduleTouchHelper.closeSwipedMenu(holder)
        }

        // 삭제 버튼 (이름 유지: mOnClickListener 사용)
        holder.binding.scheduleDeleteIv.setOnClickListener {
            mOnClickListener.onDelete(schedule)
            scheduleTouchHelper.closeSwipedMenu(holder)
        }

        holder.binding.scheduleViewTop.setOnClickListener {
            // 스와이프 상태가 아닐 때만 모달 띄우도록
            if (viewTop.translationX == 0f) {
                mOnClickListener.showModalCase(scheduleList, position)
            }
        }
    }

    override fun getItemCount(): Int = scheduleList.size

    fun getScheduleAt(position: Int): Schedule {
        return scheduleList[position]
    }

    inner class ViewHolder(val binding: ItemScheduleBinding): RecyclerView.ViewHolder(binding.root){
        fun bind(schedule: Schedule){
            // 체크박스는 HomeFragment에서 사용하지 않으므로 항상 GONE
            binding.scheduleCheckbox.visibility = View.GONE

            // 1. 이름 (Title)
            binding.scheduleTitleTv.text = schedule.title ?: "제목 없음"

            // 2. 색상 설정
            val colorResId = when {
                schedule.eventColor != null && schedule.eventColor != 0 -> schedule.eventColor
                schedule.calendarColor != null && schedule.calendarColor != 0 -> schedule.calendarColor
                else -> android.graphics.Color.parseColor("#A2BD3B") // 기본 색상
            }
            binding.scheduleCategoryIv.imageTintList = android.content.res.ColorStateList.valueOf(colorResId)

            // 3. 시간 표시 (이미 구현되어 있음)
            if (schedule.isAllDay) {
                binding.scheduleTimeTv.text = "하루 종일"
            } else {
                binding.scheduleTimeTv.text = "${schedule.startTime} - ${schedule.endTime}"
            }

            // 4. 반복 문자열 표시
            if (!schedule.repeatRule.isNullOrEmpty()) {
                binding.scheduleRepeatIv.visibility = View.VISIBLE
                binding.scheduleRepeatTv.visibility = View.VISIBLE
                // TODO: Schedule 객체에 사람이 읽을 수 있는 반복 문자열 필드가 있다면 그것을 사용.
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
                binding.scheduleRouteLocationLl.visibility = View.GONE // HomeFragment에서는 경로 위치 숨김
            } else {
                binding.scheduleNormalLocationLl.visibility = View.GONE
                binding.scheduleNormalLocationIv.visibility = View.GONE
                binding.scheduleNormalLocationTv.text = "" // 텍스트도 비워둠
                binding.scheduleRouteLocationLl.visibility = View.GONE // 항상 숨김 (route type이 아니므로)
            }

            // 고정 아이콘 (isPinned 상태에 따라)
            binding.schedulePinnedIv.visibility = if (schedule.isPinned) View.VISIBLE else View.GONE
            binding.scheduleAlertTv.visibility = View.GONE
        }
    }
}
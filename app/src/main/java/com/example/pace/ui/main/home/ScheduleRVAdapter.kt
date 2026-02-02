package com.example.pace.ui.main.home

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.pace.R
import com.example.pace.data.model.Schedule
import com.example.pace.databinding.ItemScheduleBinding

class ScheduleRVAdapter(
    private var scheduleList: MutableList<Schedule>,
    private val context: Context
): RecyclerView.Adapter<ScheduleRVAdapter.ViewHolder>() {
    lateinit var mOnClickListener: MyOnClickListener
    lateinit var scheduleTouchHelper: ScheduleTouchHelper

    interface MyOnClickListener{
        fun showModalCase(scheduleList: List<Schedule>, position: Int)
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
        val schedule = scheduleList[position]
        holder.bind(schedule)

        holder.binding.schedulePinIv.setOnClickListener {
            // 핀 로직 작성하기
            holder.binding.schedulePinnedIv.visibility = View.VISIBLE
        }
        holder.binding.scheduleDeleteIv.setOnClickListener {
            val deleteScheduleDialog = DeleteScheduleDialog(context)
            deleteScheduleDialog.show()
        }

        holder.binding.scheduleViewTop.setOnClickListener {
            if (scheduleTouchHelper.hasSwipedItem()) {
                scheduleTouchHelper.closeSwipedMenu()
            } else {
                mOnClickListener.showModalCase(scheduleList, position)
            }
        }
    }

    override fun getItemCount(): Int = scheduleList.size

    fun getScheduleAt(position: Int): Schedule {
        return scheduleList[position]
    }

    fun showModalCase(scheduleList: List<Schedule>, position: Int){
        mOnClickListener.showModalCase(scheduleList, position)
    }

    inner class ViewHolder(val binding: ItemScheduleBinding): RecyclerView.ViewHolder(binding.root){
        fun bind(schedule: Schedule){
            binding.scheduleTitleTv.text = schedule.title ?: "제목 없음"

            if (schedule.isAllDay) {
                binding.scheduleTimeTv.text = "하루 종일"
            } else {
                binding.scheduleTimeTv.text = "${schedule.startTime} - ${schedule.endTime}"
            }

            if (schedule.location.isNullOrEmpty()) {
                binding.scheduleNormalLocationLl.visibility = View.GONE
            } else {
                binding.scheduleNormalLocationLl.visibility = View.VISIBLE
                binding.scheduleNormalLocationTv.text = schedule.location
            }

            binding.schedulePinnedIv.visibility = if (schedule.isPinned) View.VISIBLE else View.GONE

            binding.scheduleAlertTv.visibility = View.GONE
            binding.scheduleCheckbox.visibility = View.VISIBLE
            binding.scheduleRouteLocationLl.visibility = View.GONE
        }
    }
}
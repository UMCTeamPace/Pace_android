package com.example.pace.ui.main.home

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.DragEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.pace.R
import com.example.pace.databinding.ItemScheduleBinding

class ScheduleRVAdapter(
    private val scheduleList:List<String>,
    private val context: Context
): RecyclerView.Adapter<ScheduleRVAdapter.ViewHolder>() {
    lateinit var mOnClickListener: MyOnClickListener
    lateinit var scheduleTouchHelper: ScheduleTouchHelper

    // 임시 리스트 추후 상태 프로퍼티로 수정
    var swipedList = arrayListOf(false, false, false)

    var isJustClosed = false
    var closedPos = -1

    interface MyOnClickListener{
        fun showModalCase(position: Int)
    }
    fun setMyOnClickListener(myOnClickListener: MyOnClickListener){
        mOnClickListener = myOnClickListener
    }
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val binding = ItemScheduleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(scheduleList[position])
        holder.binding.schedulePinIv.setOnClickListener {
            holder.binding.schedulePinnedIv.visibility = View.VISIBLE
        }
        holder.binding.scheduleDeleteIv.setOnClickListener {
            val deleteScheduleDialog = DeleteScheduleDialog(context)
            deleteScheduleDialog.show()
        }

        @SuppressLint("ClickableViewAccessibility")
        holder.binding.scheduleViewTop.setOnTouchListener { v, event ->
            when(event.action){
                MotionEvent.ACTION_DOWN -> {
                    isJustClosed = false
                }
            }
            false
        }
        holder.binding.scheduleViewTop.setOnClickListener {

            if (swipedList[position]) {
                scheduleTouchHelper.closeSwipedMenu()
                swipedList[position] = false
                isJustClosed = true
                closedPos = position
            } else {
                if(closedPos != position){
                    closedPos = -1
                }else{
                    showModalCase(position)
                    closedPos = -1
                }
            }
        }
    }

    override fun getItemCount(): Int = scheduleList.size

    fun showModalCase(position: Int){
        val modalCaseDialog = ModalCaseDialog(context, scheduleList, position)
        modalCaseDialog.show()
    }

    inner class ViewHolder(val binding: ItemScheduleBinding):RecyclerView.ViewHolder(binding.root){
        fun bind(text:String){
            binding.scheduleCategoryIv.setImageResource(R.drawable.ic_schedule_orange)
            binding.scheduleTitleTv.text = text
            binding.scheduleTimeTv.text = "하루 종일"
            binding.scheduleNormalLocationTv.text = "장소"
            binding.schedulePinnedIv.visibility = View.GONE
            binding.scheduleAlertTv.visibility = View.GONE
            binding.scheduleCheckbox.visibility = View.GONE
            binding.scheduleRouteLocationLl.visibility = View.GONE
        }
    }
}
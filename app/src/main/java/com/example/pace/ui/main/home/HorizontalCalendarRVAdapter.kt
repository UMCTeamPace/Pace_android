package com.example.pace.ui.main.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.pace.databinding.ItemHorizontalCalendarBinding
import java.time.LocalDate

class HorizontalCalendarRVAdapter(
    private var startDate: LocalDate
): RecyclerView.Adapter<HorizontalCalendarRVAdapter.ViewHolder>() {
    private val calendarSize = 1000000
    private var selectedDate = -1
    private val startPos = calendarSize / 2
    lateinit var mItemOnClickListener: MyItemOnClickListener

    interface MyItemOnClickListener{
        fun changeSelectedDate(position:Int)
    }
    fun setMyOnclickListener(myOnclickListener: MyItemOnClickListener){
        mItemOnClickListener = myOnclickListener
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val binding = ItemHorizontalCalendarBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        // 7개만 표시
        binding.root.layoutParams.width = (parent.width / 7)

        return ViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {
        // 가운데 날짜 기준 양옆 날짜 바인딩
        val pastDate = startDate.minusDays((position - startPos).toLong())
        holder.bind(pastDate.dayOfWeek.toString(), pastDate.dayOfMonth.toString())
        val futureDate = startDate.plusDays((position - startPos).toLong())
        holder.bind(futureDate.dayOfWeek.toString(), futureDate.dayOfMonth.toString())

        // 가운데 날짜 선택하기
        holder.binding.root.isSelected = (position == selectedDate)
        // 날짜 누르면 해당 날짜를 선택하고 RV의 가운데로 이동
        holder.binding.root.setOnClickListener {
            changeSelectedDate(position)
            mItemOnClickListener.changeSelectedDate(position)
        }
    }

    override fun getItemCount() = calendarSize
    fun changeSelectedDate(position: Int){
        val pSelectedDate = selectedDate
        selectedDate = position
        notifyItemChanged(pSelectedDate)
        notifyItemChanged(selectedDate)
    }

    inner class ViewHolder(val binding: ItemHorizontalCalendarBinding): RecyclerView.ViewHolder(binding.root){
        fun bind(day: String, date: String){
            val formattedDay = day.substring(0,1) + day.substring(1,3).toLowerCase()
            binding.horizontalCalendarDayTv.text = formattedDay
            binding.horizontalCalendarDateTv.text = date
        }
    }

}
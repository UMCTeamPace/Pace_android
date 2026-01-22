package com.example.pace.ui.main.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.pace.databinding.ItemHorizontalCalendarBinding
import java.time.LocalDate

class HorizontalCalendarRVAdapter(
    private var startDate: LocalDate
): RecyclerView.Adapter<HorizontalCalendarRVAdapter.ViewHolder>() {
    private var selectedDate = -1
    private val startPos = Int.MAX_VALUE / 2
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
        val pastDate = startDate.minusDays((position - startPos).toLong())
        holder.bind(pastDate.dayOfWeek.toString(), pastDate.dayOfMonth.toString())
        val futureDate = startDate.plusDays((position - startPos).toLong())
        holder.bind(futureDate.dayOfWeek.toString(), futureDate.dayOfMonth.toString())
        holder.binding.root.isSelected = (position == selectedDate)
    }

    override fun getItemCount() = Int.MAX_VALUE
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
package com.example.pace.ui.main.home

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.pace.databinding.FragmentHomeBinding
import com.example.pace.ui.add_schedule.AddScheduleActivity
import java.time.LocalDate
import java.util.Locale

class HomeFragment: Fragment() {
    lateinit var binding: FragmentHomeBinding
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentHomeBinding.inflate(inflater, container, false)

        // 일정 추가
        binding.homeAddScheduleIv.setOnClickListener {
            startActivity(Intent(context, AddScheduleActivity::class.java))
        }

        // 하단 캘린더
        val today = LocalDate.now()
        val horizontalCalendarAdapter = HorizontalCalendarRVAdapter(today)
        binding.homeHorizontalCalendarRv.adapter = horizontalCalendarAdapter
        var calendarText = today.year.toString() + "년 " + today.monthValue.toString() + "월"
        binding.homeHorizontalCalendarTv.text = calendarText

        val snapHelper = LinearSnapHelper()
        snapHelper.attachToRecyclerView(binding.homeHorizontalCalendarRv)
        binding.homeHorizontalCalendarRv.post{
            val layoutManager = binding.homeHorizontalCalendarRv.layoutManager as LinearLayoutManager
            val screenWidth = binding.homeHorizontalCalendarRv.width
            val itemWidth = screenWidth / 7
            val offset = (screenWidth / 2) - (itemWidth / 2)
            val centerPos = Int.MAX_VALUE / 2
            layoutManager.scrollToPositionWithOffset(centerPos, offset)
            horizontalCalendarAdapter.changeSelectedDate(centerPos)
        }
        binding.homeHorizontalCalendarRv.addOnScrollListener(object :
            RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    val todayPos = Int.MAX_VALUE / 2
                    val centerView = snapHelper.findSnapView(recyclerView.layoutManager)
                    if (centerView != null) {
                        val position = recyclerView.getChildAdapterPosition(centerView)
                        horizontalCalendarAdapter.changeSelectedDate(position)
                        val centerDate = if(position > todayPos){
                            today.plusDays((position - todayPos).toLong())
                        } else{
                            val diff = (todayPos - position).toLong()
                            today.minusDays(diff)
                        }
                        calendarText = centerDate.year.toString() + "년 " + centerDate.monthValue.toString() + "월"
                        binding.homeHorizontalCalendarTv.text = calendarText
                        // Todo: centerDate에 적힌 일정 가져오기
                    }
                }
            }
        })

        return binding.root
    }
}
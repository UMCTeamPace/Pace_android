package com.example.pace.ui.main.home

import android.content.Intent
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.pace.databinding.FragmentHomeBinding
import com.example.pace.ui.add_schedule.AddScheduleActivity
import java.time.LocalDate

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

        // 일정뷰
        val exampleList = listOf<String>("Example 1", "Example 2", "Example 3")
        // 일정 개수에 따라 뷰 변환하기
        if(exampleList.size == 0){
            binding.homeNoSchedule.visibility = View.VISIBLE
            binding.homeScheduleRv.visibility = View.GONE
        }else{
            binding.homeNoSchedule.visibility = View.GONE
            binding.homeScheduleRv.visibility = View.VISIBLE
        }

        val scheduleAdapter = ScheduleRVAdapter(exampleList, requireContext())
        val scheduleTouchHelper = ScheduleTouchHelper(scheduleAdapter)
        val itemTouchHelper = ItemTouchHelper(scheduleTouchHelper)

        binding.homeScheduleRv.adapter = scheduleAdapter
        scheduleAdapter.setMyOnClickListener(object: ScheduleRVAdapter.MyOnClickListener{
            override fun showModalCase(position: Int) {
                scheduleAdapter.showModalCase(position)
            }
        })
        scheduleAdapter.scheduleTouchHelper = scheduleTouchHelper
        itemTouchHelper.attachToRecyclerView(binding.homeScheduleRv)

        // 하단 캘린더
        val calendarSize = 1000000
        val today = LocalDate.now()
        val layoutManager = binding.homeHorizontalCalendarRv.layoutManager as LinearLayoutManager
        val todayPos = calendarSize / 2
        var calendarText = today.year.toString() + "년 " + today.monthValue.toString() + "월"

        val horizontalCalendarAdapter = HorizontalCalendarRVAdapter(today)
        binding.homeHorizontalCalendarRv.adapter = horizontalCalendarAdapter

        val snapHelper = LinearSnapHelper()
        snapHelper.attachToRecyclerView(binding.homeHorizontalCalendarRv)
        // 오늘 날짜를 RV의 가운데로 이동
        binding.homeHorizontalCalendarRv.post{
            val screenWidth = binding.homeHorizontalCalendarRv.width
            val itemWidth = screenWidth / 7
            val offset = (screenWidth / 2) - (itemWidth / 2)
            layoutManager.scrollToPositionWithOffset(todayPos, offset)
            horizontalCalendarAdapter.changeSelectedDate(todayPos)
        }

        // 날짜 클릭 시 해당 날짜 선택 및 RV의 중앙으로 이동
        binding.homeHorizontalCalendarTv.text = calendarText
        horizontalCalendarAdapter.setMyOnclickListener(object: HorizontalCalendarRVAdapter.MyItemOnClickListener{
            override fun changeSelectedDate(position: Int) {
                val smoothScroller = object: LinearSmoothScroller(binding.homeHorizontalCalendarRv.context){
                    override fun calculateDxToMakeVisible(view: View, snapPreference: Int): Int {
                        val screenCenter = binding.homeHorizontalCalendarRv.width/2
                        val itemCenter = (view.left + view.right)/2
                        return screenCenter - itemCenter
                    }
                    override fun calculateSpeedPerPixel(displayMetrics: DisplayMetrics): Float {
                        return 150f/displayMetrics.densityDpi
                    }
                }
                smoothScroller.targetPosition = position
                binding.homeHorizontalCalendarRv.layoutManager?.startSmoothScroll(smoothScroller)
                horizontalCalendarAdapter.changeSelectedDate(position)
            }
        })

        // 스크롤 후 선택된 날짜 변환
        binding.homeHorizontalCalendarRv.addOnScrollListener(object :
            RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
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
                        Log.d("selected", centerDate.year.toString() + centerDate.monthValue.toString() + centerDate.dayOfMonth.toString())
                        // Todo: centerDate에 적힌 일정 가져오기
                    }
                }
            }
        })

        return binding.root
    }
}
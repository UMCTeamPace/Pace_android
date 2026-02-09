package com.example.pace.ui.main.home

import android.content.Intent
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.pace.data.model.Schedule
import com.example.pace.databinding.FragmentHomeBinding
import com.example.pace.ui.add_schedule.AddScheduleActivity
import com.example.pace.ui.main.MainActivity
import com.example.pace.ui.main.calendar.ScheduleViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import androidx.fragment.app.activityViewModels // 추가 확인
import dagger.hilt.android.AndroidEntryPoint // 1. 추가

@AndroidEntryPoint
class HomeFragment: Fragment() {
    lateinit var binding: FragmentHomeBinding
    private val viewModel: ScheduleViewModel by activityViewModels()
    private lateinit var scheduleAdapter: ScheduleRVAdapter
    private lateinit var scheduleTouchHelper: ScheduleTouchHelper
    private var selectedDate: LocalDate = LocalDate.now()
    private var scheduleMap: Map<LocalDate, List<Schedule>> = emptyMap()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentHomeBinding.inflate(inflater, container, false)

        setupRecyclerView()
        setupCalendar()
        setupObservers()

        binding.homeAddScheduleIv.setOnClickListener {
            startActivity(Intent(context, AddScheduleActivity::class.java))
        }

        return binding.root
    }

    private fun setupRecyclerView() {
        scheduleAdapter = ScheduleRVAdapter(mutableListOf(), requireContext()){ schedule ->
            val updatedSchedule = schedule.copy(isPinned = !schedule.isPinned)
            viewModel.updateSchedule(updatedSchedule)
        }
        scheduleTouchHelper = ScheduleTouchHelper(scheduleAdapter)
        val itemTouchHelper = ItemTouchHelper(scheduleTouchHelper)

        binding.homeScheduleRv.adapter = scheduleAdapter
        scheduleAdapter.setMyOnClickListener(object: ScheduleRVAdapter.MyOnClickListener{
            override fun showModalCase(scheduleList: List<Schedule>, position: Int) {
                val modalCaseDialog = ModalCaseDialog(requireContext(), scheduleList, position, selectedDate)
                modalCaseDialog.show()
            }
        })
        scheduleAdapter.scheduleTouchHelper = scheduleTouchHelper
        itemTouchHelper.attachToRecyclerView(binding.homeScheduleRv)
    }

    private fun setupCalendar() {
        val calendarSize = 1000000
        val today: LocalDate = LocalDate.now()
        val layoutManager = binding.homeHorizontalCalendarRv.layoutManager as LinearLayoutManager
        val todayPos = calendarSize / 2
        var calendarText = today.year.toString() + "년 " + today.monthValue.toString() + "월"

        val horizontalCalendarAdapter = HorizontalCalendarRVAdapter(today)
        binding.homeHorizontalCalendarRv.adapter = horizontalCalendarAdapter

        val snapHelper = LinearSnapHelper()
        snapHelper.attachToRecyclerView(binding.homeHorizontalCalendarRv)

        binding.homeHorizontalCalendarRv.post{
            val screenWidth = binding.homeHorizontalCalendarRv.width
            val itemWidth = screenWidth / 7
            val offset = (screenWidth / 2) - (itemWidth / 2)
            layoutManager.scrollToPositionWithOffset(todayPos, offset)
            horizontalCalendarAdapter.changeSelectedDate(todayPos)
        }

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

                // 날짜가 클릭으로 변경되었을 때도 필터링
                val centerDate = if(position > todayPos){
                    today.plusDays((position - todayPos).toLong())
                } else{
                    val diff = (todayPos - position).toLong()
                    today.minusDays(diff)
                }
                selectedDate = centerDate
                filterAndDisplaySchedules()
            }
        })

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
                        
                        selectedDate = centerDate
                        filterAndDisplaySchedules()
                    }
                }
            }
        })
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // [수정] 가공된 scheduleMap을 관찰합니다.
                viewModel.scheduleMap.collect { map ->
                    scheduleMap = map
                    // --- [로그 추가 시작] ---
                    // 모든 날짜에 들어있는 일정들을 하나의 리스트로 합쳐서 색상 값 출력
                    map.values.flatten().forEach { schedule ->
                        Log.d("ScheduleColor", "제목: ${schedule.title} | 색상: ${schedule.eventColor}")
                    }
                    // --- [로그 추가 끝] ---

                    filterAndDisplaySchedules()
                }
            }
        }
    }

    private fun filterAndDisplaySchedules() {
        // [수정] 복잡한 문자열 포맷팅과 filter 루프 없이 Map에서 즉시 가져옵니다.
        val filteredList = scheduleMap[selectedDate] ?: emptyList()

        // 정렬 로직 추가 (필요 시: 고정 -> 시간순)
        val sortedList = filteredList.sortedWith(
            compareBy(
                { !it.isPinned },
                { !it.isAllDay },
                { it.startTime }
            )
        )

        scheduleAdapter.updateData(sortedList)

        // UI 처리
        if(sortedList.isEmpty()){
            binding.homeNoSchedule.visibility = View.VISIBLE
            binding.homeScheduleRv.visibility = View.GONE
        } else {
            binding.homeNoSchedule.visibility = View.GONE
            binding.homeScheduleRv.visibility = View.VISIBLE
        }
    }
}
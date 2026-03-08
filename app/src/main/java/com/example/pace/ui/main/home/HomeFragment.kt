package com.example.pace.ui.main.home

import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.SharedPreferences
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
import com.example.pace.data.viewmodel.ScheduleViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import androidx.fragment.app.activityViewModels // 추가 확인
import com.example.pace.data.model.response.ScheduleDetailResponse
import dagger.hilt.android.AndroidEntryPoint // 1. 추가

@AndroidEntryPoint
class HomeFragment: Fragment() {
    lateinit var binding: FragmentHomeBinding
    private val viewModel: ScheduleViewModel by activityViewModels()
    private lateinit var scheduleAdapter: ScheduleRVAdapter
    private lateinit var scheduleTouchHelper: ScheduleTouchHelper

    // 선택한 날짜 저장 및 불러오기
    private lateinit var spf: SharedPreferences
    private lateinit var selectedDate: LocalDate
    private var scheduleMap: Map<LocalDate, List<Schedule>> = emptyMap()


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentHomeBinding.inflate(inflater, container, false)
        spf = requireContext().getSharedPreferences("HOME_CALENDAR", MODE_PRIVATE)
        selectedDate = LocalDate.parse(spf.getString("SELECTED_DATE", LocalDate.now().toString()))

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
            // 서버 API를 호출하는 viewModel.updateSchedule 대신
            // 로컬 데이터만 가공하는 함수를 호출하세요.
            Log.d("PinClick", "클릭된 일정: ${schedule.title}, 현재 핀 상태: ${schedule.isPinned}")

            viewModel.togglePinLocally(selectedDate, schedule.id)
            Log.d("PinClick", "클릭된 일정: ${schedule.title}, 나중 핀 상태: ${schedule.isPinned}")
        }
        scheduleTouchHelper = ScheduleTouchHelper(scheduleAdapter)
        val itemTouchHelper = ItemTouchHelper(scheduleTouchHelper)

        binding.homeScheduleRv.adapter = scheduleAdapter
        scheduleAdapter.setMyOnClickListener(object: ScheduleRVAdapter.MyOnClickListener{
            override fun showModalCase(scheduleList: List<Schedule>, position: Int) {
                val modalCaseDialog = ModalCaseDialog(requireContext(), scheduleList, position, selectedDate, viewModel, viewLifecycleOwner)
                modalCaseDialog.show()
            }
            override fun onEdit(schedule: Schedule) {
                val intent = Intent(requireContext(), AddScheduleActivity::class.java).apply {
                    putExtra("isEdit", true)
                    putExtra("SCHEDULE_ID", schedule.id)
                    putExtra("SCHEDULE_TYPE", schedule.type) // ⭐ 타입 명시 (ROUTE 또는 GENERAL)

                    if (schedule.type == "ROUTE") {
                        putExtra("OPEN_ROUTE_TAB", true)
                    }
                }
                startActivity(intent)
            }

            override fun onDelete(schedule: Schedule) {
                // 1. 경로 일정은 항상 단일 일정이므로 바로 삭제 다이얼로그
                if (schedule.type == "ROUTE") {
                    val deleteDialog = DeleteScheduleDialog(requireContext())
                    deleteDialog.setOnConfirmListener {
                        viewModel.deleteSchedule(schedule.id, withRoute = true)
                    }
                    deleteDialog.show()
                }
                // 2. 일반 일정인 경우만 반복 여부 체크
                else {
                    if (!schedule.repeatRule.isNullOrEmpty()) {
                        val repeatDialog = DeleteRepeatScheduleDialog(requireContext())
                        repeatDialog.setOnOptionSelectedListener { option ->
                            when (option) {
                                "ONLY_THIS" -> viewModel.deleteOnlyThisOccurrence(schedule, selectedDate)
                                "ALL" -> viewModel.deleteSchedule(schedule.id, withRoute = false)
                            }
                        }
                        repeatDialog.show()
                    } else {
                        // 일반 단일 일정
                        val deleteDialog = DeleteScheduleDialog(requireContext())
                        deleteDialog.setOnConfirmListener {
                            viewModel.deleteSchedule(schedule.id, withRoute = false)
                        }
                        deleteDialog.show()
                    }
                }
            }

        })
        scheduleAdapter.scheduleTouchHelper = scheduleTouchHelper
        itemTouchHelper.attachToRecyclerView(binding.homeScheduleRv)
    }

    private fun setupCalendar() {
        val calendarSize = 1000000
        val date: LocalDate = selectedDate
        val layoutManager = binding.homeHorizontalCalendarRv.layoutManager as LinearLayoutManager
        val datePos = calendarSize / 2
        var calendarText = date.year.toString() + "년 " + date.monthValue.toString() + "월"

        val horizontalCalendarAdapter = HorizontalCalendarRVAdapter(date)
        binding.homeHorizontalCalendarRv.adapter = horizontalCalendarAdapter

        val snapHelper = LinearSnapHelper()
        snapHelper.attachToRecyclerView(binding.homeHorizontalCalendarRv)

        // 스크롤바 움직이는 애니메이션 해제
        binding.homeHorizontalCalendarRv.itemAnimator = null
        binding.homeHorizontalCalendarRv.post{
            val screenWidth = binding.homeHorizontalCalendarRv.width
            val itemWidth = screenWidth / 7
            val offset = (screenWidth / 2) - (itemWidth / 2)
            layoutManager.scrollToPositionWithOffset(datePos, offset)
            horizontalCalendarAdapter.changeSelectedDate(datePos)
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
                        return 175f/displayMetrics.densityDpi
                    }
                }
                smoothScroller.targetPosition = position
                binding.homeHorizontalCalendarRv.layoutManager?.startSmoothScroll(smoothScroller)
                horizontalCalendarAdapter.changeSelectedDate(position)

                // 날짜가 클릭으로 변경되었을 때도 필터링
                val centerDate = if(position > datePos){
                    date.plusDays((position - datePos).toLong())
                } else{
                    val diff = (datePos - position).toLong()
                    date.minusDays(diff)
                }
                selectedDate = centerDate
                spf.edit().putString("SELECTED_DATE", selectedDate.toString()).apply()
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
                        val centerDate = if(position > datePos){
                            date.plusDays((position - datePos).toLong())
                        } else{
                            val diff = (datePos - position).toLong()
                            date.minusDays(diff)
                        }

                        calendarText = centerDate.year.toString() + "년 " + centerDate.monthValue.toString() + "월"
                        binding.homeHorizontalCalendarTv.text = calendarText
                        
                        selectedDate = centerDate
                        spf.edit().putString("SELECTED_DATE", selectedDate.toString()).apply()
                        filterAndDisplaySchedules()
                    }
                }
            }
        })
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 1. 일정 데이터 관찰
                launch {
                    viewModel.scheduleMap.collect { map ->
                        scheduleMap = map
                        // 경로 일정이 보이면 API 호출 트리거
                        map[selectedDate]?.filter { it.type == "ROUTE" }?.forEach {
                            viewModel.fetchRouteDetail(it.id)
                        }
                        filterAndDisplaySchedules()
                    }
                }

                // 2. 경로 상세 데이터(API 결과) 관찰
                launch {
                    viewModel.routeDetails.collect { _ ->
                        // 상세 데이터가 들어오면 리스트 다시 그리기
                        filterAndDisplaySchedules()
                    }
                }
            }
        }
    }

    private fun filterAndDisplaySchedules() {
        // [수정] 복잡한 문자열 포맷팅과 filter 루프 없이 Map에서 즉시 가져옵니다.
        val filteredList = scheduleMap[selectedDate] ?: emptyList()

        filteredList.forEach { schedule ->
            if (schedule.type == "ROUTE") {
                viewModel.fetchRouteDetail(schedule.id)
            }
        }

        val routeSchedules = filteredList.filter { it.type == "ROUTE" }


        // 정렬 로직 추가 (필요 시: 고정 -> 시간순)
        val sortedList = filteredList.sortedWith(
            compareBy(
                { !it.isPinned },
                { !it.isAllDay },
                { it.startTime }
            )
        )

        scheduleAdapter.updateData(sortedList, viewModel.routeDetails.value)

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
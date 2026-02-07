package com.example.pace.ui.main.calendar

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.StrictMode
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.NumberPicker
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope // 추가
import com.example.pace.R
import com.example.pace.data.model.Schedule // Schedule 모델 import 필요
import com.example.pace.databinding.FragmentCalendarPageBinding
import com.example.pace.ui.main.MainActivity // MainActivity import
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.kizitonwose.calendar.core.*
import com.kizitonwose.calendar.view.*
import kotlinx.coroutines.flow.collectLatest // 추가
import kotlinx.coroutines.launch // 추가
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter // 추가
import java.time.format.TextStyle
import java.util.Locale
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import java.time.LocalTime
class CalendarPageFragment: Fragment() {
    private var _binding: FragmentCalendarPageBinding? = null
    private val binding get() = _binding!!
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<FrameLayout>

    // 1. 뷰모델 가져오기 (MainActivity의 공유 뷰모델 사용)
    private val viewModel: ScheduleViewModel by lazy {
        (requireActivity() as MainActivity).getSharedViewModel()
    }

    // 2. 캘린더에 표시할 데이터를 담을 Map (날짜 -> 일정 리스트)
    private var events = mapOf<LocalDate, List<Schedule>>()

    private var selectedMonth: YearMonth = YearMonth.now()
    private var selectedDate: LocalDate? = null
    private val today = LocalDate.now()

    private lateinit var dailyScheduleAdapter: ScheduleAdapter
    private var cachedSchedules: List<Schedule> = emptyList() // 데이터 캐싱용

    private var headerHeight = 0
    private var weekViewHeight = 0
    private var containerHeight = 0


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalendarPageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .penaltyFlashScreen()
                .build()
        )

        bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheet)

        val currentMonth = YearMonth.now()
        selectedMonth = currentMonth
        selectedDate = today

        setupCalendarLayout()     // 가독성을 위해 기존 레이아웃 계산 코드 분리 (아래 정의)
        setupBottomSheet()        // 가독성을 위해 기존 바텀시트 설정 코드 분리 (아래 정의)
        setupMonthYearPicker()    // 기존 피커 버튼 리스너
        // 리사이클러뷰 및 어댑터 설정 (이 함수가 구현되어 있어야 함)
        setupBottomSheetRecyclerView()

        // 데이터 관찰 시작
        observeSchedules()

        // [추가] 앱 시작 시 오늘 날짜의 리스트를 미리 불러옴
        updateBottomSheetList(today)
        class DayViewContainer(view: View) : ViewContainer(view) {
            val rootLayout: ConstraintLayout = view.findViewById(R.id.root_layout)
            val textView: TextView = view.findViewById(R.id.calendarDayText)
            lateinit var date: LocalDate // day 대신 date만 가짐

            init {
                rootLayout.setOnClickListener {
                    selectDate(date) // 위에서 만든 통합 함수 호출
                }
            }
        }

        // 월간 바인더
        binding.calendarView.dayBinder = object : MonthDayBinder<DayViewContainer> {
            override fun create(view: View) = DayViewContainer(view)
            override fun bind(container: DayViewContainer, day: CalendarDay) {
                container.date = day.date
                val isCurrentMonth = day.position == DayPosition.MonthDate
                updateDayUI(container.textView, container.rootLayout, day.date, isCurrentMonth)
            }
        }

// 주간 바인더 (isCurrentMonth를 항상 true로 전달하여 비활성화를 막음)
        binding.weekCalendarView.dayBinder = object : WeekDayBinder<DayViewContainer> {
            override fun create(view: View) = DayViewContainer(view)
            override fun bind(container: DayViewContainer, day: WeekDay) {
                container.date = day.date
                updateDayUI(container.textView, container.rootLayout, day.date, true)
            }
        }


        binding.calendarView.monthScrollListener = { month ->
            selectedMonth = month.yearMonth
            updateTitle()
            // 월간을 넘기면 주간 캘린더도 해당 월의 1일로 이동
            binding.weekCalendarView.scrollToWeek(month.yearMonth.atDay(1))
        }

        binding.weekCalendarView.weekScrollListener = { week ->
            val firstDate = week.days.first().date
            selectedMonth = YearMonth.from(firstDate)
            updateTitle()
            // 주간을 넘기면 월간 캘린더도 해당 월로 이동
            binding.calendarView.scrollToMonth(selectedMonth)
        }


        // ... 기존 캘린더 setup 및 스크롤 리스너 코드 유지 ...
        val firstMonth = currentMonth.minusMonths(100)
        val lastMonth = currentMonth.plusMonths(100)
        val firstDayOfWeek = DayOfWeek.SUNDAY

        binding.calendarView.setup(firstMonth, lastMonth, firstDayOfWeek)
        binding.calendarView.outDateStyle = OutDateStyle.EndOfRow
        binding.calendarView.scrollToMonth(currentMonth)

        binding.weekCalendarView.setup(today.minusWeeks(52), today.plusWeeks(52), firstDayOfWeek)
        binding.weekCalendarView.scrollToWeek(selectedDate ?: today)

        binding.calendarView.monthScrollListener = {
            selectedMonth = it.yearMonth
            updateTitle()
        }

        binding.weekCalendarView.weekScrollListener = { week ->
            val days = week.days
            val thisMonthDays = days.filter { YearMonth.from(it.date) == selectedMonth }
            val targetDay = if (thisMonthDays.isNotEmpty()) thisMonthDays.first() else days.first()
            selectedMonth = YearMonth.from(targetDay.date)
            updateTitle()
        }

        // 요일 헤더 설정
        val daysOfWeek = daysOfWeek(firstDayOfWeek)
        binding.legendLayout.root.children.forEachIndexed { index, view ->
            val tv = view as TextView
            tv.text = daysOfWeek[index].getDisplayName(TextStyle.SHORT, Locale.getDefault())
            tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
            when (daysOfWeek[index]) {
                DayOfWeek.SUNDAY -> tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.semantic_error))
                DayOfWeek.SATURDAY -> tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.semantic_success))
                else -> {}
            }
        }
        updateTitle()
    }

    // 5. 뷰모델 데이터 관찰 함수 구현
    private fun observeSchedules() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.allSchedules.collectLatest { schedules ->
                // 1. 백그라운드 스레드에서 무거운 작업 처리
                val (groupedEvents, cachedList) = withContext(Dispatchers.Default) {
                    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
                    val grouped = schedules.groupBy { schedule ->
                        try {
                            // DB의 날짜 문자열 앞 10자리만 잘라서 파싱 (시간 포함 대비)
                            val datePart = schedule.startDate.substring(0, 10)
                            LocalDate.parse(datePart, formatter)
                        } catch (e: Exception) {
                            null
                        }
                    }.filterKeys { it != null } as Map<LocalDate, List<Schedule>>

                    Pair(grouped, schedules)
                }

                // 2. 결과 반영 (Main 스레드)
                events = groupedEvents
                cachedSchedules = cachedList

                // 로그 추가: 데이터 로딩 확인
                android.util.Log.d("CalendarDebug", "Total schedules loaded: ${schedules.size}")
                if (schedules.isNotEmpty()) {
                    android.util.Log.d("CalendarDebug", "First schedule startDate raw: '${schedules[0].startDate}'")
                }

                binding.calendarView.notifyCalendarChanged()
                binding.weekCalendarView.notifyCalendarChanged()

                selectedDate?.let { updateBottomSheetList(it) }
            }
        }
    }

    private fun setupCalendarLayout() {
        binding.calendarContainer.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                binding.calendarContainer.viewTreeObserver.removeOnGlobalLayoutListener(this)
                containerHeight = binding.calendarContainer.height
                headerHeight = binding.headerContainer.height

                val collapsedCalendarHeight = containerHeight - headerHeight - bottomSheetBehavior.peekHeight
                if (collapsedCalendarHeight > 0) {
                    weekViewHeight = collapsedCalendarHeight / 5
                    binding.weekCalendarView.layoutParams.height = weekViewHeight
                }
                bottomSheetBehavior.expandedOffset = headerHeight + weekViewHeight

                val targetHeight = containerHeight - headerHeight
                if (targetHeight > 0 && binding.calendarView.layoutParams.height != targetHeight) {
                    binding.calendarView.layoutParams.height = targetHeight
                }
            }
        })
    }

    private fun setupBottomSheet() {
        bottomSheetBehavior.apply {
            state = BottomSheetBehavior.STATE_HIDDEN
            peekHeight = 500
            isFitToContents = false
            halfExpandedRatio = 0.0001f
            isHideable = true
        }
        // ... 바텀시트 콜백 로직 (기존 코드 그대로 유지) ...
        var lastBottomSheetState: Int = bottomSheetBehavior.state
        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                // 기존 애니메이션 및 상태 변경 로직 그대로 유지
                when (newState) {
                    BottomSheetBehavior.STATE_HIDDEN -> {
                        binding.weekCalendarView.visibility = View.GONE
                        binding.calendarView.visibility = View.VISIBLE
                        selectedDate?.let { binding.calendarView.scrollToMonth(YearMonth.from(it)) }
                        val targetHeight = containerHeight - headerHeight
                        if (targetHeight > 0 && binding.calendarView.layoutParams.height != targetHeight) {
                            binding.calendarView.layoutParams.height = targetHeight
                            binding.calendarView.requestLayout()
                        }
                    }
                    BottomSheetBehavior.STATE_COLLAPSED -> {
                        val calendar = binding.calendarView
                        val weekCalendar = binding.weekCalendarView
                        val collapsedHeight = containerHeight - headerHeight - bottomSheetBehavior.peekHeight

                        if (lastBottomSheetState == BottomSheetBehavior.STATE_EXPANDED) {
                            calendar.visibility = View.VISIBLE
                            val animator = ValueAnimator.ofInt(weekViewHeight, collapsedHeight).apply {
                                duration = 250
                                interpolator = DecelerateInterpolator()
                                addUpdateListener { animation ->
                                    calendar.layoutParams.height = animation.animatedValue as Int
                                    calendar.requestLayout()
                                }
                                addListener(object : AnimatorListenerAdapter() {
                                    override fun onAnimationEnd(animation: Animator) {
                                        weekCalendar.visibility = View.GONE
                                        selectedDate?.let { binding.calendarView.scrollToMonth(YearMonth.from(it)) }
                                    }
                                })
                            }
                            animator.start()
                        } else {
                            weekCalendar.visibility = View.GONE
                            calendar.visibility = View.VISIBLE
                            selectedDate?.let { binding.calendarView.scrollToMonth(YearMonth.from(it)) }
                            if (collapsedHeight > 0 && calendar.layoutParams.height != collapsedHeight) {
                                calendar.layoutParams.height = collapsedHeight
                                calendar.requestLayout()
                            }
                        }
                    }
                    BottomSheetBehavior.STATE_EXPANDED -> {
                        if (weekViewHeight > 0) {
                            val calendar = binding.calendarView
                            val weekCalendar = binding.weekCalendarView

                            val animator = ValueAnimator.ofInt(calendar.height, weekViewHeight).apply {
                                duration = 250
                                interpolator = DecelerateInterpolator()
                                addUpdateListener { animation ->
                                    val layoutParams = calendar.layoutParams
                                    layoutParams.height = animation.animatedValue as Int
                                    calendar.layoutParams = layoutParams
                                }
                                addListener(object : AnimatorListenerAdapter() {
                                    override fun onAnimationEnd(animation: Animator) {
                                        calendar.visibility = View.GONE
                                        weekCalendar.visibility = View.VISIBLE
                                        weekCalendar.scrollToWeek(selectedDate ?: today)
                                    }
                                })
                            }
                            animator.start()
                        } else {
                            binding.calendarView.visibility = View.GONE
                            binding.weekCalendarView.visibility = View.VISIBLE
                            binding.weekCalendarView.scrollToWeek(selectedDate ?: today)
                        }
                    }
                }
                lastBottomSheetState = newState
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                // 기존 슬라이드 로직 유지
                if (slideOffset <= 0) {
                    val calendarView = binding.calendarView
                    val peekHeight = bottomSheetBehavior.peekHeight

                    if (containerHeight == 0 || headerHeight == 0) return

                    val hiddenHeight = containerHeight - headerHeight
                    val collapsedHeight = containerHeight - headerHeight - peekHeight

                    if (collapsedHeight > 0 && hiddenHeight > 0) {
                        val newHeight = (collapsedHeight * (1 + slideOffset) + hiddenHeight * (-slideOffset)).toInt()
                        if (newHeight > 0) {
                            calendarView.layoutParams.height = newHeight
                            calendarView.requestLayout()
                        }
                    }
                }
            }
        })
    }

    private fun setupMonthYearPicker() {
        binding.calendarNumberPickerBtnIv.setOnClickListener {
            showMonthYearPicker()
        }
    }

    private fun showMonthYearPicker() {
        // ... 기존 다이얼로그 로직 유지 ...
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_month_year_picker, null)
        val yearPicker = dialogView.findViewById<NumberPicker>(R.id.picker_year)
        val monthPicker = dialogView.findViewById<NumberPicker>(R.id.picker_month)
        val btnCancel = dialogView.findViewById<Button>(R.id.btn_cancel)
        val btnConfirm = dialogView.findViewById<Button>(R.id.btn_confirm)

        yearPicker.minValue = 1900
        yearPicker.maxValue = 2100
        yearPicker.value = selectedMonth.year

        monthPicker.minValue = 1
        monthPicker.maxValue = 12
        monthPicker.value = selectedMonth.monthValue

        val builder = AlertDialog.Builder(requireContext())
        builder.setView(dialogView)

        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        btnConfirm.setOnClickListener {
            val year = yearPicker.value
            val month = monthPicker.value
            val newMonth = YearMonth.of(year, month)
            selectedMonth = newMonth
            selectedDate = null
            binding.calendarView.scrollToMonth(newMonth)
            binding.weekCalendarView.scrollToWeek(LocalDate.of(year, month, 1))
            updateTitle()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun selectDate(date: LocalDate) {
        if (selectedDate == date) {
            // [복구] 같은 날짜를 다시 클릭했을 때 바텀시트 토글
            bottomSheetBehavior.state = if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN) {
                BottomSheetBehavior.STATE_COLLAPSED
            } else {
                BottomSheetBehavior.STATE_HIDDEN
            }
        } else {
            // 새로운 날짜 선택 시
            val oldDate = selectedDate
            selectedDate = date

            // 1. 양쪽 UI 갱신 (동그라미 이동)
            binding.calendarView.notifyDateChanged(date)
            oldDate?.let { binding.calendarView.notifyDateChanged(it) }
            binding.weekCalendarView.notifyDateChanged(date)
            oldDate?.let { binding.weekCalendarView.notifyDateChanged(it) }

            // 2. [추가] 리스트 업데이트 함수 호출
            updateBottomSheetList(date)

            // 3. 스크롤 동기화
            if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
                binding.calendarView.scrollToMonth(YearMonth.from(date))
            } else {
                binding.weekCalendarView.scrollToWeek(date)
            }

            // 4. 날짜가 바뀌면 바텀시트를 항상 보여줌
            if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }
        }
    }

    private fun updateBottomSheetList(date: LocalDate) {
        val dateString = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val headerFormat = DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)", Locale.KOREAN)
        binding.root.findViewById<TextView>(R.id.tv_selected_date)?.text = date.format(headerFormat)

        viewLifecycleOwner.lifecycleScope.launch {
            val adapterItems = withContext(Dispatchers.Default) {
                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

                cachedSchedules.filter { schedule ->
                    try {
                        val start = LocalDate.parse(schedule.startDate.substring(0, 10), formatter)
                        var end = LocalDate.parse(schedule.endDate.substring(0, 10), formatter)

                        // [추가] 하루 종일 일정인데 종료일이 시작일보다 늦다면, 종료일에서 하루를 뺌
                        // (Calendar Provider의 00:00 종료 특성 처리)
                        if (schedule.isAllDay && end.isAfter(start)) {
                            end = end.minusDays(1)
                        }

                        // 기간 내 포함 여부 확인
                        val isWithinRange = !date.isBefore(start) && !date.isAfter(end)

                        // 반복 일정 체크
                        val isRecurring = if (!schedule.repeatRule.isNullOrEmpty()) {
                            isDateInRecurrence(date, start, schedule.repeatRule)
                        } else false

                        isWithinRange || isRecurring
                    } catch (e: Exception) {
                        false
                    }
                }.sortedWith(
                    compareBy<Schedule>(
                        { !it.isPinned },   // 1순위: 핀 고정 여부 (고정된 게 위로)
                        { !it.isAllDay },   // 2순위: 하루 종일 여부 (하루 종일이 시간 일정보다 위로)
                        { it.startTime } )  // 3순위: 시작 시간 순
                ).map { ScheduleListItem.ScheduleItem(it) }
            }
            // UI 반영
            if (::dailyScheduleAdapter.isInitialized) {
                dailyScheduleAdapter.updateData(adapterItems)
            }

            val emptyView = binding.root.findViewById<TextView>(R.id.tv_empty_state)
            val recyclerView = binding.root.findViewById<RecyclerView>(R.id.rv_daily_schedule)

            if (adapterItems.isEmpty()) {
                emptyView?.visibility = View.VISIBLE
                recyclerView?.visibility = View.INVISIBLE
                android.util.Log.w("CalendarDebug", "No schedules found for: $dateString")
            } else {
                emptyView?.visibility = View.GONE
                recyclerView?.visibility = View.VISIBLE
                android.util.Log.i("CalendarDebug", "Found ${adapterItems.size} schedules for: $dateString")
            }
        }
    }

    private fun isDateInRecurrence(targetDate: LocalDate, startDate: LocalDate, rRule: String): Boolean {
        // 시작일 이전이면 반복될 수 없음
        if (targetDate.isBefore(startDate)) return false

        return when {
            rRule.contains("FREQ=DAILY") -> true
            rRule.contains("FREQ=WEEKLY") -> {
                // 요일이 같으면 반복 (더 정교하려면 BYDAY 파싱 필요)
                targetDate.dayOfWeek == startDate.dayOfWeek
            }
            rRule.contains("FREQ=MONTHLY") -> {
                // 날짜(일)가 같으면 반복
                targetDate.dayOfMonth == startDate.dayOfMonth
            }
            rRule.contains("FREQ=YEARLY") -> {
                targetDate.month == startDate.month && targetDate.dayOfMonth == startDate.dayOfMonth
            }
            else -> false
        }
    }

    // UI를 그리는 공통 로직 (따로 빼두면 편합니다)
    private fun updateDayUI(textView: TextView, root: View, date: LocalDate, isActive: Boolean) {
        textView.text = date.dayOfMonth.toString()

        when {
            date == selectedDate && date == today -> {
                root.setBackgroundResource(R.drawable.bg_selected_day_outline)
                textView.setTextColor(Color.WHITE)
                textView.setBackgroundResource(R.drawable.drawable_circle_green)
            }
            date == selectedDate -> {
                root.setBackgroundResource(R.drawable.bg_selected_day_outline)
                textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.schedule_18))
                textView.setBackgroundResource(R.drawable.drawable_circle_white)
            }
            date == today -> {
                textView.setTextColor(Color.WHITE)
                textView.setBackgroundResource(R.drawable.drawable_circle_green)
                root.background = null
            }
            else -> {
                root.background = null
                textView.background = null
                textView.setTextColor(
                    if (isActive) ContextCompat.getColor(requireContext(), R.color.text_primary)
                    else ContextCompat.getColor(requireContext(), R.color.gray_400)
                )
            }
        }
    }
    private fun setupBottomSheetRecyclerView() {
        // 바텀시트 내부에 있는 RecyclerView ID를 확인하세요 (rv_daily_schedule 가정)
        val recyclerView = binding.root.findViewById<RecyclerView>(R.id.rv_daily_schedule)

        dailyScheduleAdapter = ScheduleAdapter(emptyList()) { schedule ->
            // 아이템 클릭 시 핀(고정) 토글 로직
            val updatedSchedule = schedule.copy(isPinned = !schedule.isPinned)
            viewModel.updateSchedule(updatedSchedule)
        }

        recyclerView?.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = dailyScheduleAdapter
        }
    }
    private fun getFormattedTimeRange(schedule: Schedule): String {
        val dateUpdateFormatter = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN)
        val timeFormatter = DateTimeFormatter.ofPattern("a hh:mm", Locale.KOREAN)

        return try {
            // 시작일과 종료일이 다른 '기간 일정'인 경우
            if (schedule.startDate != schedule.endDate) {
                // "2월 9일 오전 08:00 - 2월 11일 오전 09:00" 형식
                val startDateTime = "${LocalDate.parse(schedule.startDate).format(dateUpdateFormatter)} ${LocalTime.parse(schedule.startTime).format(timeFormatter)}"
                val endDateTime = "${LocalDate.parse(schedule.endDate).format(dateUpdateFormatter)} ${LocalTime.parse(schedule.endTime).format(timeFormatter)}"
                "$startDateTime - $endDateTime"
            } else {
                // 같은 날인 경우 기존처럼 시간만 표시
                "${LocalTime.parse(schedule.startTime).format(timeFormatter)} - ${LocalTime.parse(schedule.endTime).format(timeFormatter)}"
            }
        } catch (e: Exception) {
            "${schedule.startTime} - ${schedule.endTime}"
        }
    }
    private fun updateTitle() {
        binding.calendarNumberPickerTv.text = "${selectedMonth.year}년 ${selectedMonth.monthValue}월"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
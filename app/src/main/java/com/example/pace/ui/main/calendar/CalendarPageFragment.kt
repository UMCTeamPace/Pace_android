package com.example.pace.ui.main.calendar

import DailyPageAdapter
import android.R.attr.firstDayOfWeek
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
import androidx.viewpager2.widget.ViewPager2
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

    private lateinit var dailyPageAdapter: DailyPageAdapter
    private var isProgrammaticScroll = false // 캘린더 클릭 vs 스와이프 구분용
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
                .permitDiskReads()
                .build()
        )

        bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheet)

        val currentMonth = YearMonth.now()
        val firstDayOfWeek = DayOfWeek.SUNDAY
        selectedMonth = currentMonth
        selectedDate = today
        viewModel.setSelectedDate(today) // 초기값 세팅

        setupCalendarLayout()
        setupBottomSheet()
        setupMonthYearPicker()
        setupViewPager()

        class DayViewContainer(view: View) : ViewContainer(view) {
            val rootLayout: ConstraintLayout = view.findViewById(R.id.root_layout)
            val textView: TextView = view.findViewById(R.id.calendarDayText)
            lateinit var date: LocalDate
            init {
                rootLayout.setOnClickListener { selectDate(date) }
            }
        }

        // 월간 바인더
        binding.calendarView.dayBinder = object : MonthDayBinder<DayViewContainer> {
            override fun create(view: View) = DayViewContainer(view)
            override fun bind(container: DayViewContainer, day: CalendarDay) {
                container.date = day.date
                // [수정] 현재 달의 날짜(MonthDate)일 때만 '활성화' 상태로 UI 업데이트
                val isCurrentMonth = day.position == DayPosition.MonthDate
                updateDayUI(container.textView, container.rootLayout, day.date, isCurrentMonth)
            }
        }

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

            if (!isProgrammaticScroll) {
                val currentSelected = selectedDate ?: today

                // [수정 핵심] 이동하려는 월이 현재 이미 선택된 날짜의 월과 같은지 확인
                val isAlreadyInMonth = YearMonth.from(currentSelected) == month.yearMonth

                // 초기 진입(오늘 날짜 포함 월)이거나, 주간에서 이미 해당 월의 날짜를 선택했다면 유지
                val targetDate = if (isAlreadyInMonth) {
                    currentSelected
                } else {
                    // 아예 다른 달로 스크롤해서 넘어갈 때만 그 달의 1일을 선택
                    month.yearMonth.atDay(1)
                }

                selectedMonth = month.yearMonth
                updateTitle()

                // 선택된 날짜가 타겟과 다를 때만 업데이트 수행
                if (selectedDate != targetDate) {
                    selectDate(targetDate, scrollToPager = true, fromScroll = true)
                }
            }
        }

        // 주간 캘린더 스크롤 리스너 수정
        binding.weekCalendarView.weekScrollListener = { week ->

            val weekFirstDate = week.days.first().date
            selectedMonth = YearMonth.from(weekFirstDate)
            updateTitle()

            if (!isProgrammaticScroll) {
                // 현재 선택된 날짜가 있다면 그 요일을 유지, 없으면 그 주의 첫날
                val currentSelected = selectedDate ?: today
                val weekFirstDate = week.days.first().date

                // 스크롤된 주의 시작일로부터 기존 요일만큼 떨어진 날짜 계산
                // (주의: 기존 요일의 날짜가 해당 주 범위 내에 있는지 확인)
                val targetDate = weekFirstDate.plusDays(currentSelected.dayOfWeek.value.toLong() % 7)

                selectedMonth = YearMonth.from(targetDate)
                updateTitle()

                if (selectedDate != targetDate) {
                    selectDate(targetDate, scrollToPager = true, fromScroll = true)
                }
            }
        }

        binding.calendarView.setup(currentMonth.minusYears(10), currentMonth.plusYears(10), firstDayOfWeek)
        binding.calendarView.scrollToMonth(currentMonth)

        binding.weekCalendarView.setup(today.minusWeeks(520), today.plusWeeks(520), firstDayOfWeek)
        binding.weekCalendarView.scrollToWeek(selectedDate ?: today)


        observeSchedules()

        // [추가] 요일 헤더 설정 함수 호출
        setupDayHeaders(firstDayOfWeek)

        binding.btnReturnToToday.setOnClickListener {
            val targetMonth = YearMonth.now()

            // 1. 프로그래밍적 이동임을 선언 (리스너의 '1일 선택' 로직 방지)
            isProgrammaticScroll = true

            // 2. 날짜 먼저 선택 (데이터 및 텍스트 업데이트)
            selectDate(today, scrollToPager = true, fromScroll = false)

            // 3. 캘린더 이동
            val currentMonth = selectedMonth
            val monthDiff = java.time.temporal.ChronoUnit.MONTHS.between(currentMonth, targetMonth)

            if (Math.abs(monthDiff) > 1) {
                val intermediateMonth = if (monthDiff > 0) targetMonth.minusMonths(1) else targetMonth.plusMonths(1)
                binding.calendarView.scrollToMonth(intermediateMonth)
                binding.calendarView.smoothScrollToMonth(targetMonth)
            } else {
                binding.calendarView.smoothScrollToMonth(targetMonth)
            }

            // 주간 캘린더도 오늘로
            binding.weekCalendarView.scrollToWeek(today)

            // 4. 이동이 완료된 후 리스너 해제 (핸들러를 사용해 안전하게 처리)
            binding.calendarView.postDelayed({
                isProgrammaticScroll = false
            }, 500) // 스크롤 애니메이션 시간만큼 충분히 줌
        }

        // 초기 날짜 텍스트 설정
        updateSelectedDateText(today)
        binding.btnReturnToToday.visibility = if (selectedDate == today) View.GONE else View.VISIBLE
    }

    // 헬퍼 함수: 상단 "202X년 X월 X일" 텍스트 업데이트
    private fun updateSelectedDateText(date: LocalDate) {
        val headerFormat = DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)", Locale.KOREAN)
        binding.root.findViewById<TextView>(R.id.tv_selected_date)?.text = date.format(headerFormat)
    }


    // [추가] 요일 헤더(일~토) 설정 함수
    private fun setupDayHeaders(firstDayOfWeek: DayOfWeek) {
        val daysOfWeek = daysOfWeek(firstDayOfWeek)
        binding.legendLayout.root.children.forEachIndexed { index, view ->
            val tv = view as TextView
            tv.text = daysOfWeek[index].getDisplayName(TextStyle.SHORT, Locale.getDefault())
            when (daysOfWeek[index]) {
                DayOfWeek.SUNDAY -> tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.semantic_error))
                DayOfWeek.SATURDAY -> tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.semantic_success))
                else -> tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
            }
        }
    }


    // 5. 뷰모델 데이터 관찰 함수 구현
    private fun observeSchedules() {
        viewLifecycleOwner.lifecycleScope.launch {
            // [수정] viewModel.allSchedules 대신 viewModel.scheduleMap을 관찰합니다.
            viewModel.scheduleMap.collectLatest { groupedMap ->
                // 뷰모델에서 이미 LocalDate 키로 그룹화된 데이터를 주므로 바로 할당합니다.
                events = groupedMap

                // 어댑터에 데이터 전달
                if (::dailyPageAdapter.isInitialized) {
                    dailyPageAdapter.updateEvents(events)
                }

                // 캘린더 새로고침
                binding.calendarView.notifyCalendarChanged()
                binding.weekCalendarView.notifyCalendarChanged()
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
                        // 현재 선택된 날짜가 있는 달로 월간 캘린더 이동(주간에서 스크롤만 하다가 월간으로 전환시 오류발생 보완)
                        val targetMonth = selectedDate?.let { YearMonth.from(it) } ?: selectedMonth
                        binding.calendarView.scrollToMonth(targetMonth)
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
                                        binding.calendarView.scrollToMonth(selectedMonth)
                                    }
                                })
                            }
                            animator.start()
                        } else {
                            weekCalendar.visibility = View.GONE
                            calendar.visibility = View.VISIBLE
                            binding.calendarView.scrollToMonth(selectedMonth)
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
            binding.calendarView.scrollToMonth(newMonth)
            binding.weekCalendarView.scrollToWeek(LocalDate.of(year, month, 1))
            updateTitle()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun toggleTodayButton(show: Boolean) {
        val btn = binding.btnReturnToToday

        if (show) {
            if (btn.visibility != View.VISIBLE) {
                btn.visibility = View.VISIBLE
                btn.animate()
                    .alpha(1f)
                    .setDuration(300)
                    .setListener(null) // 기존에 설정된 리스너가 있다면 초기화
                    .start()
            }
        } else {
            if (btn.visibility == View.VISIBLE) {
                btn.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction {
                        btn.visibility = View.GONE
                    }
                    .start()
            }
        }
    }

    // CalendarPageFragment.kt 의 selectDate 수정
    private fun selectDate(date: LocalDate, scrollToPager: Boolean = true, fromScroll: Boolean = false) {
        if (selectedDate == date && scrollToPager && !fromScroll) {
            // 바텀시트 토글 로직 그대로 유지
            bottomSheetBehavior.state = if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN) {
                BottomSheetBehavior.STATE_COLLAPSED
            } else {
                BottomSheetBehavior.STATE_HIDDEN
            }
        } else {
            val oldDate = selectedDate
            selectedDate = date

            // ⭐ [중요!] 공유 뷰모델에 선택된 날짜 업데이트
            viewModel.setSelectedDate(date)

            updateSelectedDateText(date)
            toggleTodayButton(date != today)


            // 날짜 갱신 알림
            binding.calendarView.notifyDateChanged(date)
            oldDate?.let { binding.calendarView.notifyDateChanged(it) }
            binding.weekCalendarView.notifyDateChanged(date)
            oldDate?.let { binding.weekCalendarView.notifyDateChanged(it) }

            if (scrollToPager) {
                isProgrammaticScroll = true // [중요] 리스너 간섭 방지 시작

                val position = dailyPageAdapter.getPosition(date)
                binding.root.findViewById<ViewPager2>(R.id.vp_daily_schedule)
                    .setCurrentItem(position, true)

                if (!fromScroll) {
                    // 이 안에서 scrollToMonth 등이 호출될 때 리스너가 동작하지 않도록 보장
                    binding.calendarView.scrollToMonth(YearMonth.from(date))
                    binding.weekCalendarView.scrollToWeek(date)
                }

                // 약간의 딜레이 뒤에 해제 (스크롤이 완전히 끝날 때까지 보호)
                binding.root.postDelayed({ isProgrammaticScroll = false }, 100)
            }

            if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
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

        // [핵심 수정] isActive가 true일 때만 선택 UI를 그립니다.
        if (isActive && date == selectedDate) {
            if (date == today) {
                root.setBackgroundResource(R.drawable.bg_selected_day_outline)
                textView.setTextColor(Color.WHITE)
                textView.setBackgroundResource(R.drawable.drawable_circle_green)
            } else {
                root.setBackgroundResource(R.drawable.bg_selected_day_outline)
                textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.schedule_18))
                textView.setBackgroundResource(R.drawable.drawable_circle_white)
            }
        } else if (date == today) {
            // 오늘 날짜 표시는 isActive 상관없이 보여줄지 결정 (보통 isActive일 때만 보여주는 게 깔끔합니다)
            if (isActive) {
                textView.setTextColor(Color.WHITE)
                textView.setBackgroundResource(R.drawable.drawable_circle_green)
            } else {
                textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray_400))
                textView.background = null
            }
            root.background = null
        } else {
            root.background = null
            textView.background = null
            textView.setTextColor(
                if (isActive) ContextCompat.getColor(requireContext(), R.color.text_primary)
                else ContextCompat.getColor(requireContext(), R.color.gray_400)
            )
        }
    }

    private fun setupViewPager() {
        dailyPageAdapter = DailyPageAdapter(
            context = requireContext(),
            events = events,
            onScheduleClick = { schedule ->
                val updatedSchedule = schedule.copy(isPinned = !schedule.isPinned)
                viewModel.updateSchedule(updatedSchedule)
            },
            // [추가] 편집 모드 선택 시 동작할 콜백 (캘린더 페이지에선 편집을 안 하므로 빈 값)
            onEditSelect = { id ->
                // 캘린더 페이지에서도 선택 기능을 쓰고 싶다면 viewModel.toggleSelection(id) 호출
            }
        )

        binding.root.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.vp_daily_schedule).apply {
            adapter = dailyPageAdapter
            setCurrentItem(dailyPageAdapter.START_POSITION, false)

            registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)

                    // 1. 캘린더 클릭으로 인한 이동인 경우 여기서 또 캘린더를 움직일 필요 없음
                    if (isProgrammaticScroll) return

                    val newDate = dailyPageAdapter.getDate(position)

                    if (selectedDate != newDate) {
                        // 2. 날짜 선택 처리 (동그라미 표시 등)
                        // scrollToPager = false로 두어 무한 루프(Pager -> Calendar -> Pager) 방지
                        selectDate(newDate, scrollToPager = false)

                        // 3. [핵심] 뷰페이저 스와이프 시 캘린더도 해당 날짜로 이동
                        val newMonth = YearMonth.from(newDate)

                        // 월간 캘린더 부드럽게 이동
                        binding.calendarView.smoothScrollToMonth(newMonth)
                        // 주간 캘린더 부드럽게 이동
                        binding.weekCalendarView.smoothScrollToWeek(newDate)

                        // 4. 상단 타이틀(202X년 X월) 업데이트
                        selectedMonth = newMonth
                        updateTitle()
                    }
                }
            })
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
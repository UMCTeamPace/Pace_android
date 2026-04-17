package com.example.pace.ui.main.calendar

import DailyPageAdapter
import android.R.attr.firstDayOfWeek
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.ActionBar
import android.app.AlertDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.StrictMode
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.pace.R
import com.example.pace.data.model.Schedule
import com.example.pace.databinding.FragmentCalendarPageBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.kizitonwose.calendar.core.*
import com.kizitonwose.calendar.view.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.viewpager2.widget.ViewPager2
import androidx.fragment.app.activityViewModels
import com.example.pace.data.viewmodel.ScheduleViewModel
import com.example.pace.databinding.ItemMonthViewMultipleDaysBinding
import com.example.pace.databinding.ItemMonthViewSingleDayBinding
import com.example.pace.databinding.ItemWeekViewBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.roundToInt

@AndroidEntryPoint
class CalendarPageFragment: Fragment() {
    /*
     * 이 화면은 일정 탭의 캘린더 페이지 화면임
     * 달력 영역은 위쪽 제목 높이를 뺀 나머지 높이를 기준으로 계산
     * 그 높이를 다섯 칸으로 나누고, 그중 한 칸 높이를 주간 달력의 높이로 적용
     * 월간 달력은 나머지 높이 전체로 적용
     */
    private var _binding: FragmentCalendarPageBinding? = null
    private val binding get() = _binding!!
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<FrameLayout>

    private val viewModel: ScheduleViewModel by activityViewModels()

    // 캘린더에 표시할 데이터를 담을 Map (날짜 -> 일정 리스트)
    private var events = mapOf<LocalDate, List<Schedule>>()
    private var allSchedules:List<Schedule> = emptyList()
    private var rowAssignmentCache = mutableMapOf<String, Int>()

    private var selectedMonth: YearMonth = YearMonth.now()
    private var selectedDate: LocalDate? = null
    private val today = LocalDate.now()

    private lateinit var dailyPageAdapter: DailyPageAdapter
    private var isProgrammaticScroll = false // 캘린더 클릭 vs 스와이프 구분용
    private var cachedSchedules: List<Schedule> = emptyList() // 데이터 캐싱용

    private var headerHeight = 0
    private var weekViewHeight = 0
    private var containerHeight = 0
    private var isInitialLayoutReady = false
    private var isInitialDataReady = false
    private var hasShownInitialContent = false
    private var pendingResetToTodayState = false
    private var pendingFocusDate: LocalDate? = null


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


        bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheet)

        val initialDate = viewModel.selectedDate.value
        val currentMonth = YearMonth.from(initialDate)
        val firstDayOfWeek = DayOfWeek.SUNDAY
        selectedMonth = currentMonth
        selectedDate = initialDate
        updateTitle()
        updateSelectedDateText(initialDate)
        viewModel.setSelectedDate(initialDate)
        viewModel.setSelectedDate(today) // 초기값 세팅

        viewModel.setSelectedDate(initialDate)
        setupCalendarLayout()
        setupBottomSheet()
        setupMonthYearPicker()
        setupViewPager()

        class DayViewContainer(view: View) : ViewContainer(view) {
            val rootLayout: ConstraintLayout = view.findViewById(R.id.root_layout)
            val textView: TextView = view.findViewById(R.id.calendarDayText)
            val eventContainer: LinearLayout = view.findViewById(R.id.eventsContainer)
            lateinit var date: LocalDate
            init {
                rootLayout.setOnClickListener { selectDate(date) }
            }
        }

        // 월간 달력 바인딩
        binding.calendarView.dayBinder = object : MonthDayBinder<DayViewContainer> {
            override fun create(view: View) = DayViewContainer(view)
            override fun bind(container: DayViewContainer, day: CalendarDay) {
                container.date = day.date
                // 현재 달의 날짜(MonthDate)일 때만 활성화 하기
                val isCurrentMonth = day.position == DayPosition.MonthDate
                updateDayUI(container.textView, container.rootLayout, day.date, isCurrentMonth)
                if (!isCurrentMonth && bottomSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN) {
                    container.eventContainer.removeAllViews()
                    return
                }
                // 요일에 따라 색상 작성
                when(day.date.dayOfWeek){
                    DayOfWeek.SATURDAY -> {
                        container.textView.setTextColor(ContextCompat.getColor(requireContext(),R.color.semantic_success))
                    }
                    DayOfWeek.SUNDAY -> {
                        container.textView.setTextColor(ContextCompat.getColor(requireContext(),R.color.semantic_error))
                    }
                    else -> {
                        container.textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
                    }
                }
                // 바텀 시트 여부에 따라 다른 UI 적용
                when(bottomSheetBehavior.state){
                    BottomSheetBehavior.STATE_HIDDEN -> {
                        setMonthCalendarWithoutBottomSheetUI(container.eventContainer, day.date)
                    }
                    else -> {
                        setWeekAndMonthCalendarUI(container.eventContainer, day.date)
                    }
                }
            }
        }

        // 주간 달력 바인딩
        binding.weekCalendarView.dayBinder = object : WeekDayBinder<DayViewContainer> {
            override fun create(view: View) = DayViewContainer(view)
            override fun bind(container: DayViewContainer, day: WeekDay) {
                container.date = day.date
                updateDayUI(container.textView,container.rootLayout, day.date, true)
                setWeekAndMonthCalendarUI(container.eventContainer, day.date)
            }
        }


        binding.calendarView.monthScrollListener = { month ->
            selectedMonth = month.yearMonth
            updateTitle()

            if (!isProgrammaticScroll) {
                val currentSelected = selectedDate ?: today

                // 이동하려는 월이 현재 이미 선택된 날짜의 월과 같은지 확인
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

        // 주간 캘린더 스크롤 리스너
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

        // 요일 헤더 설정 함수 호출
        setupDayHeaders(firstDayOfWeek)

        binding.btnReturnToToday.setOnClickListener {
            val targetMonth = YearMonth.now()

            isProgrammaticScroll = true

            // 날짜 먼저 선택
            selectDate(today, scrollToPager = true, fromScroll = false)

            // 캘린더 이동
            val currentMonth = selectedMonth
            val monthDiff = java.time.temporal.ChronoUnit.MONTHS.between(currentMonth, targetMonth)

            if (Math.abs(monthDiff) > 1) {
                val intermediateMonth = if (monthDiff > 0) targetMonth.minusMonths(1) else targetMonth.plusMonths(1)
                binding.calendarView.scrollToMonth(intermediateMonth)
                binding.calendarView.smoothScrollToMonth(targetMonth)
            } else {
                binding.calendarView.smoothScrollToMonth(targetMonth)
            }

            // 주간 캘린더도 오늘로 설정
            binding.weekCalendarView.scrollToWeek(today)

            // 이동이 완료된 후 리스너 해제
            binding.calendarView.postDelayed({
                isProgrammaticScroll = false
            }, 500) // 스크롤 애니메이션 시간만큼 충분히 줌 이거 없으면 UI충돌날때 있음
        }

        // 초기 날짜 텍스트 설정
        updateSelectedDateText(today)
        binding.btnReturnToToday.visibility = if (selectedDate == today) View.GONE else View.VISIBLE
        applyPendingResetIfNeeded()
        applyPendingFocusIfNeeded()
    }

    // 상단 텍스트 업데이트
    private fun updateSelectedDateText(date: LocalDate) {
        val headerFormat = DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)", Locale.KOREAN)
        binding.root.findViewById<TextView>(R.id.tv_selected_date)?.text = date.format(headerFormat)
    }


    // 요일 헤더(일~토) 설정 함수
    private fun setupDayHeaders(firstDayOfWeek: DayOfWeek) {
        val daysOfWeek = daysOfWeek(firstDayOfWeek)
        val dayLabels = resources.getStringArray(R.array.weekdays_short)
        fun labelFor(day: DayOfWeek): String {
            return when (day) {
                DayOfWeek.SUNDAY -> dayLabels[0]
                DayOfWeek.MONDAY -> dayLabels[1]
                DayOfWeek.TUESDAY -> dayLabels[2]
                DayOfWeek.WEDNESDAY -> dayLabels[3]
                DayOfWeek.THURSDAY -> dayLabels[4]
                DayOfWeek.FRIDAY -> dayLabels[5]
                DayOfWeek.SATURDAY -> dayLabels[6]
            }
        }
        binding.legendLayout.root.children.forEachIndexed { index, view ->
            val tv = view as TextView
            tv.text = labelFor(daysOfWeek[index])
            when (daysOfWeek[index]) {
                DayOfWeek.SUNDAY -> tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.semantic_error))
                DayOfWeek.SATURDAY -> tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.semantic_success))
                else -> tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
            }
        }
    }


    // 뷰모델 데이터 관찰 함수 구현
    private fun observeSchedules() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.scheduleMap.collectLatest { groupedMap ->
                events = groupedMap
                allSchedules = groupedMap.values.flatten()
                rowAssignmentCache.clear()

                if (groupedMap.isNotEmpty()) {
                    groupedMap.values.flatten()
                        .filter { it.type == "ROUTE" }
                        .forEach { schedule ->
                            viewModel.fetchRouteDetail(schedule.id)
                        }
                }

                if (::dailyPageAdapter.isInitialized) {
                    dailyPageAdapter.updateEvents(events, viewModel.routeDetails.value)
                }

                binding.calendarView.notifyCalendarChanged()
                binding.weekCalendarView.notifyCalendarChanged()
                isInitialDataReady = true
                showInitialContentIfNeeded()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.routeDetails.collectLatest { routeMap ->
                if (::dailyPageAdapter.isInitialized) {
                    // 캘린더 전체를 새로고침(notifyCalendarChanged)할 필요 없이
                    // 어댑터 데이터만 갱신해서 "경로를 불러오는 중..."을 실제 데이터로 바꿉니다.
                    dailyPageAdapter.updateEvents(events, routeMap)
                }
            }
        }

    }

    private fun showInitialContentIfNeeded() {
        if (hasShownInitialContent || _binding == null) return
        if (!isInitialLayoutReady || !isInitialDataReady) return
        hasShownInitialContent = true

        val targetMonth = selectedDate?.let { YearMonth.from(it) } ?: selectedMonth
        val targetHeight = containerHeight - headerHeight

        binding.calendarView.animate().cancel()
        binding.weekCalendarView.animate().cancel()
        binding.calendarView.scrollToMonth(targetMonth)
        if (targetHeight > 0 && binding.calendarView.layoutParams.height != targetHeight) {
            binding.calendarView.layoutParams.height = targetHeight
            binding.calendarView.requestLayout()
        }
        binding.calendarView.alpha = 1f
        binding.calendarView.visibility = View.VISIBLE
        binding.weekCalendarView.alpha = 0f
        binding.weekCalendarView.visibility = View.GONE

        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

        binding.calendarContainer.visibility = View.VISIBLE
        binding.bottomSheet.visibility = View.VISIBLE
        binding.btnReturnToToday.visibility = if (selectedDate == today) View.GONE else View.VISIBLE
    }

    private fun setupCalendarLayout() {
        /*
         * 실제 화면이 그려진 뒤에 높이를 읽음
         * 위쪽 제목 높이를 빼서 달력이 쓸 수 있는 높이를 구함
         * 그 높이를 다섯 칸으로 나눠서 한 칸 높이를 주간 달력 높이로 사용
         */
        binding.calendarContainer.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                binding.calendarContainer.viewTreeObserver.removeOnGlobalLayoutListener(this)
                containerHeight = binding.calendarContainer.height
                headerHeight = binding.headerContainer.height

                val availableHeight = containerHeight - headerHeight
                if (availableHeight > 0) {
                    weekViewHeight = availableHeight / 5
                    binding.weekCalendarView.layoutParams.height = weekViewHeight
                }
                bottomSheetBehavior.expandedOffset = headerHeight + weekViewHeight

                if (availableHeight > 0 && binding.calendarView.layoutParams.height != availableHeight) {
                    binding.calendarView.layoutParams.height = availableHeight
                }
                isInitialLayoutReady = availableHeight > 0
                showInitialContentIfNeeded()
            }
        })
    }

    private fun setupBottomSheet() {
        /*
         * 아래쪽 일정 목록은 두 가지 상태만 사용
         * 숨김 상태: 월간 달력이 보임
         * 펼침 상태: 주간 달력이 보임
         * 중간 단계는 쓰지 않으며, 두 화면을 페이드 인 페이드 아웃을 나누어서 적용
         */
        bottomSheetBehavior.apply {
            state = BottomSheetBehavior.STATE_HIDDEN
            peekHeight = 0
            isFitToContents = false
            halfExpandedRatio = 0.0001f
            isHideable = true
            skipCollapsed = true
        }
        // ... 바텀시트 콜백 로직 (기존 코드 그대로 유지) ...
        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                // 기존 애니메이션 및 상태 변경 로직 그대로 유지
                when (newState) {
                    BottomSheetBehavior.STATE_HIDDEN -> {
                        crossfade(
                            fadeInView = binding.calendarView,
                            fadeOutView = binding.weekCalendarView
                        )
                        // 현재 선택된 날짜가 있는 달로 월간 캘린더 이동(주간에서 스크롤만 하다가 월간으로 전환시 오류발생 보완)
                        val targetMonth = selectedDate?.let { YearMonth.from(it) } ?: selectedMonth
                        binding.calendarView.scrollToMonth(targetMonth)
                        val targetHeight = containerHeight - headerHeight
                        if (targetHeight > 0 && binding.calendarView.layoutParams.height != targetHeight) {
                            binding.calendarView.layoutParams.height = targetHeight
                            binding.calendarView.requestLayout()
                        }
                        notifyVisibleCalendarDates()
                    }
                    BottomSheetBehavior.STATE_EXPANDED -> {
                        binding.weekCalendarView.alpha = 0f
                        binding.weekCalendarView.visibility = View.INVISIBLE
                        binding.weekCalendarView.post {
                            binding.weekCalendarView.scrollToWeek(selectedDate ?: today)
                            notifyVisibleCalendarDates()
                            binding.weekCalendarView.visibility = View.VISIBLE
                            crossfade(
                                fadeInView = binding.weekCalendarView,
                                fadeOutView = binding.calendarView
                            )
                        }
                    }
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
            }
        })
    }
    private fun setupMonthYearPicker() {
        binding.calendarNumberPickerTv.setOnClickListener {
            showMonthYearPicker()
        }
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

    private fun crossfade(fadeInView: View, fadeOutView: View, durationMs: Long = 350) {
        /*
         * 두 화면 요소를 부드럽게 바꿔치기 한다.
         * 하나는 천천히 나타나고, 다른 하나는 천천히 사라진다.
         */
        fadeInView.animate().cancel()
        fadeOutView.animate().cancel()

        if (fadeInView.visibility != View.VISIBLE) {
            fadeInView.alpha = 0f
            fadeInView.visibility = View.VISIBLE
        }
        fadeInView.animate()
            .alpha(1f)
            .setDuration(durationMs)
            .setListener(null)
            .start()

        if (fadeOutView.visibility == View.VISIBLE) {
            fadeOutView.animate()
                .alpha(0f)
                .setDuration(durationMs)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        fadeOutView.visibility = View.GONE
                    }
                })
                .start()
        } else {
            fadeOutView.alpha = 0f
            fadeOutView.visibility = View.GONE
        }
    }

    private fun notifyVisibleCalendarDates() {
        binding.calendarView.findFirstVisibleMonth()?.weekDays
            ?.flatten()
            ?.map { it.date }
            ?.distinct()
            ?.forEach { date ->
                binding.calendarView.notifyDateChanged(date)
            }

        binding.weekCalendarView.findFirstVisibleWeek()?.days
            ?.map { it.date }
            ?.distinct()
            ?.forEach { date ->
                binding.weekCalendarView.notifyDateChanged(date)
            }
    }

    // CalendarPageFragment.kt 의 selectDate 수정
    private fun selectDate(date: LocalDate, scrollToPager: Boolean = true, fromScroll: Boolean = false) {
        /*
         * 날짜를 눌렀을 때의 핵심 흐름
         * 1. 선택 날짜를 저장하고 데이터 변경 알림
         * 2. 월간과 주간 달력에서 선택 표시를 갱신한다.
         * 3. 하루 일정 화면도 같은 날짜로 맞춘다.
         * 4. 같은 날짜를 다시 누르면 아래쪽 목록을 열거나 닫는다.
         */
        if (selectedDate == date && scrollToPager && !fromScroll && hasShownInitialContent) {
            // 바텀시트 토글 로직 그대로 유지
            bottomSheetBehavior.state = if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN) {
                BottomSheetBehavior.STATE_EXPANDED
            } else {
                BottomSheetBehavior.STATE_HIDDEN
            }
        } else {
            val oldDate = selectedDate
            selectedDate = date

            // 공유 뷰모델에 선택된 날짜 업데이트
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
                }

                // 약간의 딜레이 뒤에 해제 (스크롤이 완전히 끝날 때까지 보호)
                binding.root.postDelayed({ isProgrammaticScroll = false }, 100)
            }

            if (hasShownInitialContent && !fromScroll && bottomSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN) {
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
    }

    fun resetToTodayState() {
        if (_binding == null || !::bottomSheetBehavior.isInitialized) {
            pendingResetToTodayState = true
            return
        }
        pendingResetToTodayState = false
        focusOnDate(today)
    }

    private fun applyPendingResetIfNeeded() {
        if (!pendingResetToTodayState || _binding == null || !::bottomSheetBehavior.isInitialized) return
        resetToTodayState()
    }

    fun focusOnDate(date: LocalDate) {
        if (_binding == null || !::bottomSheetBehavior.isInitialized) {
            pendingFocusDate = date
            return
        }

        pendingFocusDate = null
        isProgrammaticScroll = true
        val targetMonth = YearMonth.from(date)
        selectedMonth = targetMonth
        updateTitle()

        selectDate(date, scrollToPager = true, fromScroll = true)
        binding.calendarView.scrollToMonth(targetMonth)
        binding.weekCalendarView.scrollToWeek(date)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        binding.root.postDelayed({ isProgrammaticScroll = false }, 100)
    }

    private fun applyPendingFocusIfNeeded() {
        val date = pendingFocusDate ?: return
        if (_binding == null || !::bottomSheetBehavior.isInitialized) return
        focusOnDate(date)
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
        /*
         * 날짜 칸의 모양을 정하는 규칙
         * - 선택된 날짜는 강조 색상과 테두리를 사용
         * - 오늘 날짜는 특별한 색을 사용
         * - 현재 달이 아닌 날짜는 흐리게 표시
         */
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
            onPinClick = { schedule ->
                val updatedSchedule = schedule.copy(isPinned = !schedule.isPinned)
                viewModel.updateSchedule(updatedSchedule)
            },
            onItemClick = { schedule ->
                openScheduleDetail(schedule)
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

    private fun openScheduleDetail(schedule: Schedule) {
        (parentFragment as? CalendarFragment)?.openScheduleDetail(
            scheduleId = schedule.id,
            occurrenceDate = schedule.startDate,
            scheduleType = schedule.type
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isInitialLayoutReady = false
        isInitialDataReady = false
        hasShownInitialContent = false
        _binding = null
    }

    // 바텀 시트 없는 월간 바인더 UI
    private fun setMonthCalendarWithoutBottomSheetUI(eventContainer: LinearLayout, date: LocalDate) {
        // 이전 바인딩 지우기
        eventContainer.removeAllViews()

        if(!events[date].isNullOrEmpty()){
            val sortedEvents = getSortedScheduleListOfDate(date)
            var row = 0

            if(sortedEvents.isNotEmpty()){
                val maxVisibleRows = setPageItemNumber(sortedEvents)
                for(schedule in sortedEvents) {
                    val allDatesForThisSchedule = getDisplayDatesForSchedule(schedule)
                    if(allDatesForThisSchedule.isNotEmpty()){
                        val scheduleRow = getAssignedRow(schedule)
                        if (scheduleRow >= maxVisibleRows) {
                            break
                        }

                        val firstDate = LocalDate.parse(allDatesForThisSchedule.first())
                        val lastDate = LocalDate.parse(allDatesForThisSchedule.last())
                        val isStart = date.isEqual(firstDate)
                        val isEnd = date.isEqual(lastDate)
                        val params = LinearLayout.LayoutParams(MATCH_PARENT, (16 * resources.displayMetrics.density).roundToInt())

                        if (row < scheduleRow) {
                            val spaceCount = scheduleRow - row
                            repeat(spaceCount) {
                                val binding = ItemMonthViewMultipleDaysBinding.inflate(layoutInflater, eventContainer, false)
                                binding.root.visibility = View.INVISIBLE
                                binding.root.layoutParams = params
                                eventContainer.addView(binding.root)
                            }
                            row = scheduleRow
                        }

                        // 하루 일정 or 반복 일정
                        if(firstDate == lastDate){
                            val binding = ItemMonthViewSingleDayBinding.inflate(layoutInflater)
                            val icon = binding.itemMonthViewSingleColor
                            icon.backgroundTintList = setBackgroundTintByScheduleColor(schedule)
                            binding.itemMonthViewSingleTv.text = schedule.title
                            binding.root.layoutParams = params
                            eventContainer.addView(binding.root)
                        }
                        // 장기 일정
                        else{
                            // 장기 일정 바인딩
                            val binding = ItemMonthViewMultipleDaysBinding.inflate(layoutInflater)
                            binding.itemMonthViewMultipleDays.backgroundTintList = setBackgroundTintByScheduleColor(schedule)
                            binding.itemMonthViewMultipleDays.setBackgroundResource(when{
                                isStart -> R.drawable.bg_item_month_view_first
                                isEnd -> R.drawable.bg_item_month_view_last
                                else -> R.drawable.bg_item_month_view_middle
                            })
                            binding.itemMonthViewMultipleDays.text = when{
                                isStart -> schedule.title
                                else -> ""
                            }

                            binding.root.layoutParams = params
                            eventContainer.addView(binding.root)
                        }
                    }
                    row++
                }
                val hiddenCount = sortedEvents.count { getAssignedRow(it) >= maxVisibleRows }
                if(maxVisibleRows == 3 && hiddenCount > 0){
                    val binding = ItemMonthViewMultipleDaysBinding.inflate(layoutInflater)
                    binding.itemMonthViewMultipleDays.backgroundTintList = ColorStateList.valueOf(resources.getColor(R.color.transparent))
                    binding.itemMonthViewMultipleDays.text = "+$hiddenCount"
                    eventContainer.addView(binding.root)
                }
            }
        }
    }

    // 주간 바인더 + 바텀시트 있는 월간
    private fun setWeekAndMonthCalendarUI(eventContainer: LinearLayout, date: LocalDate) {
        // 이전 바인딩 지우기
        eventContainer.removeAllViews()

        if(!events[date].isNullOrEmpty()){
            val sortedEvents = getSortedScheduleListOfDate(date)
            var row = 0

            if(sortedEvents.isNotEmpty()){
                val maxVisibleRows = setPageItemNumber(sortedEvents)
                for(schedule in sortedEvents) {
                    val allDatesForThisSchedule = getDisplayDatesForSchedule(schedule)
                    if(allDatesForThisSchedule.isNotEmpty()){
                        val scheduleRow = getAssignedRow(schedule)
                        if (scheduleRow >= maxVisibleRows) {
                            break
                        }

                        val firstDate = LocalDate.parse(allDatesForThisSchedule.first())
                        val lastDate = LocalDate.parse(allDatesForThisSchedule.last())
                        val isStart = date.isEqual(firstDate)
                        val isEnd = date.isEqual(lastDate)
                        val params = LinearLayout.LayoutParams(MATCH_PARENT, (8 * resources.displayMetrics.density).roundToInt())

                        if (row < scheduleRow) {
                            val spaceCount = scheduleRow - row
                            repeat(spaceCount) {
                                val binding = ItemWeekViewBinding.inflate(layoutInflater, eventContainer, false)
                                binding.itemWeekTv.visibility = View.INVISIBLE
                                binding.itemWeekTv.layoutParams = params
                                eventContainer.addView(binding.root)
                            }
                            row = scheduleRow
                        }

                        // 하루 일정 or 반복 일정
                        if(firstDate == lastDate){
                            val binding = ItemWeekViewBinding.inflate(layoutInflater)
                            binding.itemWeekTv.backgroundTintList = setBackgroundTintByScheduleColor(schedule)
                            binding.itemWeekTv.layoutParams = params
                            eventContainer.addView(binding.root)
                        }
                        // 장기 일정
                        else{
                            // 장기 일정 바인딩
                            val binding = ItemWeekViewBinding.inflate(layoutInflater)
                            binding.itemWeekTv.backgroundTintList = setBackgroundTintByScheduleColor(schedule)
                            binding.itemWeekTv.setBackgroundResource(when{
                                isStart -> R.drawable.bg_item_week_view_first
                                isEnd -> R.drawable.bg_item_week_view_last
                                else -> R.drawable.bg_item_week_view_middle
                            })

                            binding.itemWeekTv.layoutParams = params
                            eventContainer.addView(binding.root)
                        }
                    }
                    row++
                }
            }
        }
    }

    // date의 일정을 장기 -> 하루 일정 순으로 정렬
    private fun getSortedScheduleListOfDate(date: LocalDate): List<Schedule>{
        return events[date]?.sortedWith(compareBy<Schedule> { schedule ->
            getAssignedRow(schedule)
        }.thenBy { schedule ->
            val allDates = getDisplayDatesForSchedule(schedule)
            if (allDates.size > 1) 0 else 1
        }.thenBy { schedule ->
            if (schedule.isAllDay) 0 else 1
        }.thenBy { schedule ->
            schedule.startTime
        }.thenBy { schedule ->
            schedule.title.orEmpty()
        }.thenBy { schedule ->
            schedule.startDate
        }.thenBy { schedule ->
            schedule.id
        }) ?: emptyList()
    }

    // 해당 일정이 속한 모든 날짜 가져오기
    private fun getAllDatesForThisSchedule(schedule: Schedule): List<String> = allSchedules.filter { it.id == schedule.id }.map{ it.startDate }.distinct().sorted()

    private fun getDisplayDatesForSchedule(schedule: Schedule): List<String> {
        return if (!schedule.repeatRule.isNullOrEmpty()) {
            val start = runCatching { LocalDate.parse(schedule.startDate) }.getOrNull() ?: return listOf(schedule.startDate)
            val end = runCatching { LocalDate.parse(schedule.endDate) }.getOrNull() ?: return listOf(schedule.startDate)
            generateSequence(start) { current ->
                current.plusDays(1).takeIf { !it.isAfter(end) }
            }.map { it.toString() }.toList()
        } else {
            getAllDatesForThisSchedule(schedule)
        }
    }

    private fun getOccurrenceKey(schedule: Schedule): String {
        return if (!schedule.repeatRule.isNullOrEmpty()) {
            "${schedule.id}_${schedule.startDate}_${schedule.endDate}"
        } else {
            schedule.id.toString()
        }
    }

    private fun getAssignedRow(schedule: Schedule): Int {
        if (rowAssignmentCache.isEmpty()) {
            rowAssignmentCache = buildRowAssignments().toMutableMap()
        }
        return rowAssignmentCache[getOccurrenceKey(schedule)] ?: 0
    }

    private fun buildRowAssignments(): Map<String, Int> {
        data class DisplayOccurrence(
            val key: String,
            val start: LocalDate,
            val end: LocalDate,
            val priority: Int,
            val title: String,
            val id: Long
        )

        val occurrences = allSchedules
            .groupBy { schedule -> getOccurrenceKey(schedule) }
            .mapNotNull { (key, schedules) ->
                val representative = schedules.firstOrNull() ?: return@mapNotNull null
                val dates = getDisplayDatesForSchedule(representative)
                    .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }
                    .distinct()
                    .sorted()

                if (dates.isEmpty()) {
                    null
                } else {
                    val isMultiDay = dates.size > 1
                    val priority = when {
                        isMultiDay && representative.isAllDay && !representative.repeatRule.isNullOrEmpty() -> 0
                        isMultiDay && representative.isAllDay -> 1
                        isMultiDay && !representative.repeatRule.isNullOrEmpty() -> 2
                        isMultiDay -> 3
                        representative.isAllDay && !representative.repeatRule.isNullOrEmpty() -> 4
                        representative.isAllDay -> 5
                        !representative.repeatRule.isNullOrEmpty() -> 6
                        else -> 7
                    }
                    DisplayOccurrence(
                        key = key,
                        start = dates.first(),
                        end = dates.last(),
                        priority = priority,
                        title = representative.title.orEmpty(),
                        id = representative.id
                    )
                }
            }
            .sortedWith(
                compareBy<DisplayOccurrence>({ it.priority }, { it.start }, { it.end }, { it.title }, { it.id })
            )

        val occupiedByDate = mutableMapOf<LocalDate, MutableSet<Int>>()
        val assignments = mutableMapOf<String, Int>()

        occurrences.forEach { occurrence ->
            var row = 0
            while (isRowOccupied(occurrence.start, occurrence.end, row, occupiedByDate)) {
                row++
            }
            assignments[occurrence.key] = row

            generateSequence(occurrence.start) { current ->
                current.plusDays(1).takeIf { !it.isAfter(occurrence.end) }
            }.forEach { date ->
                occupiedByDate.getOrPut(date) { mutableSetOf() }.add(row)
            }
        }

        return assignments
    }

    private fun isRowOccupied(
        start: LocalDate,
        end: LocalDate,
        row: Int,
        occupiedByDate: Map<LocalDate, Set<Int>>
    ): Boolean {
        return generateSequence(start) { current ->
            current.plusDays(1).takeIf { !it.isAfter(end) }
        }.any { date ->
            occupiedByDate[date]?.contains(row) == true
        }
    }

    // 일정 색상에 따라 텍스트뷰 배경 적용
    private fun setBackgroundTintByScheduleColor(schedule: Schedule): ColorStateList{
        return when {
            schedule.eventColor != null && schedule.eventColor != 0 -> ColorStateList.valueOf(schedule.eventColor)
            schedule.calendarColor != null && schedule.calendarColor != 0 -> ColorStateList.valueOf(schedule.calendarColor)
            else -> ColorStateList.valueOf(Color.parseColor("#A2BD3B"))
        }
    }
    // 화면에 표시할 아이템 개수 설정
    private fun setPageItemNumber(sortedEvents: List<Schedule>):Int = if(sortedEvents.size > 4) 3 else 4
}

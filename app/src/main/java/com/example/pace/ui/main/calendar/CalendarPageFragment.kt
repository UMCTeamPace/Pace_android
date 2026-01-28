package com.example.pace.ui.main.calendar

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
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
import com.example.pace.R
import com.example.pace.databinding.FragmentCalendarPageBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.kizitonwose.calendar.core.*
import com.kizitonwose.calendar.view.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale


class CalendarPageFragment: Fragment() {
    private var _binding: FragmentCalendarPageBinding? = null
    private val binding get() = _binding!!
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<FrameLayout>

    private var selectedMonth: YearMonth = YearMonth.now()
    private var selectedDate: LocalDate? = null
    private val today = LocalDate.now()

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

    // 대부분의 초기화 코드작성한 함수
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ui스레드 디버깅용
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectAll()
                .penaltyLog()
                .penaltyFlashScreen()
                .build()
        )

        // 바텀시트 움직임 작성
        bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheet)

        // 현재 날짜 가져오기
        val currentMonth = YearMonth.now()
        selectedMonth = currentMonth
        selectedDate = today


        // 캘린더뷰 컨테이너의 높이계산 코드
        binding.calendarContainer.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                binding.calendarContainer.viewTreeObserver.removeOnGlobalLayoutListener(this)
                containerHeight = binding.calendarContainer.height
                headerHeight = binding.headerContainer.height

                // 주간캘린더 높이는 월간 캘린더 높이 / 5로 고정
                val collapsedCalendarHeight = containerHeight - headerHeight - bottomSheetBehavior.peekHeight
                if (collapsedCalendarHeight > 0) {
                    weekViewHeight = collapsedCalendarHeight / 5
                    binding.weekCalendarView.layoutParams.height = weekViewHeight
                }

                // Expanded 상태에서 상단 여백 설정 (주간 캘린더가 보이도록)
                bottomSheetBehavior.expandedOffset = headerHeight + weekViewHeight

                // STATE_HIDDEN일 때의 월간 캘린더 높이 설정
                val targetHeight = containerHeight - headerHeight
                if (targetHeight > 0 && binding.calendarView.layoutParams.height != targetHeight) {
                    binding.calendarView.layoutParams.height = targetHeight
                }
            }
        })

        bottomSheetBehavior.apply {
            state = BottomSheetBehavior.STATE_HIDDEN
            // 현재 하드코딩 되어있음, 다른 방식으로 계산 필요
            peekHeight = 500
            // 모든 상태에 대해서 내가 설정한대로 높이 조절해주기 위해서 false로 설정
            isFitToContents = false
            // 모든 상태가 나오면 Half_Expanded상태도 나오게 되는데, 그걸 생략하려고 함
            halfExpandedRatio = 0.0001f
            isHideable = true
        }

        var lastBottomSheetState: Int = bottomSheetBehavior.state

        binding.calendarNumberPickerBtnIv.setOnClickListener {
            showMonthYearPicker()
        }

        class DayViewContainer(view: View) : ViewContainer(view) {
            // 바깥 바인딩 객체를 아래에서도 사용해야하기 때문에 그냥 findViewById로 작성함
            val rootLayout: ConstraintLayout = view.findViewById(R.id.root_layout)
            val textView: TextView = view.findViewById(R.id.calendarDayText)

            lateinit var day: CalendarDay
            lateinit var weekDay: WeekDay

            // 이 부분 잘 모르겠음
            init {
                rootLayout.setOnClickListener {
                    val date: LocalDate
                    val isThisMonth: Boolean

                    when {
                        ::day.isInitialized -> {
                            date = day.date
                            isThisMonth = day.position == DayPosition.MonthDate
                        }
                        ::weekDay.isInitialized -> {
                            date = weekDay.date
                            isThisMonth = YearMonth.from(weekDay.date) == selectedMonth
                        }
                        else -> return@setOnClickListener
                    }

                    if (!isThisMonth) return@setOnClickListener

                    if (selectedDate != date) {
                        val oldDate = selectedDate
                        selectedDate = date
                        binding.calendarView.notifyDateChanged(date)
                        oldDate?.let { binding.calendarView.notifyDateChanged(it) }
                        binding.weekCalendarView.notifyDateChanged(date)
                        oldDate?.let { binding.weekCalendarView.notifyDateChanged(it) }
                        if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN) {
                            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                        }
                    } else {
                        when (bottomSheetBehavior.state) {
                            BottomSheetBehavior.STATE_HIDDEN -> {
                                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                            }
                            else -> {
                                bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                            }
                        }
                    }
                }
            }
        }

        // 월간 캘린더뷰에서 커스터마이징
        val monthDayBinder = object : MonthDayBinder<DayViewContainer> {
            override fun create(view: View) = DayViewContainer(view)
            override fun bind(container: DayViewContainer, day: CalendarDay) {
                container.day = day
                val textView = container.textView
                val rootLayout = container.rootLayout

                textView.text = day.date.dayOfMonth.toString()
                // 잔상이 남는다는 말이 있어서 초기화 강화
                textView.background = null
                rootLayout.background = null
                textView.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.text_primary)
                )

                if (day.position == DayPosition.MonthDate) {
                    when {
                        // 선택된 날짜가 오늘이면 하얀글씨에 초록원
                        day.date == selectedDate && day.date == today -> {
                            rootLayout.setBackgroundResource(R.drawable.bg_selected_day_outline)
                            textView.setTextColor(Color.WHITE)
                            textView.setBackgroundResource(R.drawable.drawable_circle_green)
                        }
                       // 그냥 선택됐으면 초록글씨에 하얀원
                        day.date == selectedDate -> {
                            rootLayout.setBackgroundResource(R.drawable.bg_selected_day_outline)
                            textView.setTextColor(
                                ContextCompat.getColor(requireContext(), R.color.schedule_18)
                            )
                            textView.setBackgroundResource(R.drawable.drawable_circle_white)
                        }
                        // 오늘에는 테두리없이 하얀글씨에 초록원
                        day.date == today -> {
                            textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                            textView.setBackgroundResource(R.drawable.drawable_circle_green)
                        }
                        //아무것도 없으면 텍스트만 검정색으로
                        else -> {
                            textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
                            textView.background = null
                            rootLayout.background = null
                        }
                    }
                } else {
                    // 이번달이 아니면 회색으로 표시
                    textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray_400))
                }
            }
        }
        // 월간 캘린더뷰에 커스터마이징 완료한 바인더 넣어주기
        binding.calendarView.dayBinder = monthDayBinder

        // 주간 캘린더뷰 커스터마이징
        val weekDayBinder = object : WeekDayBinder<DayViewContainer> {
            override fun create(view: View) = DayViewContainer(view)
            override fun bind(container: DayViewContainer, day: WeekDay) {
                container.weekDay = day
                val textView = container.textView
                val rootLayout = container.rootLayout

                textView.text = day.date.dayOfMonth.toString()
                textView.background = null
                rootLayout.background = null
                textView.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.text_primary)
                )

                val isThisMonth = YearMonth.from(day.date) == selectedMonth

                if (isThisMonth) {
                    when {
                        day.date == selectedDate && day.date == today -> {
                            rootLayout.setBackgroundResource(R.drawable.bg_selected_day_outline)
                            textView.setTextColor(Color.WHITE)
                            textView.setBackgroundResource(R.drawable.drawable_circle_green)
                        }
                        day.date == selectedDate -> {
                            rootLayout.setBackgroundResource(R.drawable.bg_selected_day_outline)
                            textView.setTextColor(
                                ContextCompat.getColor(requireContext(), R.color.schedule_18)
                            )
                            textView.setBackgroundResource(R.drawable.drawable_circle_white)
                        }
                        day.date == today -> {
                            textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                            textView.setBackgroundResource(R.drawable.drawable_circle_green)
                        }
                        else -> {
                            textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
                        }
                    }
                } else {
                    textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray_400))
                }
            }
        }

        // 커스터마이징 완료한 주간캘린더뷰 바인딩
        binding.weekCalendarView.dayBinder = weekDayBinder

        // 시작, 마지막월을 앞뒤로 100달 넣어주기
        val firstMonth = currentMonth.minusMonths(100)
        val lastMonth = currentMonth.plusMonths(100)
        // 시작을 일요일로 설정(기본 월요일이라 계산할 때 편하게 하기위함)
        val firstDayOfWeek = DayOfWeek.SUNDAY

        binding.calendarView.setup(firstMonth, lastMonth, firstDayOfWeek)
        // 해당 월이 아니면 지정된 스타일로 해주기
        binding.calendarView.outDateStyle = OutDateStyle.EndOfRow
        // 선택된 날을 시작으로 스크롤
        binding.calendarView.scrollToMonth(currentMonth)

        // 주차별 캘린더도 한계지점 설정
        binding.weekCalendarView.setup(
            today.minusWeeks(52),
            today.plusWeeks(52),
            firstDayOfWeek
        )
        // 선택된 날을 기준으로 스크롤 시작
        binding.weekCalendarView.scrollToWeek(selectedDate ?: today)

        // 월별로 넘어갈 때 제목 수정하기
        binding.calendarView.monthScrollListener = {
            selectedMonth = it.yearMonth
            updateTitle()
        }

        binding.weekCalendarView.weekScrollListener = { week ->
            val days = week.days
            val thisMonthDays = days.filter { YearMonth.from(it.date) == selectedMonth }
            val targetDay = if (thisMonthDays.isNotEmpty()) {
                thisMonthDays.first()
            } else {
                days.first()
            }
            selectedMonth = YearMonth.from(targetDay.date)
            updateTitle()
        }

        // 주차별로 일요일과 토요일에 색상 설정
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

        // 바텀시트의 상태에 따라 캘린더뷰 크기 재계산
        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
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
                            // Start animating calendarView back to collapsed height
                            // weekCalendarView will still be visible initially, calendarView will expand beneath it
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
                                        // Once calendarView has expanded, hide weekCalendarView
                                        weekCalendar.visibility = View.GONE
                                        selectedDate?.let { binding.calendarView.scrollToMonth(YearMonth.from(it)) }
                                    }
                                })
                            }
                            animator.start()
                        } else {
                            // Not coming from EXPANDED, just set states directly
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
                            // Fallback if weekViewHeight is not calculated yet or invalid
                            binding.calendarView.visibility = View.GONE
                            binding.weekCalendarView.visibility = View.VISIBLE
                            binding.weekCalendarView.scrollToWeek(selectedDate ?: today)
                        }
                    }
                }
                lastBottomSheetState = newState
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                // Only handle sliding for HIDDEN and COLLAPSED transitions
                // When slideOffset <= 0, it means the bottom sheet is moving between COLLAPSED (0) and HIDDEN (-1).
                // When slideOffset > 0, it means the bottom sheet is moving between COLLAPSED (0) and EXPANDED (1).
                // We let the ValueAnimator in onStateChanged handle the EXPANDED <-> COLLAPSED transition
                if (slideOffset <= 0) {
                    val calendarView = binding.calendarView
                    val peekHeight = bottomSheetBehavior.peekHeight

                    if (containerHeight == 0 || headerHeight == 0) return

                    val hiddenHeight = containerHeight - headerHeight
                    val collapsedHeight = containerHeight - headerHeight - peekHeight

                    if (collapsedHeight > 0 && hiddenHeight > 0) {
                        // Interpolate height between collapsedHeight (at slideOffset=0) and hiddenHeight (at slideOffset=-1)
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

    // 피커 보여주는 함수
    private fun showMonthYearPicker() {
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

    // 연 월 업데이트 함수
    private fun updateTitle() {
        binding.calendarNumberPickerTv.text = "${selectedMonth.year}년 ${selectedMonth.monthValue}월"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
package com.example.pace.ui.main.calendar

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator

import android.app.AlertDialog
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
                adjustCalendarHeight()
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

        binding.calendarNumberPickerBtnIv.setOnClickListener {
            showMonthYearPicker()
        }

        class DayViewContainer(view: View) : ViewContainer(view) {
            // 바깥 바인딩 객체를 아래에서도 사용해야하기 때문에 그냥 findViewById로 작성함
            val rootLayout: ConstraintLayout = view.findViewById(R.id.root_layout)
            val textView: TextView = view.findViewById(R.id.calendarDayText)

            lateinit var day: CalendarDay
            lateinit var weekDay: WeekDay

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

        val monthDayBinder = object : MonthDayBinder<DayViewContainer> {
            override fun create(view: View) = DayViewContainer(view)
            override fun bind(container: DayViewContainer, day: CalendarDay) {
                container.day = day
                val textView = container.textView
                val rootLayout = container.rootLayout

                textView.text = day.date.dayOfMonth.toString()
                textView.background = null
                rootLayout.background = null
                textView.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.text_primary)
                )

                if (day.position == DayPosition.MonthDate) {
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
                    // This handles the "disable" part of the "4+1" week display
                    textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray_400))
                }
            }
        }
        binding.calendarView.dayBinder = monthDayBinder

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
        binding.weekCalendarView.dayBinder = weekDayBinder

        val firstMonth = currentMonth.minusMonths(100)
        val lastMonth = currentMonth.plusMonths(100)
        // D. 주의 시작을 일요일로 설정
        val firstDayOfWeek = DayOfWeek.SUNDAY
        binding.calendarView.setup(firstMonth, lastMonth, firstDayOfWeek)
        binding.calendarView.outDateStyle = OutDateStyle.EndOfRow
        binding.calendarView.scrollToMonth(currentMonth)

        binding.weekCalendarView.setup(
            today.minusWeeks(52),
            today.plusWeeks(52),
            firstDayOfWeek
        )
        binding.weekCalendarView.scrollToWeek(selectedDate ?: today)

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

        val daysOfWeek = daysOfWeek(firstDayOfWeek)
        binding.legendLayout.root.children.forEachIndexed { index, view ->
            val tv = view as TextView
            tv.text = daysOfWeek[index].getDisplayName(TextStyle.SHORT, Locale.getDefault())
            tv.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
            when (daysOfWeek[index]) {
                DayOfWeek.SUNDAY -> tv.setTextColor(Color.parseColor("#FF4242"))
                DayOfWeek.SATURDAY -> tv.setTextColor(Color.parseColor("#4F81FF"))
                else -> {}
            }
        }
        updateTitle()

        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                android.util.Log.d("BottomSheetState", "newState=$newState (${stateName(newState)})")

                when (newState) {
                    BottomSheetBehavior.STATE_HIDDEN -> {
                        binding.weekCalendarView.visibility = View.GONE
                        binding.calendarView.visibility = View.VISIBLE
                        selectedDate?.let { binding.calendarView.scrollToMonth(YearMonth.from(it)) }
                        val targetHeight = containerHeight - headerHeight
                        if (targetHeight > 0) {
                            animateCalendarHeight(targetHeight)
                        }
                    }
                    BottomSheetBehavior.STATE_COLLAPSED -> {
                        binding.weekCalendarView.visibility = View.GONE
                        binding.calendarView.visibility = View.VISIBLE
                        selectedDate?.let { binding.calendarView.scrollToMonth(YearMonth.from(it)) }
                        val targetHeight = containerHeight - headerHeight - bottomSheetBehavior.peekHeight
                        if (targetHeight > 0) {
                            animateCalendarHeight(targetHeight)
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
            }
            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })
    }

    private fun animateCalendarHeight(toHeight: Int) {
        val calendar = binding.calendarView
        if (calendar.height == toHeight) return

        val animator = ValueAnimator.ofInt(calendar.height, toHeight).apply {
            duration = 250
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                val layoutParams = calendar.layoutParams
                layoutParams.height = animation.animatedValue as Int
                calendar.layoutParams = layoutParams
            }
        }
        animator.start()
    }

    private fun stateName(state: Int): String = when (state) {
        BottomSheetBehavior.STATE_HIDDEN -> "HIDDEN"
        BottomSheetBehavior.STATE_COLLAPSED -> "COLLAPSED"
        BottomSheetBehavior.STATE_EXPANDED -> "EXPANDED"
        BottomSheetBehavior.STATE_DRAGGING -> "DRAGGING"
        BottomSheetBehavior.STATE_SETTLING -> "SETTLING"
        BottomSheetBehavior.STATE_HALF_EXPANDED -> "HALF_EXPANDED"
        else -> "UNKNOWN($state)"
    }

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

    private fun updateTitle() {
        binding.calendarNumberPickerTv.text = "${selectedMonth.year}년 ${selectedMonth.monthValue}월"
    }

    private fun adjustCalendarHeight() {
        // A. Hidden 상태일 때 항상 컨테이너 전체 높이 차지
        val targetHeight = containerHeight - headerHeight
        if (targetHeight > 0 && binding.calendarView.layoutParams.height != targetHeight) {
            binding.calendarView.layoutParams.height = targetHeight
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
package com.example.pace.ui.main.calendar

import androidx.transition.TransitionManager
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.os.StrictMode
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
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
import java.time.temporal.WeekFields
import java.util.Locale


class CalendarPageFragment: Fragment() {
    private var _binding: FragmentCalendarPageBinding? = null
    private val binding get() = _binding!!
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<FrameLayout>

    private var selectedMonth: YearMonth = YearMonth.now()
    private var selectedDate: LocalDate? = null
    private val today = LocalDate.now()

    private var headerHeight = 0

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
                .penaltyFlashScreen()  // 디버그 시 화면 깜빡임으로 확인
                .build()
        )

        bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheet)

        //월간 캘린더뷰가 담기는 컨테이너가 변화하는 바텀시트 높이에 대응하여 동적으로 변하게 하기
        binding.calendarContainer.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                binding.calendarContainer.viewTreeObserver.removeOnGlobalLayoutListener(this)
                headerHeight = binding.headerContainer.height

                // Temporarily make it invisible to get the height
                binding.weekCalendarView.visibility = View.INVISIBLE
                val weekViewHeight = binding.weekCalendarView.height
                // Set it back to gone
                binding.weekCalendarView.visibility = View.GONE

                val screenHeight = binding.root.height
                val desiredHeight = screenHeight - headerHeight - weekViewHeight
                
                val layoutParams = binding.bottomSheet.layoutParams
                layoutParams.height = desiredHeight
                binding.bottomSheet.layoutParams = layoutParams

                adjustCalendarHeight()
            }
        })

        bottomSheetBehavior.apply {
            state = BottomSheetBehavior.STATE_HIDDEN
            peekHeight = 300
            isFitToContents = true // We will control height by setting the container's height
            isHideable = true
            skipCollapsed = true // We want to skip collapsed on drag, but not on programmatic set
        }

        binding.calendarNumberPickerBtnIv.setOnClickListener {
            showMonthYearPicker()
        }

        class DayViewContainer(view: View) : ViewContainer(view) {
            val rootLayout: ConstraintLayout = view.findViewById(R.id.root_layout)
            val textView: TextView = view.findViewById(R.id.calendarDayText)

            lateinit var day: CalendarDay   // Month용
            lateinit var weekDay: WeekDay   // Week용

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
                        // Different date clicked
                        val oldDate = selectedDate
                        selectedDate = date
                        binding.calendarView.notifyDateChanged(date)
                        oldDate?.let { binding.calendarView.notifyDateChanged(it) }
                        binding.weekCalendarView.notifyDateChanged(date)
                        oldDate?.let { binding.weekCalendarView.notifyDateChanged(it) }
                        // If sheet was hidden, show it collapsed
                        if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN) {
                            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                        }
                    } else {
                        // Same date clicked, toggle
                        when (bottomSheetBehavior.state) {
                            BottomSheetBehavior.STATE_HIDDEN -> {
                                bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                            }
                            // If collapsed or expanded, hide it
                            else -> {
                                bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                            }
                        }
                    }
                }
            }
        }

        // 월별 캘린더 커스터마이징
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
                            // 오늘이면서 선택된 날
                            rootLayout.setBackgroundResource(R.drawable.bg_selected_day_outline)
                            textView.setTextColor(Color.WHITE)
                            textView.setBackgroundResource(R.drawable.drawable_circle_green)
                        }
                        day.date == selectedDate -> {
                            // 선택된 날 (오늘 아님)
                            rootLayout.setBackgroundResource(R.drawable.bg_selected_day_outline)
                            textView.setTextColor(
                                ContextCompat.getColor(requireContext(), R.color.schedule_18)
                            )
                            textView.setBackgroundResource(R.drawable.drawable_circle_white)
                        }
                        day.date == today -> {
                            // 오늘(선택 안 된 상태)
                            textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                            textView.setBackgroundResource(R.drawable.drawable_circle_green)       // 흰 원
                        }
                        else -> {
                            // 평일: 요일에 따라 색
                            textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
                        }
                    }
                } else {
                    textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray_400))
                }

            }
        }
        binding.calendarView.dayBinder = monthDayBinder


        // 주차별 캘린더 커스터마이징
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
                    when (day.date) {
                        selectedDate -> {
                            rootLayout.setBackgroundColor(
                                ContextCompat.getColor(requireContext(), R.color.schedule_18)
                            )
                            textView.setTextColor(Color.WHITE)
                            textView.setBackgroundResource(R.drawable.drawable_circle_green)
                        }
                        today -> {
                            textView.setTextColor(
                                ContextCompat.getColor(requireContext(), R.color.schedule_18)
                            )
                            textView.setBackgroundResource(R.drawable.drawable_circle_white)
                        }
                    }
                } else {
                    textView.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.gray_400)
                    )
                }
            }
        }
        binding.weekCalendarView.dayBinder = weekDayBinder

        val currentMonth = YearMonth.now()
        selectedMonth = currentMonth
        selectedDate = today
        val firstMonth = currentMonth.minusMonths(100)
        val lastMonth = currentMonth.plusMonths(100)
        val firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
        binding.calendarView.setup(firstMonth, lastMonth, firstDayOfWeek)
        binding.calendarView.scrollToMonth(currentMonth)

        binding.weekCalendarView.setup(
            today.minusWeeks(52),
            today.plusWeeks(52),
            firstDayOfWeek
        )
        binding.weekCalendarView.scrollToWeek(selectedDate ?: today)


        binding.calendarView.monthScrollListener = { month ->
            selectedMonth = month.yearMonth
            updateTitle()
        }

        binding.weekCalendarView.weekScrollListener = { week ->
            val days = week.days

            // 이 주에서 현재 month와 같은 날짜들
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


        // 바텀시트 움직임을 등록
        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                android.util.Log.d("BottomSheetState", "newState=$newState (${stateName(newState)})")
                
                TransitionManager.beginDelayedTransition(binding.root as ViewGroup)

                when (newState) {
                    BottomSheetBehavior.STATE_HIDDEN -> {
                        binding.calendarView.visibility = View.VISIBLE
                        binding.weekCalendarView.visibility = View.GONE
                        selectedDate?.let { binding.calendarView.scrollToMonth(YearMonth.from(it)) }
                        adjustCalendarHeight()
                    }

                    BottomSheetBehavior.STATE_COLLAPSED -> {
                        binding.calendarView.visibility = View.VISIBLE
                        binding.weekCalendarView.visibility = View.GONE
                        selectedDate?.let { binding.calendarView.scrollToMonth(YearMonth.from(it)) }

                        // 높이 재계산
                        val containerHeight = binding.calendarContainer.height
                        val bottomSheetHeight = bottomSheetBehavior.peekHeight
                        val calendarHeight = containerHeight - headerHeight - bottomSheetHeight
                        if (calendarHeight > 0) {
                            binding.calendarView.layoutParams.height = calendarHeight
                            binding.calendarView.requestLayout()
                        }
                    }

                    BottomSheetBehavior.STATE_EXPANDED -> {
                        binding.calendarView.visibility = View.GONE
                        binding.weekCalendarView.visibility = View.VISIBLE
                        binding.weekCalendarView.scrollToWeek(selectedDate ?: today)
                    }
                }
            }
            override fun onSlide(bottomSheet: View, slideOffset: Float) {

            }
        })

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


    // 날짜 피커 나타내기
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

    // 캘린더 높이를 동적으로 조절
    private fun adjustCalendarHeight() {
        val containerHeight = binding.calendarContainer.height
        if (containerHeight > headerHeight) {
            binding.calendarView.layoutParams.height = containerHeight - headerHeight
            binding.calendarView.requestLayout()
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
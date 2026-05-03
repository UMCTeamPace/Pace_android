package com.example.pace.ui.add_schedule

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.TouchDelegate
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import com.example.pace.R
import com.example.pace.data.model.request.RepeatInfo
import com.example.pace.databinding.FragmentScheduleRepeatBinding
import com.example.pace.databinding.ItemCalendarDayAddscheduleBinding
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.daysOfWeek
import com.kizitonwose.calendar.view.MonthDayBinder
import com.kizitonwose.calendar.view.ViewContainer
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.*

class ScheduleRepeatFragment : Fragment() {

    private var _binding: FragmentScheduleRepeatBinding? = null
    private val binding get() = _binding!!

    private var detailView: View? = null
    private var selectedEndDate: LocalDate? = LocalDate.now().plusWeeks(1) // 기본값 1주일 뒤

    // 날짜 포맷터 추가
    private val monthFormatter = DateTimeFormatter.ofPattern("yyyy년 M월")
    // 기존 dateFormatter를 요일이 포함된 형식으로 수정
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd (E)", Locale.KOREAN)

    private lateinit var baseDate: LocalDate
    private var existingInfo: RepeatInfo? = null


    private val descriptionWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) { updateFullDescription() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            val dateStr = it.getString("startDate")
            baseDate = if (dateStr != null) LocalDate.parse(dateStr) else LocalDate.now()

            // 기존 설정 정보 가져오기
            existingInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                it.getSerializable("existingRepeatInfo", RepeatInfo::class.java)
            } else {
                @Suppress("DEPRECATION")
                it.getSerializable("existingRepeatInfo") as? RepeatInfo
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScheduleRepeatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupFocusClearInteractions()
        setupMainListeners()
        expandRepeatOptionTouchArea()
        setupCalendar()
        setupLegend()
        if (existingInfo != null) {
            restorePreviousSettings(existingInfo!!)
        } else {
            updateFullDescription()
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                sendResultAndBack()
            }
        })
    }

    private fun setupFocusClearInteractions() {
        binding.root.setOnClickListener {
            hideKeyboard()
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val imeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            if (!imeVisible) {
                clearCurrentInputFocus()
            }
            insets
        }
    }

    private fun setupMainListeners() {
        binding.ivRepeatBack.setOnClickListener { sendResultAndBack() }
        configureNumberInput(binding.etEndCount)

        binding.rgRepeatOptions.setOnCheckedChangeListener { _, checkedId ->
            hideKeyboard()
            binding.rgRepeatOptions.requestFocus()
            handleLayoutSwitch(checkedId)
            updateFullDescription()
        }

        binding.rgEndOptions.setOnCheckedChangeListener { _, checkedId ->
            // 어떤 옵션을 누르든 일단 키보드부터 내림
            hideKeyboard()

            // 포커스를 라디오 그룹으로 강제 이동시켜 EditText에서 포커스를 뺏어옴
            binding.rgEndOptions.requestFocus()

            handleEndLayoutVisibility()
            updateFullDescription()

            if (checkedId == R.id.rb_end_count) {
                // '횟수 지정'일 때만 다시 키보드 올림
                binding.etEndCount.postDelayed({ // 레이아웃 안정화 후 키보드 팝업
                    binding.etEndCount.requestFocus()
                    val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(binding.etEndCount, InputMethodManager.SHOW_IMPLICIT)
                }, 100)
            }
        }

        binding.rbEndNever.setOnClickListener {
            // 1. 강제 포커스 해제 (EditText에서 포커스를 완전히 뺏어옴)
            binding.etEndCount.clearFocus()

            // 2. 키보드 즉시 숨김
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view?.windowToken, 0)

            // 3. 라디오 버튼 체크 강제 수행 (간혹 이벤트가 씹히는 것 방지)
            binding.rbEndNever.isChecked = true
            handleEndLayoutVisibility()
            updateFullDescription()
        }

        val endRadioButtons = listOf(binding.rbEndNever, binding.rbEndCount, binding.rbEndDate)
        endRadioButtons.forEach { rb ->
            rb.setOnClickListener { clickedView ->
                endRadioButtons.forEach { it.isChecked = (it == clickedView) }
                handleEndLayoutVisibility()
                updateFullDescription()

                if (clickedView == binding.rbEndCount) {
                    binding.etEndCount.requestFocus()
                    val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(binding.etEndCount, InputMethodManager.SHOW_IMPLICIT)
                }
            }
        }

        binding.etEndCount.addTextChangedListener(descriptionWatcher)

        // 캘린더 월 이동 리스너
        binding.btnPrevMonth.setOnClickListener {
            binding.calendarPicker.findFirstVisibleMonth()?.let {
                // scrollToMonth 대신 smoothScrollToMonth 사용
                binding.calendarPicker.smoothScrollToMonth(it.yearMonth.minusMonths(1))
            }
        }

        binding.btnNextMonth.setOnClickListener {
            binding.calendarPicker.findFirstVisibleMonth()?.let {
                // scrollToMonth 대신 smoothScrollToMonth 사용
                binding.calendarPicker.smoothScrollToMonth(it.yearMonth.plusMonths(1))
            }
        }
    }

    private fun expandRepeatOptionTouchArea() {
        val targets = listOf(
            binding.rbNone,
            binding.rbDaily,
            binding.rbWeek,
            binding.rbMonth,
            binding.rbYear
        )
        expandTouchAreas(targets, extraTop = dpToPx(12), extraBottom = dpToPx(12))
    }

    private fun expandTouchAreas(targets: List<View>, extraTop: Int = 0, extraBottom: Int = 0) {
        val parent = targets.firstOrNull()?.parent as? View ?: return
        parent.post {
            val delegates = targets.map { target ->
                val rect = Rect()
                target.getHitRect(rect)
                rect.top -= extraTop
                rect.bottom += extraBottom
                TouchDelegate(rect, target)
            }
            parent.touchDelegate = MultiTouchDelegate(
                anchorView = targets.first(),
                delegates = delegates
            )
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun setFadeVisibility(view: View, visible: Boolean, duration: Long = 150L) {
        view.animate().cancel()
        if (visible) {
            if (view.visibility == View.VISIBLE && view.alpha == 1f) return
            view.alpha = 0f
            view.visibility = View.VISIBLE
            view.animate().alpha(1f).setDuration(duration).start()
        } else {
            if (view.visibility != View.VISIBLE) return
            view.animate()
                .alpha(0f)
                .setDuration(duration)
                .withEndAction {
                    view.visibility = View.GONE
                    view.alpha = 1f
                }
                .start()
        }
    }

    private class MultiTouchDelegate(
        anchorView: View,
        private val delegates: List<TouchDelegate>
    ) : TouchDelegate(Rect(), anchorView) {
        override fun onTouchEvent(event: MotionEvent): Boolean {
            return delegates.any { it.onTouchEvent(event) }
        }
    }

    private fun handleLayoutSwitch(checkedId: Int) {
        val container = binding.layoutDynamicDetailContainer
        container.removeAllViews()
        detailView = null

        if (checkedId == R.id.rb_none) {
            container.visibility = View.GONE
            binding.layoutCommonRepeatEnd.visibility = View.GONE
            hideKeyboard()
            return
        }

        container.visibility = View.VISIBLE
        binding.layoutCommonRepeatEnd.visibility = View.VISIBLE

        val layoutId = when (checkedId) {
            R.id.rb_daily -> R.layout.layout_repeat_daily
            R.id.rb_week -> R.layout.layout_repeat_weekly
            R.id.rb_month -> R.layout.layout_repeat_monthly
            R.id.rb_year -> R.layout.layout_repeat_yearly
            else -> null
        }

        layoutId?.let {
            detailView = layoutInflater.inflate(it, container, false)
            container.addView(detailView)
            setupDetailListeners(checkedId)
        }
    }

    private fun setupDetailListeners(checkedId: Int) {
        val v = detailView ?: return
        when (checkedId) {
            R.id.rb_daily -> {
                v.findViewById<EditText>(R.id.et_daily_interval)?.apply {
                    configureNumberInput(this)
                    addTextChangedListener(descriptionWatcher)
                }
            }
            R.id.rb_week -> {
                // 주간 전용 반복주기 보여지게하기
                v.findViewById<View>(R.id.layout_day_of_week)?.visibility = View.VISIBLE

                v.findViewById<EditText>(R.id.et_week_interval)?.apply {
                    configureNumberInput(this)
                    addTextChangedListener(descriptionWatcher)
                }
                val dayIds = listOf(R.id.cb_sun, R.id.cb_mon, R.id.cb_tue, R.id.cb_wed, R.id.cb_thu, R.id.cb_fri, R.id.cb_sat)
                dayIds.forEach { id ->
                    v.findViewById<CheckBox>(id)?.setOnCheckedChangeListener { _, _ -> updateFullDescription() }
                }

                // 오늘날짜 자동 추가
                 val today = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1
                 v.findViewById<CheckBox>(dayIds[today])?.isChecked = true
            }
            R.id.rb_month -> {
                val rgMonthly = v.findViewById<RadioGroup>(R.id.rg_monthly_detail)
                val gridDates = v.findViewById<GridLayout>(R.id.grid_monthly_dates)
                val rbOrdinal = v.findViewById<RadioButton>(R.id.rb_monthly_ordinal_day)
                val rbFixed = v.findViewById<RadioButton>(R.id.rb_monthly_day_fixed)

                val dayOfMonth = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
                rbFixed?.isChecked = true
                rbFixed?.text = "${dayOfMonth}일 마다 반복"
                rbOrdinal?.text = "${getOrdinalDayOfWeekText()} 마다 반복"

                gridDates?.let { setupDateGrid(it) }
                rgMonthly?.setOnCheckedChangeListener { _, checkedId ->
                    gridDates?.visibility = if (checkedId == R.id.rb_monthly_specific_date) View.VISIBLE else View.GONE
                    updateFullDescription()
                }
                v.findViewById<EditText>(R.id.et_month_interval)?.apply {
                    configureNumberInput(this)
                    addTextChangedListener(descriptionWatcher)
                }
            }
            R.id.rb_year -> {
                val rgYearly = v.findViewById<RadioGroup>(R.id.rg_yearly_detail)
                val gridMonths = v.findViewById<GridLayout>(R.id.grid_yearly_months)
                v.findViewById<RadioButton>(R.id.rb_yearly_day_fixed)?.isChecked = true
                gridMonths?.let { setupMonthGrid(it) }
                rgYearly?.setOnCheckedChangeListener { _, checkedId ->
                    gridMonths?.visibility = if (checkedId == R.id.rb_yearly_specific_date) View.VISIBLE else View.GONE
                    updateFullDescription()
                }
                v.findViewById<EditText>(R.id.et_year_interval)?.apply {
                    configureNumberInput(this)
                    addTextChangedListener(descriptionWatcher)
                }
            }
        }
    }

    private fun updateFullDescription() {
        val typeId = binding.rgRepeatOptions.checkedRadioButtonId
        if (typeId == R.id.rb_none) {
            binding.tvRepeatDescription.text = "일정 반복을 진행하지 않습니다."
            return
        }

        val interval = when (typeId) {
            R.id.rb_daily -> detailView?.findViewById<EditText>(R.id.et_daily_interval)?.text.toString()
            R.id.rb_week -> detailView?.findViewById<EditText>(R.id.et_week_interval)?.text.toString()
            R.id.rb_month -> detailView?.findViewById<EditText>(R.id.et_month_interval)?.text.toString()
            R.id.rb_year -> detailView?.findViewById<EditText>(R.id.et_year_interval)?.text.toString()
            else -> "1"
        }.ifEmpty { "1" }

        var detailInfo = ""
        when (typeId) {
            R.id.rb_week -> {
                val selectedDays = mutableListOf<String>()
                val dayNames = listOf("일", "월", "화", "수", "목", "금", "토")
                val dayIds = listOf(R.id.cb_sun, R.id.cb_mon, R.id.cb_tue, R.id.cb_wed, R.id.cb_thu, R.id.cb_fri, R.id.cb_sat)
                dayIds.forEachIndexed { i, id ->
                    if (detailView?.findViewById<CheckBox>(id)?.isChecked == true) selectedDays.add(dayNames[i])
                }
                if (selectedDays.isNotEmpty()) detailInfo = "${selectedDays.joinToString(", ")}요일"
            }
            R.id.rb_month -> {
                val v = detailView ?: return
                if (v.findViewById<RadioButton>(R.id.rb_monthly_specific_date)?.isChecked == true) {
                    val selectedDates = mutableListOf<Int>()
                    val grid = v.findViewById<GridLayout>(R.id.grid_monthly_dates)
                    for (i in 0 until (grid?.childCount ?: 0)) {
                        val cb = grid?.getChildAt(i) as? CheckBox
                        if (cb?.isChecked == true) selectedDates.add(cb.text.toString().toInt())
                    }
                    if (selectedDates.isNotEmpty()) detailInfo = "${selectedDates.sorted().joinToString(", ")}일"
                }
            }
            R.id.rb_year -> {
                val v = detailView ?: return
                if (v.findViewById<RadioButton>(R.id.rb_yearly_specific_date)?.isChecked == true) {
                    val selectedMonths = mutableListOf<String>()
                    val grid = v.findViewById<GridLayout>(R.id.grid_yearly_months)
                    for (i in 0 until (grid?.childCount ?: 0)) {
                        val cb = grid?.getChildAt(i) as? CheckBox
                        if (cb?.isChecked == true) selectedMonths.add(cb.text.toString())
                    }
                    if (selectedMonths.isNotEmpty()) detailInfo = "${selectedMonths.joinToString(", ")} 반복"
                }
            }
        }

        val unit = when(typeId) {
            R.id.rb_daily -> "일"
            R.id.rb_week -> "주"
            R.id.rb_month -> "개월"
            R.id.rb_year -> "년"
            else -> ""
        }
        val intervalText = if (interval == "1") "매$unit" else "${interval}${unit}마다"

        // 종료 부분에 선택된 날짜 포맷 반영
        val endText = when {
            binding.rbEndNever.isChecked -> "반복됩니다"
            binding.rbEndCount.isChecked -> "${binding.etEndCount.text.toString().ifEmpty { "1" }}회 반복됩니다"
            binding.rbEndDate.isChecked -> "${selectedEndDate?.format(dateFormatter)}까지 반복됩니다"
            else -> "반복됩니다"
        }

        binding.tvRepeatDescription.text = "$intervalText $detailInfo $endText".replace("\\s+".toRegex(), " ").trim()
    }

    private fun handleEndLayoutVisibility() {

        val checkedId = binding.rgEndOptions.checkedRadioButtonId
        setFadeVisibility(binding.layoutCountInput, checkedId == R.id.rb_end_count)
        binding.calendarContainer.isVisible = (checkedId == R.id.rb_end_date)

        if (checkedId != R.id.rb_end_count) {
            hideKeyboard() // 횟수 지정이 아니면 키보드 닫기
        }

        // 1. 횟수 지정 레이아웃 제어
        if (binding.rbEndCount.isChecked) {
            binding.rbEndCount.text = ""
            setFadeVisibility(binding.layoutCountInput, true)
        } else {
            binding.rbEndCount.text = "횟수 지정"
            setFadeVisibility(binding.layoutCountInput, false)
        }

        // 2. 종료 날짜 텍스트 및 캘린더 컨테이너 제어
        if (binding.rbEndDate.isChecked) {
            val formattedDate = selectedEndDate?.format(dateFormatter) ?: ""
            binding.rbEndDate.text = "종료 날짜"
            binding.tvEndDateValue.text = "$formattedDate 까지"
            binding.tvEndDateValue.visibility = View.VISIBLE
            binding.calendarContainer.visibility = View.VISIBLE
        } else {
            binding.rbEndDate.text = "종료 날짜"
            binding.tvEndDateValue.visibility = View.GONE
            binding.calendarContainer.visibility = View.GONE
        }
    }

    private fun setupCalendar() {
        val currentMonth = java.time.YearMonth.now()
        val startMonth = currentMonth.minusMonths(12) // 1년 전
        val endMonth = currentMonth.plusMonths(12)   // 1년 후 (총 2년)
        val firstDayOfWeek = java.time.DayOfWeek.SUNDAY

        binding.calendarPicker.setup(startMonth, endMonth, firstDayOfWeek)
        binding.calendarPicker.scrollToMonth(currentMonth)
        binding.tvCurrentMonth.text = monthFormatter.format(currentMonth)

        class DayViewContainer(view: View) : ViewContainer(view) {
            val textView = ItemCalendarDayAddscheduleBinding.bind(view).calendarDayText
            lateinit var day: CalendarDay

            init {
                view.setOnClickListener {
                    if (day.position == DayPosition.MonthDate) {
                        val oldDate = selectedEndDate
                        selectedEndDate = day.date

                        // 변경된 날짜 알림
                        binding.calendarPicker.notifyDateChanged(day.date)
                        oldDate?.let { binding.calendarPicker.notifyDateChanged(it) }

                        // 중요: 라디오 버튼 텍스트와 하단 전체 설명을 모두 갱신
                        handleEndLayoutVisibility()
                        updateFullDescription()
                    }
                }
            }
        }

        binding.calendarPicker.dayBinder = object : MonthDayBinder<DayViewContainer> {
            override fun create(view: View) = DayViewContainer(view)
            override fun bind(container: DayViewContainer, data: CalendarDay) {
                container.day = data
                container.textView.text = data.date.dayOfMonth.toString()

                if (data.position == DayPosition.MonthDate) {
                    container.textView.visibility = View.VISIBLE

                    if (data.date == selectedEndDate) {
                        // [선택된 날짜] 초록색 원 배경 + 하얀색 글씨
                        container.textView.setBackgroundResource(R.drawable.drawable_circle_green)
                        container.textView.setTextColor(android.graphics.Color.WHITE)
                    } else {
                        // [일반 날짜] 배경 없음 + 검정색 글씨 (또는 기본색)
                        container.textView.background = null
                        container.textView.setTextColor(
                            when (data.date.dayOfWeek) {
                                java.time.DayOfWeek.SUNDAY -> Color.RED
                                java.time.DayOfWeek.SATURDAY -> Color.BLUE
                                else -> resources.getColor(R.color.black, null)
                            }
                        )
                    }
                } else {
                    // 이번 달이 아닌 날짜들 숨김
                    container.textView.visibility = View.INVISIBLE
                }
            }
        }

        binding.calendarPicker.monthScrollListener = { month ->
            binding.tvCurrentMonth.text = monthFormatter.format(month.yearMonth)
        }
    }

    // --- 유틸리티 ---
    private fun setupDateGrid(grid: GridLayout) {
        grid.removeAllViews()
        grid.columnCount = 7
        for (i in 1..31) {
            val cb = CheckBox(requireContext()).apply {
                text = i.toString()
                buttonDrawable = null
                gravity = android.view.Gravity.CENTER
                setBackgroundResource(
                    if (i <= 28) R.drawable.bg_month_date_item_with_divider
                    else R.drawable.bg_month_date_item
                )
                applyMonthGridTextStyle(this, false)
                layoutParams = GridLayout.LayoutParams().apply {
                    width = dpToPx(42)
                    height = dpToPx(45)
                    setMargins(0, 0, 0, 0)
                }
                setOnCheckedChangeListener { buttonView, isChecked ->
                    applyMonthGridTextStyle(buttonView as CheckBox, isChecked)
                    updateFullDescription()
                }
            }
            grid.addView(cb)
        }
    }

    private fun setupMonthGrid(grid: GridLayout) {
        grid.removeAllViews()
        grid.columnCount = 6
        for (i in 1..12) {
            val cb = CheckBox(requireContext()).apply {
                text = "${i}월"
                buttonDrawable = null
                gravity = android.view.Gravity.CENTER
                setBackgroundResource(R.drawable.bg_month_item)
                applyMonthGridTextStyle(this, false)
                layoutParams = GridLayout.LayoutParams().apply {
                    width = dpToPx(49)
                    height = dpToPx(49)
                    setMargins(0, if (i <= 6) 0 else dpToPx(6), 0, if (i <= 6) dpToPx(6) else 0)
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                }
                setOnCheckedChangeListener { buttonView, isChecked ->
                    applyMonthGridTextStyle(buttonView as CheckBox, isChecked)
                    updateFullDescription()
                }
            }
            grid.addView(cb)
        }
    }

    private fun applyMonthGridTextStyle(checkBox: CheckBox, isChecked: Boolean) {
        val textAppearance = if (isChecked) {
            R.style.TextAppearance_App_Num_CaptionMd_Bold
        } else {
            R.style.TextAppearance_App_Num_CaptionMd_Medium
        }
        checkBox.setTextAppearance(textAppearance)
        checkBox.setTextColor(
            androidx.core.content.ContextCompat.getColorStateList(
                checkBox.context,
                R.color.selector_month_text
            )
        )
    }

    private fun getOrdinalDayOfWeekText(): String {
        val calendar = Calendar.getInstance()
        val dayNames = listOf("일요일", "월요일", "화요일", "수요일", "목요일", "금요일", "토요일")
        val dayOfWeekName = dayNames[calendar.get(Calendar.DAY_OF_WEEK) - 1]
        val ordinal = calendar.get(Calendar.DAY_OF_WEEK_IN_MONTH)
        val ordinalNames = listOf("첫 번째", "두 번째", "세 번째", "네 번째", "다섯 번째")
        return "${if (ordinal <= 5) ordinalNames[ordinal - 1] else ""} $dayOfWeekName"
    }

    private fun sendResultAndBack() {
        val typeId = binding.rgRepeatOptions.checkedRadioButtonId

        // 1. 반복 안 함일 경우
        if (typeId == R.id.rb_none) {
            parentFragmentManager.setFragmentResult("repeatKey", bundleOf(
                "selectedRepeat" to "반복 안함",
                "repeatInfo" to null
            ))
            parentFragmentManager.popBackStack()
            return
        }

        // 2. RepeatInfo 객체 생성
        val weeklyHasSelection = if (typeId == R.id.rb_week) {
            val dayIds = listOf(R.id.cb_sun, R.id.cb_mon, R.id.cb_tue, R.id.cb_wed, R.id.cb_thu, R.id.cb_fri, R.id.cb_sat)
            dayIds.any { detailView?.findViewById<CheckBox>(it)?.isChecked == true }
        } else true

        val monthlyHasSelection = if (
            typeId == R.id.rb_month &&
            detailView?.findViewById<RadioButton>(R.id.rb_monthly_specific_date)?.isChecked == true
        ) {
            hasCheckedItems(detailView?.findViewById(R.id.grid_monthly_dates))
        } else true

        val yearlyHasSelection = if (
            typeId == R.id.rb_year &&
            detailView?.findViewById<RadioButton>(R.id.rb_yearly_specific_date)?.isChecked == true
        ) {
            hasCheckedItems(detailView?.findViewById(R.id.grid_yearly_months))
        } else true

        if (!weeklyHasSelection || !monthlyHasSelection || !yearlyHasSelection) {
            sendNoneResultAndBack()
            return
        }

        val repeatType = when (typeId) {
            R.id.rb_daily -> "DAILY"
            R.id.rb_week -> "WEEKLY"
            R.id.rb_month -> "MONTHLY"
            R.id.rb_year -> "YEARLY"
            else -> "NONE"
        }

        // 간격(Interval) 추출
        val interval = when (typeId) {
            R.id.rb_daily -> detailView?.findViewById<EditText>(R.id.et_daily_interval)?.text.toString()
            R.id.rb_week -> detailView?.findViewById<EditText>(R.id.et_week_interval)?.text.toString()
            R.id.rb_month -> detailView?.findViewById<EditText>(R.id.et_month_interval)?.text.toString()
            R.id.rb_year -> detailView?.findViewById<EditText>(R.id.et_year_interval)?.text.toString()
            else -> "1"
        }.ifEmpty { "1" }.toInt()

        // 요일(daysOfWeek) 추출 (주간 반복일 때만)
        var daysOfWeekStr: String? = null
        var monthlyOption: String? = null
        var monthlyDays: String? = null
        var yearlyOption: String? = null
        var yearlyMonths: String? = null
        val referenceDayOfMonth = baseDate.dayOfMonth
        val referenceMonth = baseDate.monthValue
        val referenceDayOfWeek = when (baseDate.dayOfWeek.name) {
            "MONDAY" -> "MO"
            "TUESDAY" -> "TU"
            "WEDNESDAY" -> "WE"
            "THURSDAY" -> "TH"
            "FRIDAY" -> "FR"
            "SATURDAY" -> "SA"
            else -> "SU"
        }
        val referenceWeekOfMonth = ((baseDate.dayOfMonth - 1) / 7) + 1
        if (typeId == R.id.rb_week) {
            val selectedDays = mutableListOf<String>()
            val codes = listOf("SU", "MO", "TU", "WE", "TH", "FR", "SA")
            val dayIds = listOf(R.id.cb_sun, R.id.cb_mon, R.id.cb_tue, R.id.cb_wed, R.id.cb_thu, R.id.cb_fri, R.id.cb_sat)
            dayIds.forEachIndexed { i, id ->
                if (detailView?.findViewById<CheckBox>(id)?.isChecked == true) {
                    selectedDays.add(codes[i])
                }
            }
            if (selectedDays.isNotEmpty()) daysOfWeekStr = selectedDays.joinToString(",")
        } else if (typeId == R.id.rb_month) {
            val v = detailView
            monthlyOption = when {
                v?.findViewById<RadioButton>(R.id.rb_monthly_day_fixed)?.isChecked == true -> "FIXED_DAY"
                v?.findViewById<RadioButton>(R.id.rb_monthly_ordinal_day)?.isChecked == true -> "ORDINAL_DAY"
                v?.findViewById<RadioButton>(R.id.rb_monthly_specific_date)?.isChecked == true -> "SPECIFIC_DATE"
                else -> "FIXED_DAY"
            }
            if (monthlyOption == "SPECIFIC_DATE") {
                val grid = v?.findViewById<GridLayout>(R.id.grid_monthly_dates)
                val selectedDates = mutableListOf<Int>()
                for (i in 0 until (grid?.childCount ?: 0)) {
                    val cb = grid?.getChildAt(i) as? CheckBox
                    if (cb?.isChecked == true) selectedDates.add(cb.text.toString().toInt())
                }
                monthlyDays = selectedDates.sorted().joinToString(",").ifBlank { baseDate.dayOfMonth.toString() }
            }
        } else if (typeId == R.id.rb_year) {
            val v = detailView
            yearlyOption = when {
                v?.findViewById<RadioButton>(R.id.rb_yearly_day_fixed)?.isChecked == true -> "FIXED_DAY"
                v?.findViewById<RadioButton>(R.id.rb_yearly_ordinal_day)?.isChecked == true -> "ORDINAL_DAY"
                v?.findViewById<RadioButton>(R.id.rb_yearly_specific_date)?.isChecked == true -> "SPECIFIC_DATE"
                else -> "FIXED_DAY"
            }
            if (yearlyOption == "SPECIFIC_DATE") {
                val grid = v?.findViewById<GridLayout>(R.id.grid_yearly_months)
                val selectedMonths = mutableListOf<Int>()
                for (i in 0 until (grid?.childCount ?: 0)) {
                    val cb = grid?.getChildAt(i) as? CheckBox
                    if (cb?.isChecked == true) {
                        selectedMonths.add(cb.text.toString().replace("월", "").trim().toInt())
                    }
                }
                yearlyMonths = selectedMonths.sorted().joinToString(",").ifBlank { baseDate.monthValue.toString() }
            }
        }

        // 종료 조건 추출
        val endType = when {
            binding.rbEndNever.isChecked -> "NEVER"
            binding.rbEndCount.isChecked -> "COUNT"
            binding.rbEndDate.isChecked -> "DATE"
            else -> "NEVER"
        }
        val endCount = if (endType == "COUNT") {
            binding.etEndCount.text.toString().ifEmpty { "1" }.toInt()
        } else null

        val repeatEndDate = if (endType == "DATE") {
            selectedEndDate?.toString() // "yyyy-MM-dd"
        } else null

        // 3. 데이터 클래스 생성
        val info = RepeatInfo(
            repeatType = repeatType,
            repeatInterval = interval,
            daysOfWeek = daysOfWeekStr,
            monthlyOption = monthlyOption,
            monthlyDays = monthlyDays,
            yearlyOption = yearlyOption,
            yearlyMonths = yearlyMonths,
            referenceDayOfMonth = referenceDayOfMonth,
            referenceMonth = referenceMonth,
            referenceDayOfWeek = referenceDayOfWeek,
            referenceWeekOfMonth = referenceWeekOfMonth,
            endType = endType,
            endCount = endCount,
            repeatEndDate = repeatEndDate
        )

        // 4. 결과 전달
        val description = binding.tvRepeatDescription.text.toString()
        parentFragmentManager.setFragmentResult("repeatKey", bundleOf(
            "selectedRepeat" to description,
            "repeatInfo" to info  // 여기서 RepeatInfo 객체를 넘겨줌
        ))

        parentFragmentManager.popBackStack()
    }

    private fun hasCheckedItems(grid: GridLayout?): Boolean {
        for (i in 0 until (grid?.childCount ?: 0)) {
            val child = grid?.getChildAt(i) as? CheckBox
            if (child?.isChecked == true) return true
        }
        return false
    }

    private fun sendNoneResultAndBack() {
        parentFragmentManager.setFragmentResult("repeatKey", bundleOf(
            "selectedRepeat" to "반복 안함",
            "repeatInfo" to null
        ))
        parentFragmentManager.popBackStack()
    }

    private fun configureNumberInput(editText: EditText) {
        editText.setSelectAllOnFocus(false)
        editText.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                editText.post {
                    editText.setSelection(editText.text?.length ?: 0)
                }
            }
        }
        editText.setOnClickListener {
            editText.post {
                editText.setSelection(editText.text?.length ?: 0)
            }
        }
    }

    private fun clearCurrentInputFocus() {
        binding.root.findFocus()?.clearFocus()
        detailView?.findFocus()?.clearFocus()
        binding.etEndCount.clearFocus()
        binding.repeatToolbar.requestFocus()
    }

    private fun hideKeyboard() {
        clearCurrentInputFocus()
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view?.windowToken, 0)
    }

    private fun setupLegend() {
        val daysOfWeek = arrayOf("일", "월", "화", "수", "목", "금", "토")
        val legendLayout = binding.legendLayout.root as ViewGroup
        for (i in 0 until legendLayout.childCount) {
            (legendLayout.getChildAt(i) as? TextView)?.apply {
                text = daysOfWeek[i]
                // 가이드에 따른 주말 색상 처리 (선택)
                if (i == 0) setTextColor(Color.RED)
                else if (i == 6) setTextColor(Color.BLUE)
            }
        }
    }

    private fun updateDynamicTexts(v: View, type: String) {
        val dayOfMonth = baseDate.dayOfMonth
        val monthValue = baseDate.monthValue
        val dayOfWeekName = baseDate.format(DateTimeFormatter.ofPattern("E요일", Locale.KOREAN))

        // 몇 번째 요일인지 계산 (1~5)
        val ordinal = (dayOfMonth - 1) / 7 + 1
        val ordinalNames = listOf("첫 번째", "두 번째", "세 번째", "네 번째", "다섯 번째")
        val ordinalText = "${ordinalNames[ordinal - 1]} $dayOfWeekName"

        if (type == "MONTHLY") {
            v.findViewById<RadioButton>(R.id.rb_monthly_day_fixed)?.text = "${dayOfMonth}일마다 반복"
            v.findViewById<RadioButton>(R.id.rb_monthly_ordinal_day)?.text = "$ordinalText 마다 반복"
            v.findViewById<RadioButton>(R.id.rb_monthly_day_fixed)?.isChecked = true
        } else {
            v.findViewById<RadioButton>(R.id.rb_yearly_day_fixed)?.text = "${monthValue}월 ${dayOfMonth}일마다 반복"
            v.findViewById<RadioButton>(R.id.rb_yearly_ordinal_day)?.text = "${monthValue}월 $ordinalText 마다 반복"
            v.findViewById<RadioButton>(R.id.rb_yearly_day_fixed)?.isChecked = true
        }
    }

    private fun restorePreviousSettings(info: RepeatInfo) {
        // 1. 반복 유형 라디오 버튼 선택
        val typeRbId = when (info.repeatType) {
            "DAILY" -> R.id.rb_daily
            "WEEKLY" -> R.id.rb_week
            "MONTHLY" -> R.id.rb_month
            "YEARLY" -> R.id.rb_year
            else -> R.id.rb_none
        }
        binding.rgRepeatOptions.check(typeRbId)

        // 2. 세부 설정 복원
        binding.layoutDynamicDetailContainer.post {
            val v = detailView ?: return@post

            // 모든 케이스에서 공통적으로 repeatInterval 사용
            when (info.repeatType) {
                "DAILY" -> {
                    v.findViewById<EditText>(R.id.et_daily_interval)?.setText(info.repeatInterval.toString())
                }
                "WEEKLY" -> {
                    v.findViewById<EditText>(R.id.et_week_interval)?.setText(info.repeatInterval.toString())

                    // "MO,WE" -> [1, 3] 형태의 인덱스로 변환하여 체크박스 복구
                    val dayMap = mapOf("SU" to 0, "MO" to 1, "TU" to 2, "WE" to 3, "TH" to 4, "FR" to 5, "SA" to 6)
                    val dayIds = listOf(R.id.cb_sun, R.id.cb_mon, R.id.cb_tue, R.id.cb_wed, R.id.cb_thu, R.id.cb_fri, R.id.cb_sat)

                    // 기본 체크 해제 후 저장된 요일만 체크
                    dayIds.forEach { v.findViewById<CheckBox>(it)?.isChecked = false }
                    info.daysOfWeek?.split(",")?.forEach { dayCode ->
                        dayMap[dayCode.trim()]?.let { index ->
                            v.findViewById<CheckBox>(dayIds[index])?.isChecked = true
                        }
                    }
                }
                "MONTHLY" -> {
                    v.findViewById<EditText>(R.id.et_month_interval)?.setText(info.repeatInterval.toString())
                    updateDynamicTexts(v, "MONTHLY")
                    when (info.monthlyOption) {
                        "ORDINAL_DAY" -> v.findViewById<RadioButton>(R.id.rb_monthly_ordinal_day)?.isChecked = true
                        "SPECIFIC_DATE" -> {
                            v.findViewById<RadioButton>(R.id.rb_monthly_specific_date)?.isChecked = true
                            v.findViewById<GridLayout>(R.id.grid_monthly_dates)?.visibility = View.VISIBLE
                            val selectedDays = info.monthlyDays?.split(",")?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList()
                            val grid = v.findViewById<GridLayout>(R.id.grid_monthly_dates)
                            for (i in 0 until (grid?.childCount ?: 0)) {
                                val cb = grid?.getChildAt(i) as? CheckBox
                                cb?.isChecked = cb?.text?.toString()?.toIntOrNull() in selectedDays
                            }
                        }
                        else -> v.findViewById<RadioButton>(R.id.rb_monthly_day_fixed)?.isChecked = true
                    }
                    // 월간 세부 타입(고정일/요일)은 현재 모델에 없으므로
                    // 필요시 baseDate 기준으로 기본 라디오 버튼을 체크하게 둡니다.
                }
                "YEARLY" -> {
                    v.findViewById<EditText>(R.id.et_year_interval)?.setText(info.repeatInterval.toString())
                    updateDynamicTexts(v, "YEARLY")
                    when (info.yearlyOption) {
                        "ORDINAL_DAY" -> v.findViewById<RadioButton>(R.id.rb_yearly_ordinal_day)?.isChecked = true
                        "SPECIFIC_DATE" -> {
                            v.findViewById<RadioButton>(R.id.rb_yearly_specific_date)?.isChecked = true
                            v.findViewById<GridLayout>(R.id.grid_yearly_months)?.visibility = View.VISIBLE
                            val selectedMonths = info.yearlyMonths?.split(",")?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList()
                            val grid = v.findViewById<GridLayout>(R.id.grid_yearly_months)
                            for (i in 0 until (grid?.childCount ?: 0)) {
                                val cb = grid?.getChildAt(i) as? CheckBox
                                val month = cb?.text?.toString()?.replace("월", "")?.trim()?.toIntOrNull()
                                cb?.isChecked = month in selectedMonths
                            }
                        }
                        else -> v.findViewById<RadioButton>(R.id.rb_yearly_day_fixed)?.isChecked = true
                    }
                }
            }
        }

        // 3. 종료 조건 복원
        when (info.endType) {
            "NEVER" -> binding.rgEndOptions.check(R.id.rb_end_never)
            "COUNT" -> {
                binding.rgEndOptions.check(R.id.rb_end_count)
                binding.etEndCount.setText(info.endCount?.toString() ?: "1")
            }
            "DATE" -> {
                binding.rgEndOptions.check(R.id.rb_end_date)
                // String(yyyy-MM-dd) -> LocalDate 변환
                info.repeatEndDate?.let {
                    selectedEndDate = LocalDate.parse(it)
                    binding.calendarPicker.notifyDateChanged(selectedEndDate!!)
                    val endMonth = YearMonth.from(selectedEndDate)
                    binding.calendarPicker.scrollToMonth(endMonth)
                    binding.tvCurrentMonth.text = monthFormatter.format(endMonth)
                }
            }
        }

        handleEndLayoutVisibility()
        updateFullDescription()
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

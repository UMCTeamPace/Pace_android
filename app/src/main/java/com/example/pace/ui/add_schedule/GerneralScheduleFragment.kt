package com.example.pace.ui.add_schedule

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.pace.R
import com.example.pace.databinding.FragmentGeneralScheduleBinding
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.view.MonthDayBinder
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class GeneralScheduleFragment : Fragment() {

    private var _binding: FragmentGeneralScheduleBinding? = null
    private val binding get() = _binding!!

    private var isEditingStartTime: Boolean = true

    private var isAllDay = true

    private var startDate: LocalDate? = null
    private var endDate: LocalDate? = null

    private val routeSearchLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            val name = data?.getStringExtra("placeName")
            val id = data?.getStringExtra("placeId")
            //여기서 업데이트! 받아온 정보 여기서 써요!
            Toast.makeText(context, "선택된 장소: $name, 선택된 아이디: $id", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFragmentResultListener("repeatKey") { _, bundle ->
            val result = bundle.getString("selectedRepeat")
            binding.tvRepeatStatus.text = result
        }

    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentGeneralScheduleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupCalendar()         // 기본 셋업
        setupLegend()           // 요일 셋업
        setupMonthNavigation()  // 화살표 셋업
        initTimePickers()
        updateTimeVisibility()

        val selectedDate = arguments?.getString("selected_date")
        val mode = arguments?.getString("mode")
        if (selectedDate != null) {
            val message = "날짜: $selectedDate\n모드: $mode"
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()

            // 팁: 받아온 날짜를 화면의 날짜 텍스트뷰(예: btnStartDate)에도 바로 넣어주면 좋습니다.
            // binding.btnStartDate.text = selectedDate
        }

        binding.layoutScheduleName.setOnClickListener {
            binding.etScheduleName.requestFocus()

            val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(binding.etScheduleName, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }

        binding.btnConfirm.setOnClickListener {
            val scheduleName = binding.etScheduleName.text.toString().trim()

            if (scheduleName.isEmpty()) {
                Toast.makeText(context, "일정명을 입력해 주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!isAllDay) {
                val start = binding.tvStartTime.text.toString()
                val end = binding.tvEndTime.text.toString()
                val isSameDay = startDate != null && endDate != null && startDate == endDate

                // [수정] 같은 날짜일 때만 시간 선후 관계를 엄격하게 체크
                if (isSameDay && isTimeAfter(start, end)) {
                    Toast.makeText(context, "종료 시간이 시작 시간보다 빨라야 합니다.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            Toast.makeText(context, "일정이 저장되었습니다.", Toast.LENGTH_SHORT).show()
            if (parentFragmentManager.backStackEntryCount > 0) {
                parentFragmentManager.popBackStack()
            } else {
                requireActivity().finish()
            }
        }

        binding.btnCancel.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("작성 취소")
                .setMessage("작성 중인 내용을 삭제하고 메인 화면으로 돌아갈까요?")
                .setPositiveButton("확인") { _, _ ->
                    if (parentFragmentManager.backStackEntryCount > 0) {
                        parentFragmentManager.popBackStack()
                    } else {
                        requireActivity().finish()
                    }
                }
                .setNegativeButton("계속 작성", null)
                .show()
        }

        setupKeyboardVisibilityListener()

        setFragmentResultListener("repeatKey") { _, bundle ->
            val result = bundle.getString("selectedRepeat")
            binding.tvRepeatStatus.text = result
        }


        binding.btnRepeat.setOnClickListener {
            val repeatFragment = ScheduleRepeatFragment()

            requireActivity().supportFragmentManager.beginTransaction()
                .setCustomAnimations(
                    android.R.anim.slide_in_left,
                    android.R.anim.slide_out_right,
                    android.R.anim.slide_in_left,
                    android.R.anim.slide_out_right
                )
                .replace(android.R.id.content, repeatFragment)
                .addToBackStack(null)
                .commit()
        }

        val dateClickAction = View.OnClickListener {
            // 하루종일 여부와 상관없이 날짜는 항상 수정 가능해야 하므로 캘린더를 엽니다.
            // 단, 캘린더가 열릴 때 시간 피커는 확실히 닫습니다.
            showCalendar()
        }

        binding.btnStartDate.setOnClickListener(dateClickAction)
        binding.btnEndDate.setOnClickListener(dateClickAction)

        // 시간 텍스트 클릭 시 (하루종일이 아닐 때만 반응)
        // 시간 텍스트 클릭 시
        binding.tvStartTime.setOnClickListener {
            if (isAllDay) return@setOnClickListener
            isEditingStartTime = true
            updateTimeVisibility() // [수정] 피커를 보여주기 전에 색상부터 즉시 변경
            showTimePicker()
            binding.calendarContainer.visibility = View.GONE
        }

        binding.tvEndTime.setOnClickListener {
            if (isAllDay) return@setOnClickListener
            isEditingStartTime = false
            updateTimeVisibility() // [수정] 즉시 초록색 불 켜기
            showTimePicker()
            binding.calendarContainer.visibility = View.GONE
        }


        binding.viewColorDot.setOnClickListener {
            if (binding.layoutColorSelector.visibility == View.GONE) {
                binding.layoutColorSelector.visibility = View.VISIBLE
                binding.calendarPicker.visibility = View.GONE
                binding.timePickerContainer.visibility = View.GONE
            } else {
                binding.layoutColorSelector.visibility = View.GONE
            }
        }


        val colorList = listOf(
            ColorItem(R.color.schedule_5,"#DC354B"),
            ColorItem(R.color.route_line_3, "#D8643F"),
            ColorItem(R.color.route_suin_bundang, "#FFBB00"),
            ColorItem(R.color.route_branch_bus, "#53B332"),
            ColorItem(R.color.schedule_14, "#51AEED"),
            ColorItem(R.color.schedule_12, "#2A4ABF"),
            ColorItem(R.color.schedule_8, "#5F46DD"),
            ColorItem(R.color.route_line_8,"#F14C82"),
            ColorItem(R.color.gray_600, "#666666")
        )


        val colorAdapter = ColorAdapter(colorList) { selectedColor ->
            changeSelectedColor(selectedColor)
        }

        binding.rvColors.apply {
            adapter = colorAdapter
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)

            // ViewPager2와의 터치 간섭 해결
            addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
                override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                    when (e.action) {
                        MotionEvent.ACTION_DOWN -> {
                            rv.parent.requestDisallowInterceptTouchEvent(true)
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            rv.parent.requestDisallowInterceptTouchEvent(false)
                        }
                    }
                    return false
                }
                override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {}
                override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
            })
        }

        binding.addscheMyPhoneIv.setOnClickListener {
            animateLayoutChange()
            isAllDay = !isAllDay

            if (isAllDay) {
                binding.addscheMyPhoneIv.setImageResource(R.drawable.ic_toggle_selected)
                // 하루종일 켜지면 피커들을 다 닫음
                binding.calendarContainer.visibility = View.GONE
                binding.timePickerContainer.visibility = View.GONE
            } else {
                binding.addscheMyPhoneIv.setImageResource(R.drawable.ic_toggle_unselected)

                // [핵심 수정] 캘린더가 닫혀있을 때만 시간 피커를 보여줌 (공존 방지)
                if (binding.calendarContainer.visibility == View.GONE) {
                    showTimePicker()
                }
            }
            updateTimeVisibility()
        }

        binding.btnRoute.setOnClickListener {
            val intent = android.content.Intent(requireContext(), com.example.pace.ui.main.MainActivity::class.java).apply {
                putExtra("ACTION_MODE", "SCHEDULE")
            }
            routeSearchLauncher.launch(intent)
        }

        // 오른쪽 이동 버튼
        binding.btnNextMonth.setOnClickListener {
            binding.calendarPicker.findFirstVisibleMonth()?.let {
                binding.calendarPicker.smoothScrollToMonth(it.yearMonth.plusMonths(1))
            }
        }

        // 왼쪽 이동 버튼
        binding.btnPrevMonth.setOnClickListener {
            binding.calendarPicker.findFirstVisibleMonth()?.let {
                binding.calendarPicker.smoothScrollToMonth(it.yearMonth.minusMonths(1))
            }
        }

        binding.btnRemindalarm.setOnClickListener {
            val fragment = AlarmScheduleFragment()

            // 1. requireActivity().supportFragmentManager를 써야 액티비티 전체를 씁니다.
            // 2. replace 대상은 반복 버튼과 동일하게 android.R.id.content 혹은 R.id.add_schedule_root_layout
            requireActivity().supportFragmentManager.beginTransaction()
                .setCustomAnimations(
                    android.R.anim.slide_in_left,
                    android.R.anim.slide_out_right,
                    android.R.anim.slide_in_left,
                    android.R.anim.slide_out_right
                )
                .replace(android.R.id.content, fragment)
                .addToBackStack(null)
                .commit()
        }
        // [추가] AlarmScheduleFragment에서 보낸 결과 수신
        parentFragmentManager.setFragmentResultListener("scheduleAlarmKey", viewLifecycleOwner) { _, bundle ->
            val selectedAlarm = bundle.getString("selectedAlarm")

            if (!selectedAlarm.isNullOrEmpty() && selectedAlarm != "일정 알림 안함") {
                // 알람이 설정된 경우: 검정색 텍스트로 변경
                binding.tvRemindStatus.text = selectedAlarm
                binding.tvRemindStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
            } else {
                // 알람이 없는 경우: 회색 텍스트로 변경
                binding.tvRemindStatus.text = "일정 알림 안함"
                binding.tvRemindStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray_500))
            }
        }


        val currentMonth = java.time.YearMonth.now()
        val startMonth = currentMonth.minusMonths(12) // 1년 전부터
        val endMonth = currentMonth.plusMonths(12)   // 1년 후까지
        val firstDayOfWeek = java.time.DayOfWeek.SUNDAY // 일요일 시작

        binding.calendarPicker.setup(startMonth, endMonth, firstDayOfWeek)
        binding.calendarPicker.scrollToMonth(currentMonth) // 현재 달로 이동

        binding.calendarPicker.dayBinder = object : MonthDayBinder<DayViewContainer> {
            override fun create(view: View) = DayViewContainer(view) { date ->
                selectDate(date)
            }
            override fun bind(container: DayViewContainer, day: CalendarDay) {
                container.currentDay = day
                val date = day.date
                val textView = container.textView
                val root = container.rootLayout

                textView.text = date.dayOfMonth.toString()
                if (day.position != com.kizitonwose.calendar.core.DayPosition.MonthDate) {
                    textView.setTextColor(Color.LTGRAY)
                }else{
                    when {
                        // 시작일/종료일 동일 (원형)
                        date == startDate && (endDate == null || endDate == startDate) -> {
                            textView.setTextColor(Color.WHITE)
                            textView.setBackgroundResource(R.drawable.drawable_circle_green)
                            root.background = null
                        }
                        // 기간 시작점
                        date == startDate -> {
                            textView.setTextColor(Color.WHITE)
                            textView.setBackgroundResource(R.drawable.drawable_circle_green)
                            root.setBackgroundResource(R.drawable.bg_calendar_range_start)
                        }
                        // 기간 종료점
                        date == endDate -> {
                            textView.setTextColor(Color.WHITE)
                            textView.setBackgroundResource(R.drawable.drawable_circle_green)
                            root.setBackgroundResource(R.drawable.bg_calendar_range_end)
                        }
                        // 기간 사이 (연두색 배경 적용)
                        startDate != null && endDate != null && date.isAfter(startDate) && date.isBefore(endDate) -> {
                            textView.setTextColor(Color.BLACK)
                            textView.background = null
                            // 여기에 @color/semantic_info가 적용된 drawable 연결
                            root.setBackgroundResource(R.drawable.bg_calendar_range_middle)
                        }
                        else -> {
                            textView.setTextColor(Color.BLACK)
                            textView.background = null
                            root.background = null
                        }
                    }
                    updateDaySelectionUI(container, day)
                }
            }
        }

        binding.btnCalendar.setOnClickListener {
            val selectCalendarFragment = SelectCalendarFragment() // 프래그먼트 이름 확인 필요

            requireActivity().supportFragmentManager.beginTransaction()
                .setCustomAnimations(
                    android.R.anim.slide_in_left,
                    android.R.anim.slide_out_right,
                    android.R.anim.slide_in_left,
                    android.R.anim.slide_out_right
                )
                // 다른 버튼들과 마찬가지로 최상위 컨테이너(android.R.id.content)를 교체
                .replace(android.R.id.content, selectCalendarFragment)
                .addToBackStack(null)
                .commit()
        }

        // [추가 선택사항] SelectCalendarFragment에서 돌아올 때 결과 수신
        // GeneralScheduleFragment의 onViewCreated 내부
        // GeneralScheduleFragment의 onViewCreated 내부
        parentFragmentManager.setFragmentResultListener("calendarSelectKey", viewLifecycleOwner) { _, bundle ->
            val calendarName = bundle.getString("selectedCalendarName")
            val calendarColor = bundle.getInt("selectedCalendarColor") // 시스템에서 가져온 Int 색상값

            // 1. [핵심] 상단 일정명 옆의 대표 색상 점 업데이트
            binding.viewColorDot.backgroundTintList = android.content.res.ColorStateList.valueOf(calendarColor)

            // 2. 하단 캘린더 선택 버튼 내의 텍스트와 작은 점 업데이트
            binding.tvCalendarStatus.text = calendarName
        }
    }

    // 예시: 초기 로드 시점 (onViewCreated 안에서 호출)
    private fun setupDefaultCalendar() {
        // 실제로는 저장된 Preference나 DB에서 가져온 값을 사용하세요.
        val defaultColor = ContextCompat.getColor(requireContext(), R.color.schedule_5)
        val defaultName = "내 휴대전화"

        binding.viewColorDot.backgroundTintList = android.content.res.ColorStateList.valueOf(defaultColor)
        binding.tvCalendarStatus.text = defaultName
    }

    private fun changeSelectedColor(colorStr: String) {
        val color = Color.parseColor(colorStr)
        binding.viewColorDot.backgroundTintList = ColorStateList.valueOf(color)
        binding.layoutColorSelector.visibility = View.GONE
    }

    private fun initTimePickers() {

        binding.pickerHour.apply {
            minValue = 0
            maxValue = 23
            setFormatter { String.format("%02d", it) }
            wrapSelectorWheel = true
        }

        // [수정] 5분 단위 설정
        val minutes = Array(12) { i -> String.format("%02d", i * 5) } // ["00", "05", "10", ..., "55"]
        binding.pickerMinute.apply {
            minValue = 0
            maxValue = 11 // 0부터 11까지 총 12개
            displayedValues = minutes // 실제 화면에 보여질 텍스트 연결
            wrapSelectorWheel = true
        }

        val timeChangeListener = NumberPicker.OnValueChangeListener { _, _, _ ->
            val hour = binding.pickerHour.value
            // [수정] 실제 분 계산: 선택된 인덱스 * 5
            val minute = binding.pickerMinute.value * 5
            val formattedTime = String.format("%02d:%02d", hour, minute)

            val isSameDay = startDate != null && endDate != null && startDate == endDate

            if (isEditingStartTime) {
                binding.tvStartTime.text = formattedTime
                if (isSameDay) {
                    val endTime = binding.tvEndTime.text.toString()
                    if (isTimeAfter(formattedTime, endTime)) {
                        // 시작 시간이 종료 시간보다 늦으면 종료 시간을 1시간 뒤로
                        val newEndHour = if (hour < 23) hour + 1 else 23
                        binding.tvEndTime.text = String.format("%02d:%02d", newEndHour, minute)
                    }
                }
            } else {
                binding.tvEndTime.text = formattedTime
            }
            updateTimeVisibility()
        }

        binding.pickerHour.setOnValueChangedListener(timeChangeListener)
        binding.pickerMinute.setOnValueChangedListener(timeChangeListener)
    }

    private fun showCalendar() {
        animateLayoutChange()
        binding.calendarContainer.visibility = View.VISIBLE
        binding.timePickerContainer.visibility = View.GONE // 시간 피커 강제 종료
        binding.layoutColorSelector.visibility = View.GONE

        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(view?.windowToken, 0)
    }

    private fun showTimePicker() {
        if (isAllDay) return
        animateLayoutChange()
        binding.timePickerContainer.visibility = View.VISIBLE
        binding.calendarContainer.visibility = View.GONE

        val timeText = if (isEditingStartTime) {
            binding.tvStartTime.text.toString()
        } else {
            binding.tvEndTime.text.toString()
        }

        try {
            val parts = timeText.split(":")
            if (parts.size == 2) {
                val h = parts[0].trim().toInt()
                val m = parts[1].trim().toInt()

                binding.pickerHour.value = h
                // [수정] 분을 5로 나눠서 인덱스 값으로 설정 (예: 15분 -> index 3)
                binding.pickerMinute.value = (m / 5).coerceIn(0, 11)
            }
        } catch (e: Exception) {
            binding.pickerHour.value = 10
            binding.pickerMinute.value = 0
        }
    }
    private fun updateTimeVisibility() {
        if (isAllDay) {
            binding.tvStartTime.visibility = View.GONE
            binding.tvEndTime.visibility = View.GONE
            binding.timePickerContainer.visibility = View.GONE
        } else {
            binding.tvStartTime.visibility = View.VISIBLE
            binding.tvEndTime.visibility = View.VISIBLE

            val highlightColor = Color.parseColor("#8BC34A")
            val defaultColor = Color.BLACK

            // 현재 편집 중인 시간에만 '불'이 들어오게 설정
            if (isEditingStartTime) {
                binding.tvStartTime.setTextColor(highlightColor)
                binding.tvEndTime.setTextColor(defaultColor)
                binding.tvEndTime.setTypeface(null, Typeface.NORMAL)
            } else {
                binding.tvStartTime.setTextColor(defaultColor)
                binding.tvStartTime.setTypeface(null, Typeface.NORMAL)

                binding.tvEndTime.setTextColor(highlightColor)
            }
        }
    }

    private fun setupKeyboardVisibilityListener() {
        val rootView = binding.root
        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            val rect = android.graphics.Rect()
            rootView.getWindowVisibleDisplayFrame(rect)

            val screenHeight = rootView.rootView.height
            val keypadHeight = screenHeight - rect.bottom

            if (keypadHeight > screenHeight * 0.15) {
                binding.layoutBottomButtons.visibility = View.GONE
            } else {
                binding.layoutBottomButtons.visibility = View.VISIBLE
            }
        }
    }

    private fun isTimeAfter(t1: String, t2: String): Boolean {
        val s = t1.split(":").map { it.trim().toInt() }
        val e = t2.split(":").map { it.trim().toInt() }

        val sMin = s[0] * 60 + s[1]
        val eMin = e[0] * 60 + e[1]

        return sMin > eMin
    }

    private fun selectDate(date: LocalDate) {
        if (startDate != null && endDate == null) {
            if (date.isBefore(startDate)) {
                startDate = date
            } else {
                endDate = date
                // [추가] 날짜 선택 완료 시점
                onDateSelectionComplete()
            }
        } else {
            startDate = date
            endDate = null
            // 만약 '하루종일'이 꺼져있는데 시작일만 찍어도 시간을 설정하게 하고 싶다면 여기서도 호출 가능하지만,
            // 보통은 종료일까지 선택된 후에 시간을 설정하는 것이 자연스럽습니다.
        }

        binding.calendarPicker.notifyCalendarChanged()
        updateDateDisplay()
    }
    private fun onDateSelectionComplete() {
        animateLayoutChange()
        if (isAllDay) {
            binding.calendarContainer.visibility = View.GONE
        } else {
            binding.calendarContainer.visibility = View.GONE
            isEditingStartTime = true // 기본 포커스를 시작 시간으로 설정
            updateTimeVisibility()     // 색상 반영
            showTimePicker()
        }
    }
    private fun updateDateDisplay() {
        val formatter = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)
        val highlightColor = Color.parseColor("#8BC34A") // 강조 초록색
        val defaultColor = Color.BLACK

        // 1. 시작일 업데이트
        startDate?.let {
            val tvStart = binding.btnStartDate.findViewById<TextView>(R.id.tv_start_date)
            tvStart.text = it.format(formatter)

            // 시작일이 선택되었고 종료일이 아직 없다면 다음 타겟인 종료일을 강조하기 위해 시작일은 검정으로
            if (endDate == null) {
                tvStart.setTextColor(defaultColor)
            }
        } ?: run {
            // 시작일 선택 전에는 시작일 텍스트 강조
            binding.btnStartDate.findViewById<TextView>(R.id.tv_start_date).setTextColor(highlightColor)
        }

        // 2. 종료일 업데이트
        val endToShow = endDate ?: startDate
        val tvEnd = binding.btnEndDate.findViewById<TextView>(R.id.tv_end_date)

        endToShow?.let {
            tvEnd.text = it.format(formatter)

            // 시작일만 있고 종료일이 아직 없는 상태라면 종료일 텍스트를 초록색으로 강조
            if (startDate != null && endDate == null) {
                tvEnd.setTextColor(highlightColor)
            } else {
                tvEnd.setTextColor(defaultColor)
            }
        }
    }

    // 1. 요일 레이아웃 (일~토) 초기화
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

    // 2. 캘린더 설정 및 스크롤 리스너
    private fun setupCalendar() {
        val currentMonth = java.time.YearMonth.now()
        val startMonth = currentMonth.minusMonths(12) // 1년 전
        val endMonth = currentMonth.plusMonths(12)   // 1년 후 (총 2년)
        val firstDayOfWeek = java.time.DayOfWeek.SUNDAY

        binding.calendarPicker.setup(startMonth, endMonth, firstDayOfWeek)
        binding.calendarPicker.scrollToMonth(currentMonth)

        // 스크롤 시 상단 텍스트(2026년 2월) 업데이트
        binding.calendarPicker.monthScrollListener = { month ->
            val titleFormatter = DateTimeFormatter.ofPattern("yyyy년 M월", Locale.KOREAN)
            binding.tvCurrentMonth.text = month.yearMonth.format(titleFormatter)
        }
    }

    // 3. 상단 화살표 버튼 클릭 리스너
    private fun setupMonthNavigation() {
        binding.btnNextMonth.setOnClickListener {
            binding.calendarPicker.findFirstVisibleMonth()?.let {
                binding.calendarPicker.smoothScrollToMonth(it.yearMonth.plusMonths(1))
            }
        }
        binding.btnPrevMonth.setOnClickListener {
            binding.calendarPicker.findFirstVisibleMonth()?.let {
                binding.calendarPicker.smoothScrollToMonth(it.yearMonth.minusMonths(1))
            }
        }
    }

    private fun updateDaySelectionUI(container: DayViewContainer, day: CalendarDay) {
        val date = day.date
        val textView = container.textView
        val root = container.rootLayout

        textView.background = null
        textView.backgroundTintList = null
        root.background = null

        if (day.position != com.kizitonwose.calendar.core.DayPosition.MonthDate) {
            textView.setTextColor(Color.LTGRAY)
        } else {
            val colorPrimary300 = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.primary_300)
            val verticalInset = (8 * resources.displayMetrics.density).toInt()

            when {
                // [CASE 1] 시작일과 종료일이 모두 선택되었고, 두 날짜가 서로 다를 때만 막대 표시
                startDate != null && endDate != null && startDate != endDate -> {
                    when (date) {
                        startDate -> {
                            textView.setBackgroundResource(R.drawable.drawable_circle_green)
                            textView.backgroundTintList = ColorStateList.valueOf(colorPrimary300)
                            val startBg = androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.bg_calendar_range_start)
                            root.background = android.graphics.drawable.InsetDrawable(startBg, 0, verticalInset, 0, verticalInset)
                        }
                        endDate -> {
                            textView.setBackgroundResource(R.drawable.drawable_circle_green)
                            textView.backgroundTintList = ColorStateList.valueOf(colorPrimary300)
                            val endBg = androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.bg_calendar_range_end)
                            root.background = android.graphics.drawable.InsetDrawable(endBg, 0, verticalInset, 0, verticalInset)
                        }
                        else -> {
                            if (date.isAfter(startDate) && date.isBefore(endDate)) {
                                val middleBg = androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.bg_calendar_range_middle)
                                root.background = android.graphics.drawable.InsetDrawable(middleBg, 0, verticalInset, 0, verticalInset)
                            }
                        }
                    }
                    textView.setTextColor(Color.BLACK)
                }

                // [CASE 2] 시작일만 선택되었거나, 시작일과 종료일이 같은 날짜일 때 (원만 표시)
                date == startDate || date == endDate -> {
                    textView.setTextColor(Color.BLACK)
                    textView.setBackgroundResource(R.drawable.drawable_circle_green)
                    textView.backgroundTintList = ColorStateList.valueOf(colorPrimary300)
                    root.background = null // 막대 제거
                }

                else -> {
                    textView.setTextColor(Color.BLACK)
                }
            }
        }
    }

    // 마진을 조절하여 배경 높이를 깎는 보조 함수
    private fun applySelectionMargin(view: View, isFullHeight: Boolean) {
        val params = view.layoutParams as ViewGroup.MarginLayoutParams
        if (isFullHeight) {
            params.topMargin = 0
            params.bottomMargin = 0
        } else {
            // XML의 paddingTop/Bottom이 4dp라면,
            // 배경이 그만큼 깎이도록 마진을 4dp(픽셀로 변환)만큼 줍니다.
            val margin = (4 * resources.displayMetrics.density).toInt()
            params.topMargin = margin
            params.bottomMargin = margin
        }
        view.layoutParams = params
    }

    private fun animateLayoutChange() {
        // 뷰의 크기, 위치, 가시성 변화를 부드럽게 처리합니다.
        android.transition.TransitionManager.beginDelayedTransition(binding.root as ViewGroup,
            android.transition.AutoTransition().apply {
                duration = 200 // 애니메이션 속도 (0.2초)
            }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class DayViewContainer(view: View, val onDateSelected: (LocalDate) -> Unit) : com.kizitonwose.calendar.view.ViewContainer(view) {
    val rootLayout: androidx.constraintlayout.widget.ConstraintLayout = view.findViewById(R.id.root_layout)
    val textView: android.widget.TextView = view.findViewById(R.id.calendarDayText)

    // date를 여기서 초기화하지 말고, 클릭 시점에 bind된 값을 사용하게 합니다.
    var currentDay: CalendarDay? = null

    init {
        view.setOnClickListener {
            currentDay?.let {
                if (it.position == com.kizitonwose.calendar.core.DayPosition.MonthDate) {
                    onDateSelected(it.date)
                }
            }
        }
    }
}
package com.example.pace.ui.add_schedule

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
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
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.pace.R
import com.example.pace.databinding.FragmentGeneralScheduleBinding
import com.example.pace.ui.main.calendar.ScheduleViewModel
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.view.MonthDayBinder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.example.pace.data.model.request.PlaceRequest
import com.example.pace.data.model.request.RepeatInfo
import com.example.pace.data.viewmodel.SettingsViewModel
import com.example.pace.ui.onboarding.CalendarSelectFragment
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

@AndroidEntryPoint
class GeneralScheduleFragment : Fragment() {

    private var _binding: FragmentGeneralScheduleBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ScheduleViewModel by activityViewModels()


    private var isEditingStartTime: Boolean = true

    private var isAllDay = true

    private var startDate: LocalDate? = null
    private var endDate: LocalDate? = null
    private var selectedColorHex: String = "#53B332" // 기본 색상

    private var selectedPlaceId: String? = null
    private var selectedPlaceName: String? = null
    private var selectedLat: Double = 0.0
    private var selectedLng: Double = 0.0

    private var currentSelectedAlarms: IntArray? = null
    private var currentSelectedCalendarId: Long? = null
    private var currentSelectedCalendarName: String? = null

    private val dateFormatter = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)
    val colorInt = android.graphics.Color.parseColor(selectedColorHex)
    private var currentRepeatInfo: RepeatInfo? = null

    private val settingsViewModel: SettingsViewModel by viewModels()

    private val routeSearchLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            selectedPlaceName = data?.getStringExtra("placeName")
            selectedPlaceId = data?.getStringExtra("placeId")
            selectedLat = data?.getDoubleExtra("placeLat", 0.0) ?: 0.0
            selectedLng = data?.getDoubleExtra("placeLng", 0.0) ?: 0.0

            // UI 반영: XML에 정의된 정확한 ID인 tv_location_status를 사용합니다.
            binding.tvLocationStatus.apply {
                text = selectedPlaceName
                setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
            }

            Toast.makeText(context, "장소 선택: $selectedPlaceName", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        setupCalendar()
        setupLegend()
        setupMonthNavigation()
        initTimePickers()
        updateTimeVisibility()
        observeUserSettings()

        if (currentSelectedAlarms == null || currentSelectedCalendarId == null) {
            viewLifecycleOwner.lifecycleScope.launch {
                // first()를 사용하여 최초 1회만 가져오고 연결을 끊습니다 (덮어쓰기 방지)
                val settings = settingsViewModel.userSettings.filterNotNull().first()

                settings.let {
                    // 알람 초기화
                    if (currentSelectedAlarms == null) {
                        currentSelectedAlarms = it.scheduleAlarms.toIntArray()
                        updateAlarmText(currentSelectedAlarms!!)
                    }
                    // 캘린더 초기화
                    if (currentSelectedCalendarId == null) {
                        currentSelectedCalendarId = it.calendarId
                        val calendarName = viewModel.getCalendarNameById(it.calendarId)
                        binding.tvCalendarStatus.text = calendarName
                        binding.tvCalendarStatus.setTextColor(Color.BLACK)
                        // 필요 시 색상 점 초기화 로직 추가
                    }
                }
            }
        }

        val today = LocalDate.now()
        startDate = today
        endDate = today
        binding.tvStartDate.text = today.format(dateFormatter)
        binding.tvEndDate.text = today.format(dateFormatter)

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

            val imm =
                requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(
                binding.etScheduleName,
                android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT
            )
        }

        binding.btnConfirm.setOnClickListener {
            val scheduleName = binding.etScheduleName.text.toString().trim()

            // 1. 필수 유효성 체크 (일정명)
            if (scheduleName.isEmpty()) {
                Toast.makeText(context, "일정명을 입력해 주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 2. 날짜 유효성 체크
            if (startDate == null) {
                Toast.makeText(context, "시작 날짜를 선택해 주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }


            // 만약 장소 검색 결과로 받은 데이터가 있다면 여기에 PlaceRequest 객체를 생성해 넣어주세요.
            val placeRequest = selectedPlaceName?.let { name ->
                PlaceRequest(
                    targetName = name,
                    targetLat = selectedLat,
                    targetLng = selectedLng
                )
            }

            val selectedColorInt = try {
                android.graphics.Color.parseColor(selectedColorHex) // String -> Int 변환
            } catch (e: Exception) {
                android.graphics.Color.parseColor("#DC354B") // 실패 시 기본값
            }

            viewModel.createScheduleWithDefaultSettings(
                title = scheduleName,
                memo = binding.etMemo.text?.toString(),
                isAllDay = isAllDay,
                startDate = startDate.toString(),
                startTime = if (isAllDay) null else binding.tvStartTime.text.toString(),
                endDate = (endDate ?: startDate).toString(),
                endTime = if (isAllDay) null else binding.tvEndTime.text.toString(),
                place = placeRequest,      // 서버로 보낼 위도/경도 객체
                placeId = selectedPlaceId,  // 룸 DB에 저장할 ID (추가)
                customAlarms = currentSelectedAlarms?.toList(),
                calendarId = currentSelectedCalendarId,
                selectedColor = selectedColorInt, // Int 타입으로 전달
                repeatInfo = currentRepeatInfo    // 이 변수가 상단에 선언되어 있어야 함
            )
        }
        observeCreateEvent()
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

        // 반복 버튼 눌렀을 때 선택된 시작 날짜 보내주기
        binding.btnRepeat.setOnClickListener {
            val dateToSend = startDate?.toString() ?: LocalDate.now().toString()

            val repeatFragment = ScheduleRepeatFragment().apply {
                arguments = Bundle().apply {
                    putString("startDate", dateToSend)
                    // 기존에 설정된 리핏 인포가 있다면 함께 전달
                    putSerializable("existingRepeatInfo", currentRepeatInfo)
                }
            }

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
                animateLayoutChange()
                binding.layoutColorSelector.visibility = View.VISIBLE

                // 캘린더를 아예 닫고 싶지 않다면 아래 줄을 주석 처리하세요.
                // 만약 닫아야 한다면, 나중에 다시 열 때 확실히 VISIBLE로 만들어야 합니다.
                binding.calendarContainer.visibility = View.GONE
                binding.timePickerContainer.visibility = View.GONE
            } else {
                binding.layoutColorSelector.visibility = View.GONE
            }
        }

        val colorList = listOf(
            ColorItem(R.color.schedule_5, "#DC354B"),
            ColorItem(R.color.route_line_3, "#D8643F"),
            ColorItem(R.color.route_suin_bundang, "#FFBB00"),
            ColorItem(R.color.route_branch_bus, "#53B332"),
            ColorItem(R.color.schedule_14, "#51AEED"),
            ColorItem(R.color.schedule_12, "#2A4ABF"),
            ColorItem(R.color.schedule_8, "#5F46DD"),
            ColorItem(R.color.route_line_8, "#F14C82"),
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
            val intent = android.content.Intent(
                requireContext(),
                com.example.pace.ui.main.MainActivity::class.java
            ).apply {
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
            val bundle = Bundle().apply {
                // 온보딩 값이 아닌, 현재 화면에서 들고 있는 변수를 넘김
                putIntArray("selectedAlarmMinutes", currentSelectedAlarms)
                putString("requestKey", "GENERAL_ALARM_KEY") // 💡 전용 키 전달
            }
            fragment.arguments = bundle

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
                } else {
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
                        startDate != null && endDate != null && date.isAfter(startDate) && date.isBefore(
                            endDate
                        ) -> {
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
        binding.etScheduleName.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                // 이름을 입력할 때는 복잡한 피커들을 잠시 접어두는 것이 좋습니다.
                binding.calendarContainer.visibility = View.GONE
                binding.timePickerContainer.visibility = View.GONE
                binding.layoutColorSelector.visibility = View.GONE
            }
        }
        binding.btnCalendar.setOnClickListener {
            // SelectCalendarFragment로 통일
            val fragment = SelectCalendarFragment()
            val bundle = Bundle().apply {
                putLong("currentCalendarId", currentSelectedCalendarId ?: -1L)
                putString("requestKey", "GENERAL_CALENDAR_KEY") // 일반용 키
            }
            fragment.arguments = bundle

            parentFragmentManager.beginTransaction()
                .setCustomAnimations(android.R.anim.slide_in_left, android.R.anim.slide_out_right, android.R.anim.slide_in_left, android.R.anim.slide_out_right)
                .replace(android.R.id.content, fragment)
                .addToBackStack(null)
                .commit()
        }

        parentFragmentManager.setFragmentResultListener("GENERAL_ALARM_KEY", viewLifecycleOwner) { _, bundle ->
            // 일반 일정 로직 수행
            val resultText = bundle.getString("selectedAlarm")
            val resultMinutes = bundle.getIntArray("selectedAlarmMinutes")

            if (resultMinutes != null) {
                // 1) 프래그먼트 내부 임시 변수만 업데이트 (DB는 건드리지 않음)
                currentSelectedAlarms = resultMinutes

                // 2) UI 텍스트만 즉시 업데이트
                binding.tvRemindStatus.text = resultText
                binding.tvRemindStatus.setTextColor(Color.BLACK)

            }
        }
        parentFragmentManager.setFragmentResultListener("GENERAL_CALENDAR_KEY", viewLifecycleOwner) { _, bundle ->
            val selectedId = bundle.getLong("calendarId", -1L)
            val selectedName = bundle.getString("calendarName") ?: "내 일정"
            val calendarColor = bundle.getInt("selectedCalendarColor", -1)

            android.util.Log.d("CALENDAR_RECEIVE", "일반 일정 수신: $selectedName")

            if (selectedId != -1L) {
                currentSelectedCalendarId = selectedId
                currentSelectedCalendarName = selectedName

                binding.tvCalendarStatus.text = selectedName
                binding.tvCalendarStatus.setTextColor(Color.BLACK)

                if (calendarColor != -1) {
                    binding.viewColorDot.backgroundTintList = ColorStateList.valueOf(calendarColor)
                }
            }
        }

        setFragmentResultListener("repeatKey") { _, bundle ->
            val resultText = bundle.getString("selectedRepeat")
            binding.tvRepeatStatus.text = resultText
            binding.tvRepeatStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.black))

            // RepeatInfo 객체가 넘어올 경우 저장
            currentRepeatInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                bundle.getSerializable("repeatInfo", RepeatInfo::class.java)
            } else {
                @Suppress("DEPRECATION")
                bundle.getSerializable("repeatInfo") as? RepeatInfo
            }
        }

    }


    private fun changeSelectedColor(colorStr: String) {
        selectedColorHex = colorStr

        val color = Color.parseColor(colorStr)
        binding.viewColorDot.backgroundTintList = ColorStateList.valueOf(color)

        // 2. UI 처리
        binding.layoutColorSelector.visibility = View.GONE

        // 로그로 값이 바뀌는지 확인해보세요
        Log.d("COLOR_CHECK", "선택된 색상: $selectedColorHex")
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

        // 1. 방해 요소 제거
        binding.layoutColorSelector.visibility = View.GONE
        binding.timePickerContainer.visibility = View.GONE

        // 2. 컨테이너와 캘린더 본체를 모두 VISIBLE로
        binding.calendarContainer.visibility = View.VISIBLE
        binding.calendarPicker.visibility = View.VISIBLE // 💡 명시적으로 추가

        // 3. 키보드 숨기기 및 포커스 제거
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        view?.let {
            imm.hideSoftInputFromWindow(it.windowToken, 0)
            it.clearFocus()
        }

        // 4. 재계산 요청
        binding.calendarPicker.post {
            binding.calendarPicker.requestLayout()
            binding.nestedScrollView.smoothScrollTo(0, binding.calendarContainer.top)
        }
    }

    private fun showTimePicker() {
        if (isAllDay) return

        // 1. 레이아웃 가시성 조절
        animateLayoutChange()
        binding.timePickerContainer.visibility = View.VISIBLE
        binding.calendarContainer.visibility = View.GONE
        binding.layoutColorSelector.visibility = View.GONE

        // 2. 현재 선택된 시간 텍스트를 파싱하여 피커 초기값 설정
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
                // 5분 단위 인덱스 계산 (예: 15분 -> index 3)
                binding.pickerMinute.value = (m / 5).coerceIn(0, 11)
            }
        } catch (e: Exception) {
            binding.pickerHour.value = 10
            binding.pickerMinute.value = 0
        }

        // 3. 스크롤을 시간 피커 위치로 이동
        binding.timePickerContainer.post {
            binding.nestedScrollView.smoothScrollTo(0, binding.timePickerContainer.top)
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
        // 1. 이미 범위 선택이 완료되었거나(start/end 둘 다 있음), 아예 없는 경우 -> 새로 시작
        if (startDate != null && endDate != null) {
            startDate = date
            endDate = null // 종료일만 null로 비워서 다음 클릭을 기다림
        }
        // 2. 시작일만 있고 종료일은 없는 상태 -> 종료일 확정
        else if (startDate != null && endDate == null) {
            if (date.isBefore(startDate)) {
                startDate = date // 시작일보다 이전이면 시작일을 변경
            } else {
                endDate = date
                onDateSelectionComplete()
            }
        }
        // 3. 혹시나 둘 다 null인 경우 (방어 코드)
        else {
            startDate = date
            endDate = null
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

    private fun observeCreateEvent() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.createScheduleEvent.collect { isSuccess ->
                when (isSuccess) {
                    true -> {
                        Toast.makeText(context, "일정이 성공적으로 저장되었습니다.", Toast.LENGTH_SHORT).show()
                        viewModel.resetCreateEvent()
                        requireActivity().finish()
                    }
                    false -> {
                        Toast.makeText(context, "일정 저장에 실패했습니다.", Toast.LENGTH_SHORT).show()
                        viewModel.resetCreateEvent()
                    }
                    null -> {}
                }
            }
        }
    }

    private fun observeUserSettings() {
        viewLifecycleOwner.lifecycleScope.launch {
            // collect가 아닌 first()를 사용하여 화면 진입 시점에 딱 한 번만 데이터를 가져옵니다.
            val settings = viewModel.userSettings.filterNotNull().first()

            // 알람: 사용자가 아직 수정 안 했다면 기본값 표시
            if (currentSelectedAlarms == null) {
                currentSelectedAlarms = settings.scheduleAlarms.toIntArray()
                updateAlarmText(currentSelectedAlarms!!)
            }

            // 캘린더: 사용자가 아직 수정 안 했다면 기본값 표시
            if (currentSelectedCalendarId == null) {
                currentSelectedCalendarId = settings.calendarId
                val calendarName = viewModel.getCalendarNameById(settings.calendarId)
                binding.tvCalendarStatus.text = calendarName
                binding.tvCalendarStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
            }
        }
    }
    private fun updateAlarmText(alarms: IntArray) {
        if (alarms.isEmpty()) {
            binding.tvRemindStatus.text = "일정 알림 안함"
        } else {
            val texts = alarms.map { minutesToText(it) } // minutesToText 함수를 여기도 복사하거나 유틸로 분리
            binding.tvRemindStatus.text = texts.joinToString(", ")
        }
    }
    private fun minutesToText(minutes: Int): String {
        return when (minutes) {
            0 -> "정시"
            5 -> "5분 전"
            10 -> "10분 전"
            15 -> "15분 전"
            30 -> "30분 전"
            60 -> "1시간 전"
            120 -> "2시간 전"
            1440 -> "1일 전"
            2880 -> "2일 전"
            10080 -> "1주일 전"
            else -> "${minutes}분 전"
        }
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
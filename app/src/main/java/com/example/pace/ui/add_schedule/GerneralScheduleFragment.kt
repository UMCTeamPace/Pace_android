package com.example.pace.ui.add_schedule

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
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
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import biweekly.util.Recurrence
import com.example.pace.R
import com.example.pace.databinding.FragmentGeneralScheduleBinding
import com.example.pace.data.viewmodel.ScheduleViewModel
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.view.MonthDayBinder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.example.pace.data.model.request.PlaceRequest
import com.example.pace.data.model.request.RepeatInfo
import com.example.pace.data.viewmodel.SettingsViewModel
import com.example.pace.ui.onboarding.CalendarSelectFragment
import com.example.pace.ui.main.home.EditRepeatScheduleDialog
import com.google.gson.Gson
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import kotlin.compareTo

@AndroidEntryPoint
class GeneralScheduleFragment : Fragment() {
    private data class FormSnapshot(
        val title: String,
        val memo: String,
        val isAllDay: Boolean,
        val startDate: LocalDate?,
        val endDate: LocalDate?,
        val startTime: String,
        val endTime: String,
        val selectedColorHex: String,
        val selectedPlaceId: String?,
        val selectedPlaceName: String?,
        val selectedLat: Double,
        val selectedLng: Double,
        val selectedAlarms: List<Int>,
        val selectedCalendarId: Long?,
        val repeatInfoJson: String?
    )

    private enum class ActiveInput {
        START_DATE,
        END_DATE,
        START_TIME,
        END_TIME
    }

    private enum class ExpandedPicker {
        NONE,
        CALENDAR,
        TIME
    }

    private var _binding: FragmentGeneralScheduleBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ScheduleViewModel by activityViewModels()


    private var isEditingStartTime: Boolean = true
    private var activeInput: ActiveInput? = null
    private var expandedPicker: ExpandedPicker = ExpandedPicker.NONE
    private var isSyncingTimePicker = false

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
    private var currentSelectedCalendarColor: Int? = null
    private var colorAdapter: ColorAdapter? = null
    private var hasUserSelectedEventColor = false

    private val dateFormatter = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)
    val colorInt = android.graphics.Color.parseColor(selectedColorHex)
    private var currentRepeatInfo: RepeatInfo? = null
    private var isRepeatChanged: Boolean = false

    private val settingsViewModel: SettingsViewModel by viewModels()

    private var isEditMode: Boolean = false
    private var scheduleIdForEdit: Long = -1L
    private var occurrenceDateForEdit: LocalDate? = null
    private var initialFormSnapshot: FormSnapshot? = null
    private var isSubmitInProgress = false
    private var isKeyboardVisible = false
    private var isTouchingInputArea = false
    private var suppressKeyboardDismissUntil = 0L
    private val showBottomButtonsRunnable = Runnable {
        if (_binding != null && !isKeyboardVisible) {
            binding.layoutBottomButtons.visibility = View.VISIBLE
        }
    }

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
            }
            updateLocationFieldColor(!selectedPlaceName.isNullOrBlank())

            Toast.makeText(context, "장소 선택: $selectedPlaceName", Toast.LENGTH_SHORT).show()
        }
    }

    val backPressedCallback = object: OnBackPressedCallback(true){
        override fun handleOnBackPressed() {
            handleExitAttempt()
        }
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentGeneralScheduleBinding.inflate(inflater, container, false)
        requireActivity().onBackPressedDispatcher.addCallback(backPressedCallback)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. 초기화 필수 함수들 (UI 구성)
        setupCalendar()
        setupLegend()
        setupMonthNavigation()
        initTimePickers()
        updateTimeVisibility()
        binding.calendarContainer.visibility = View.GONE

        // 2. 데이터 모드 설정
        isEditMode = arguments?.getBoolean("isEdit") ?: false
        scheduleIdForEdit = arguments?.getLong("SCHEDULE_ID") ?: -1L
        occurrenceDateForEdit = arguments?.getString("OCCURRENCE_DATE")?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        }

        setupEditMode() // 여기서 비동기로 데이터를 채움

        // 3. 관찰자들 (수정 모드일 땐 기본 설정값이 덮어쓰지 않게 주의)
        observeUserSettings()
        observeCreateEvent() // 필요 시 observeUpdateEvent() 추가


        if (currentSelectedAlarms == null || currentSelectedCalendarId == null) {
            viewLifecycleOwner.lifecycleScope.launch {
                // first()를 사용하여 최초 1회만 가져오고 연결을 끊습니다 (덮어쓰기 방지)
                val settings = settingsViewModel.userSettings.filterNotNull().first()

                settings.let {
                    // 알람 초기화
                    if (currentSelectedAlarms == null) {
                        currentSelectedAlarms = it.scheduleAlarms.toIntArray()
                        updateAlarmText(currentSelectedAlarms!!)
                        updateReminderFieldColor(currentSelectedAlarms!!)
                    }
                    // 캘린더 초기화
                    if (currentSelectedCalendarId == null) {
                        val resolvedCalendarId = resolveDefaultCalendarId(it.calendarId)
                        currentSelectedCalendarId = resolvedCalendarId
                        val calendarName = viewModel.getCalendarNameById(resolvedCalendarId)
                        binding.tvCalendarStatus.text = calendarName
                        binding.tvCalendarStatus.setTextColor(Color.BLACK)
                        applyCalendarColor(resolvedCalendarId)
                    }
                }
            }
        }

        if (!isEditMode) {
            val today = LocalDate.now()
            startDate = today
            endDate = today
            updateDateDisplay() // 위에서 만든 함수를 쓰면 텍스트뷰까지 한 번에 업데이트됩니다.
        }


        if (!isEditMode) {
            resetInitialFormSnapshot()
        }

        binding.root.post {
            if (initialFormSnapshot == null) {
                resetInitialFormSnapshot()
            }
        }

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
            moveTitleCursorToEnd()

            val imm =
                requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(
                binding.etScheduleName,
                android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT
            )
        }

        // 1. 저장 버튼 클릭 리스너 부분
        binding.btnConfirm.setOnClickListener {
            if (isSubmitInProgress) return@setOnClickListener

            val scheduleName = binding.etScheduleName.text.toString().trim()

            // 1. 필수 유효성 체크 (시작일)
            if (startDate == null) {
                Toast.makeText(context, "시작 날짜를 선택해 주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 날짜 문자열 확정 (null 방지)
            enforceValidTimeRange()

            val finalStartDateStr = startDate.toString()
            val finalEndDateStr = (endDate ?: startDate).toString()

            // 💡 [핵심 보정] 반복 설정이 있는데 종료일이 null이면 1970년 에러를 막기 위해 보정
            currentRepeatInfo?.let { info ->
                if (info.repeatEndDate.isNullOrBlank()) {
                    // 반복 종료일이 명시되지 않았다면 일정의 종료일(endDate)을 반복의 끝으로 설정
                    info.repeatEndDate = finalEndDateStr
                }
            }

            Log.d("SaveCheck", "보정 완료 - 반복여부: ${currentRepeatInfo != null}, " +
                    "종료일: $finalEndDateStr, 반복종료일: ${currentRepeatInfo?.repeatEndDate}")

            // 장소 정보 객체화
            val placeRequest = selectedPlaceName?.let { name ->
                PlaceRequest(
                    targetName = name,
                    targetLat = selectedLat,
                    targetLng = selectedLng
                )
            }

            // 색상 String -> Int 변환
            val saveEventColorInt = getSelectedEventColorInt()

            if (isEditMode && scheduleIdForEdit != -1L) {
                // A. 수정 모드
                setSubmitInProgress(true)
                viewLifecycleOwner.lifecycleScope.launch {
                    val originalSchedule = viewModel.getScheduleById(scheduleIdForEdit)

                    if (originalSchedule == null) {
                        setSubmitInProgress(false)
                        return@launch
                    }

                    originalSchedule.let { existing ->
                        // 수정된 정보로 객체 생성
                        val updatedSchedule = existing.copy(
                            title = scheduleName,
                            memo = binding.etMemo.text?.toString(),
                            startDate = finalStartDateStr,
                            endDate = finalEndDateStr,
                            startTime = if (isAllDay) "00:00" else binding.tvStartTime.text.toString(),
                            endTime = if (isAllDay) "23:59" else binding.tvEndTime.text.toString(),
                            isAllDay = isAllDay,
                            calendarId = currentSelectedCalendarId ?: existing.calendarId,
                            eventColor = saveEventColorInt,
                            calendarColor = currentSelectedCalendarColor ?: existing.calendarColor,
                            placeJson = placeRequest?.let { com.google.gson.Gson().toJson(it) },
                            reminders = currentSelectedAlarms?.toList() ?: existing.reminders,

                            // 💡 [핵심] 반복 정보 업데이트 (수정 시 repeatInfo를 RRULE로 변환하여 넣어줘야 함)
                            repeatRule = when {
                                currentRepeatInfo != null -> viewModel.buildRRuleString(currentRepeatInfo)
                                isRepeatChanged -> null
                                else -> existing.repeatRule
                            }
                        )

                        // 💡 뷰모델에 수정 명령 (이 함수가 RemoteDataSource.updateCalendarEvent를 호출해야 함)
                        if (!existing.repeatRule.isNullOrEmpty()) {
                            val occurrenceDate = occurrenceDateForEdit ?: runCatching {
                                LocalDate.parse(updatedSchedule.startDate)
                            }.getOrNull() ?: LocalDate.now()

                            EditRepeatScheduleDialog(requireContext()).apply {
                                var isOptionSelected = false
                                setOnOptionSelectedListener { option ->
                                    isOptionSelected = true
                                    viewModel.updateRecurringSchedule(
                                        originalSchedule = existing,
                                        updatedSchedule = updatedSchedule,
                                        occurrenceDate = occurrenceDate,
                                        scope = option
                                    )
                                }
                                setOnDismissListener {
                                    if (!isOptionSelected) setSubmitInProgress(false)
                                }
                            }.show()
                        } else {
                            viewModel.updateSchedule(updatedSchedule)
                        }

                    }
                }
            } else {
                // B. 생성 모드
                setSubmitInProgress(true)
                viewModel.createScheduleWithDefaultSettings(
                    title = scheduleName,
                    memo = binding.etMemo.text?.toString(),
                    isAllDay = isAllDay,
                    startDate = finalStartDateStr,
                    startTime = if (isAllDay) null else binding.tvStartTime.text.toString(),
                    endDate = finalEndDateStr,
                    endTime = if (isAllDay) null else binding.tvEndTime.text.toString(),
                    place = placeRequest,
                    placeId = selectedPlaceId,
                    customAlarms = currentSelectedAlarms?.toList(),
                    calendarId = currentSelectedCalendarId,
                    selectedColor = saveEventColorInt,
                    repeatInfo = currentRepeatInfo // 보정된 RepeatInfo 전달
                )
            }
        }

// 2. 이벤트 관찰 부분 (onCreateView나 onViewCreated에서 호출)
        observeCreateEvent()
        observeUpdateEvent() // 수정 완료 관찰 추가

// 3. 취소 버튼 (기존과 동일)
        binding.btnCancel.setOnClickListener {
            handleExitAttempt()
        }

        setupKeyboardVisibilityListener()

        // 반복 버튼 눌렀을 때 선택된 시작 날짜 보내주기
        binding.btnRepeat.setOnClickListener {
            dismissKeyboard()
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
        binding.btnStartDate.setOnClickListener {
            dismissKeyboard()
            if (expandedPicker == ExpandedPicker.CALENDAR && activeInput == ActiveInput.START_DATE) {
                closeExpandedPicker()
                return@setOnClickListener
            }
            activeInput = ActiveInput.START_DATE
            updateDateDisplay()
            updateTimeVisibility()
            showCalendar()
        }
        binding.btnEndDate.setOnClickListener {
            dismissKeyboard()
            if (expandedPicker == ExpandedPicker.CALENDAR && activeInput == ActiveInput.END_DATE) {
                closeExpandedPicker()
                return@setOnClickListener
            }
            activeInput = ActiveInput.END_DATE
            updateDateDisplay()
            updateTimeVisibility()
            showCalendar()
        }

        // 시간 텍스트 클릭 시 (하루종일이 아닐 때만 반응)
        // 시간 텍스트 클릭 시
        binding.tvStartTime.setOnClickListener {
            dismissKeyboard()
            if (isAllDay) return@setOnClickListener
            if (expandedPicker == ExpandedPicker.TIME && activeInput == ActiveInput.START_TIME) {
                closeExpandedPicker()
                return@setOnClickListener
            }
            isEditingStartTime = true
            activeInput = ActiveInput.START_TIME
            updateDateDisplay()
            updateTimeVisibility() // [수정] 피커를 보여주기 전에 색상부터 즉시 변경
            showTimePicker()
        }

        binding.tvEndTime.setOnClickListener {
            dismissKeyboard()
            if (isAllDay) return@setOnClickListener
            if (expandedPicker == ExpandedPicker.TIME && activeInput == ActiveInput.END_TIME) {
                closeExpandedPicker()
                return@setOnClickListener
            }
            isEditingStartTime = false
            activeInput = ActiveInput.END_TIME
            updateDateDisplay()
            updateTimeVisibility() // [수정] 즉시 초록색 불 켜기
            showTimePicker()
        }


        binding.viewColorDot.setOnClickListener {
            dismissKeyboard()
            animateColorSelectorChange()
            if (binding.layoutColorSelector.visibility == View.GONE) {
                binding.layoutColorSelector.visibility = View.VISIBLE
            } else {
                binding.layoutColorSelector.visibility = View.GONE
            }
        }
        binding.etScheduleName.onFocusChangeListener = null
        installInputProtection()
        setupStatusIconColors()

        binding.viewColorDot.backgroundTintList = ColorStateList.valueOf(getSaveColorInt())
        val selectedColorForPalette = colorIntToHex(getSaveColorInt())
        val colorList = buildColorItems(selectedColorForPalette)


        colorAdapter = ColorAdapter(requireContext(), colorList) { selectedColor ->
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
            dismissKeyboard()
            animateLayoutChange()
            isAllDay = !isAllDay

            if (isAllDay) {
                binding.addscheMyPhoneIv.setImageResource(R.drawable.ic_toggle_selected)
                // 하루종일 켜지면 피커들을 다 닫음
                binding.calendarContainer.visibility = View.GONE
                binding.timePickerContainer.visibility = View.GONE
                expandedPicker = ExpandedPicker.NONE
                if (activeInput == ActiveInput.START_TIME || activeInput == ActiveInput.END_TIME) {
                    activeInput = null
                }
            } else {
                binding.addscheMyPhoneIv.setImageResource(R.drawable.ic_toggle_unselected)

                // [핵심 수정] 캘린더가 닫혀있을 때만 시간 피커를 보여줌 (공존 방지)
                if (binding.calendarContainer.visibility == View.GONE) {
                    activeInput = ActiveInput.START_TIME
                    showTimePicker()
                }
            }
            updateTimeVisibility()
            updateDateDisplay()
        }

        binding.btnRoute.setOnClickListener {
            dismissKeyboard()
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
            dismissKeyboard()
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

                textView.text = date.dayOfMonth.toString()
                if (day.position != com.kizitonwose.calendar.core.DayPosition.MonthDate) {
                    textView.setTextColor(Color.LTGRAY)
                } else {
                    when {
                        // 시작일/종료일 동일 (원형)
                        date == startDate && (endDate == null || endDate == startDate) -> {
                            textView.setTextColor(resources.getColor(R.color.white))
                            textView.setBackgroundResource(R.drawable.drawable_circle_green)
                            root.background = null
                        }
                        // 기간 시작점
                        date == startDate -> {
                            textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                            textView.setBackgroundResource(R.drawable.drawable_circle_green)
                            root.setBackgroundResource(R.drawable.bg_calendar_range_start)
                        }
                        // 기간 종료점
                        date == endDate -> {
                            textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                            textView.setBackgroundResource(R.drawable.drawable_circle_green)
                            root.setBackgroundResource(R.drawable.bg_calendar_range_end)
                        }
                        // 기간 사이 (연두색 배경 적용)
                        startDate != null && endDate != null && date.isAfter(startDate) && date.isBefore(
                            endDate
                        ) -> {
                            textView.setTextColor(resources.getColor(R.color.semantic_info))
                            textView.background = null
                            // 여기에 @color/semantic_info가 적용된 drawable 연결
                            root.setBackgroundResource(R.drawable.bg_calendar_range_middle)
                        }

                        else -> {
                            textView.setTextColor(getCalendarDateTextColor(date))
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
        binding.etScheduleName.onFocusChangeListener = null
        binding.btnCalendar.setOnClickListener {
            dismissKeyboard()
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
            val resultMinutes = bundle.getIntArray("selectedAlarmMinutes")

            if (resultMinutes != null) {
                // 1) 프래그먼트 내부 임시 변수만 업데이트 (DB는 건드리지 않음)
                currentSelectedAlarms = resultMinutes

                // 2) 정렬 규칙을 포함한 공통 UI 갱신 경로 사용
                updateAlarmText(resultMinutes)
                updateReminderFieldColor(resultMinutes)

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
                    currentSelectedCalendarColor = calendarColor
                    hasUserSelectedEventColor = false
                    binding.viewColorDot.backgroundTintList = ColorStateList.valueOf(calendarColor)
                    colorAdapter?.selectColor(colorIntToHex(calendarColor))
                }
            }
        }

        setFragmentResultListener("repeatKey") { _, bundle ->
            val resultText = bundle.getString("selectedRepeat")
            binding.tvRepeatStatus.text = resultText

            // RepeatInfo 객체가 넘어올 경우 저장
            currentRepeatInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                bundle.getSerializable("repeatInfo", RepeatInfo::class.java)
            } else {
                @Suppress("DEPRECATION")
                bundle.getSerializable("repeatInfo") as? RepeatInfo
            }
            isRepeatChanged = true
            updateRepeatFieldColor(currentRepeatInfo != null)
        }

    }


    private fun changeSelectedColor(colorStr: String) {
        selectedColorHex = colorStr

        val color = Color.parseColor(colorStr)
        binding.viewColorDot.backgroundTintList = ColorStateList.valueOf(color)
        colorAdapter?.selectColor(colorStr)
        hasUserSelectedEventColor = true
        // 수동 색상 선택 시 캘린더 색상 우선순위 해제
        currentSelectedCalendarColor = null

        // 2. UI 처리
        binding.layoutColorSelector.visibility = View.GONE

        // 로그로 값이 바뀌는지 확인해보세요
        Log.d("COLOR_CHECK", "선택된 색상: $selectedColorHex")
    }
    private fun handleExitAttempt() {
        if (!hasUnsavedChanges()) {
            exitScreen()
            return
        }

        AddCancelDialog(requireContext()) {
            exitScreen()
        }.show()
    }

    private fun exitScreen() {
        if (parentFragmentManager.backStackEntryCount > 0) {
            parentFragmentManager.popBackStack()
        } else {
            requireActivity().finish()
        }
    }

    private fun hasUnsavedChanges(): Boolean {
        return currentFormSnapshot() != initialFormSnapshot
    }

    private fun resetInitialFormSnapshot() {
        initialFormSnapshot = currentFormSnapshot()
    }

    private fun currentFormSnapshot(): FormSnapshot {
        return FormSnapshot(
            title = binding.etScheduleName.text?.toString().orEmpty().trim(),
            memo = binding.etMemo.text?.toString().orEmpty().trim(),
            isAllDay = isAllDay,
            startDate = startDate,
            endDate = endDate,
            startTime = binding.tvStartTime.text?.toString().orEmpty(),
            endTime = binding.tvEndTime.text?.toString().orEmpty(),
            selectedColorHex = selectedColorHex,
            selectedPlaceId = selectedPlaceId,
            selectedPlaceName = selectedPlaceName,
            selectedLat = selectedLat,
            selectedLng = selectedLng,
            selectedAlarms = currentSelectedAlarms?.sorted()?.toList() ?: emptyList(),
            selectedCalendarId = currentSelectedCalendarId,
            repeatInfoJson = currentRepeatInfo?.let { Gson().toJson(it) }
        )
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

        binding.pickerHour.setOnValueChangedListener { _, oldValue, newValue ->
            if (isSyncingTimePicker) return@setOnValueChangedListener
            applyTimePickerDelta(hourDelta(oldValue, newValue) * 60L)
        }
        binding.pickerMinute.setOnValueChangedListener { _, oldValue, newValue ->
            if (isSyncingTimePicker) return@setOnValueChangedListener
            applyTimePickerDelta(minuteDelta(oldValue, newValue).toLong())
        }
    }

    private fun showCalendar() {
        animateLayoutChange()
        expandedPicker = ExpandedPicker.CALENDAR

        // 1. 방해 요소 제거
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
        expandedPicker = ExpandedPicker.TIME
        binding.timePickerContainer.visibility = View.VISIBLE
        binding.calendarContainer.visibility = View.GONE

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
                syncTimePickerValues(h, (m / 5).coerceIn(0, 11))
            }
        } catch (e: Exception) {
            syncTimePickerValues(10, 0)
        }

        // 3. 스크롤을 시간 피커 위치로 이동
        binding.timePickerContainer.post {
            binding.nestedScrollView.smoothScrollTo(0, binding.timePickerContainer.top)
        }
    }

    private fun closeExpandedPicker() {
        animateLayoutChange()
        binding.calendarContainer.visibility = View.GONE
        binding.timePickerContainer.visibility = View.GONE
        expandedPicker = ExpandedPicker.NONE
        activeInput = null
        updateDateDisplay()
        updateTimeVisibility()
    }


    private fun updateTimeVisibility() {
        if (isAllDay) {
            binding.tvStartTime.visibility = View.GONE
            binding.tvEndTime.visibility = View.GONE
            binding.timePickerContainer.visibility = View.GONE
            if (expandedPicker == ExpandedPicker.TIME) {
                expandedPicker = ExpandedPicker.NONE
            }
        } else {
            binding.tvStartTime.visibility = View.VISIBLE
            binding.tvEndTime.visibility = View.VISIBLE

            val highlightColor = Color.parseColor("#8BC34A")
            val defaultColor = Color.BLACK

            // 현재 편집 중인 시간에만 '불'이 들어오게 설정
            if (activeInput == ActiveInput.START_TIME) {
                binding.tvStartTime.setTextColor(highlightColor)
                binding.tvEndTime.setTextColor(defaultColor)
                binding.tvEndTime.setTypeface(null, Typeface.NORMAL)
            } else if (activeInput == ActiveInput.END_TIME) {
                binding.tvStartTime.setTextColor(defaultColor)
                binding.tvStartTime.setTypeface(null, Typeface.NORMAL)
                binding.tvEndTime.setTextColor(highlightColor)
            } else {
                binding.tvStartTime.setTextColor(defaultColor)
                binding.tvEndTime.setTextColor(defaultColor)
                binding.tvStartTime.setTypeface(null, Typeface.NORMAL)
                binding.tvEndTime.setTypeface(null, Typeface.NORMAL)
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
            val keyboardVisibleNow = keypadHeight > screenHeight * 0.15

            if (keyboardVisibleNow != isKeyboardVisible) {
                isKeyboardVisible = keyboardVisibleNow
                rootView.removeCallbacks(showBottomButtonsRunnable)
                if (keyboardVisibleNow) {
                    binding.layoutBottomButtons.visibility = View.GONE
                } else {
                    rootView.postDelayed(showBottomButtonsRunnable, 120)
                }
            }
        }

        binding.nestedScrollView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isTouchingInputArea = isTouchInsideInput(event)
                    if (isTouchingInputArea) {
                        suppressKeyboardDismissUntil = android.os.SystemClock.uptimeMillis() + 500L
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!isTouchingInputArea && !shouldSuppressKeyboardDismiss()) {
                        dismissKeyboard()
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    binding.nestedScrollView.post {
                        isTouchingInputArea = false
                    }
                }
            }
            false
        }

        binding.nestedScrollView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            if (scrollY != oldScrollY && !isTouchingInputArea && !shouldSuppressKeyboardDismiss()) {
                dismissKeyboard()
            }
        }
    }

    private fun installInputProtection() {
        val markInputInteraction = View.OnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                isTouchingInputArea = true
                suppressKeyboardDismissUntil = android.os.SystemClock.uptimeMillis() + 500L
                moveTitleCursorToEnd()
                moveMemoCursorToEnd()
            }
            false
        }

        binding.etScheduleName.setOnTouchListener(markInputInteraction)
        binding.etMemo.setOnTouchListener(markInputInteraction)
    }

    private fun moveTitleCursorToEnd() {
        binding.etScheduleName.post {
            binding.etScheduleName.text?.length?.let(binding.etScheduleName::setSelection)
        }
    }

    private fun moveMemoCursorToEnd() {
        binding.etMemo.post {
            binding.etMemo.text?.length?.let(binding.etMemo::setSelection)
        }
    }

    private fun updateReminderFieldColor(alarms: IntArray) {
        val colorRes = if (alarms.isEmpty()) R.color.text_tertiary else R.color.text_primary
        val color = ContextCompat.getColor(requireContext(), colorRes)
        binding.tvRemindStatus.setTextColor(color)
        binding.ivRemind.setColorFilter(color)
    }

    private fun setupStatusIconColors() {
        binding.ivCalendar.setColorFilter(ContextCompat.getColor(requireContext(), R.color.text_primary))
        updateRepeatFieldColor(currentRepeatInfo != null)
        updateLocationFieldColor(!selectedPlaceName.isNullOrBlank())
        updateReminderFieldColor(currentSelectedAlarms ?: intArrayOf())
        updateMemoFieldColor(binding.etMemo.text?.toString().orEmpty())

        binding.etMemo.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateMemoFieldColor(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }

    private fun updateRepeatFieldColor(hasRepeat: Boolean) {
        val colorRes = if (hasRepeat) R.color.text_primary else R.color.text_tertiary
        val color = ContextCompat.getColor(requireContext(), colorRes)
        binding.tvRepeatStatus.setTextColor(color)
        binding.ivRepeat.setColorFilter(color)
    }

    private fun updateLocationFieldColor(hasLocation: Boolean) {
        val colorRes = if (hasLocation) R.color.text_primary else R.color.text_tertiary
        val color = ContextCompat.getColor(requireContext(), colorRes)
        binding.tvLocationStatus.setTextColor(color)
        binding.ivLocation.setColorFilter(color)
    }

    private fun updateMemoFieldColor(memo: String) {
        val colorRes = if (memo.isBlank()) R.color.text_tertiary else R.color.text_primary
        binding.ivMemo.setColorFilter(ContextCompat.getColor(requireContext(), colorRes))
    }

    private fun shouldSuppressKeyboardDismiss(): Boolean {
        return android.os.SystemClock.uptimeMillis() < suppressKeyboardDismissUntil
    }

    private fun applyTimePickerDelta(deltaMinutes: Long) {
        when (activeInput) {
            ActiveInput.START_TIME -> {
                val start = parseDateTime(startDate, binding.tvStartTime.text) ?: return
                applyStartDateTime(start.plusMinutes(deltaMinutes))
                binding.calendarPicker.notifyCalendarChanged()
                updateDateDisplay()
                enforceValidTimeRange(ActiveInput.START_TIME)
            }
            ActiveInput.END_TIME -> {
                val end = parseDateTime(endDate ?: startDate, binding.tvEndTime.text) ?: return
                applyEndDateTime(end.plusMinutes(deltaMinutes))
                binding.calendarPicker.notifyCalendarChanged()
                updateDateDisplay()
                enforceValidTimeRange(ActiveInput.END_TIME)
            }
            else -> return
        }
        syncPickerToActiveTime()
        updateTimeVisibility()
    }

    private fun syncPickerToActiveTime() {
        val timeText = when (activeInput) {
            ActiveInput.START_TIME -> binding.tvStartTime.text
            ActiveInput.END_TIME -> binding.tvEndTime.text
            else -> return
        }
        val parts = timeText.toString().split(":")
        if (parts.size != 2) return

        val hour = parts[0].trim().toIntOrNull() ?: return
        val minute = parts[1].trim().toIntOrNull() ?: return
        syncTimePickerValues(hour, minute / 5)
    }

    private fun syncTimePickerValues(hour: Int, minuteIndex: Int) {
        isSyncingTimePicker = true
        binding.pickerHour.value = hour.coerceIn(0, 23)
        binding.pickerMinute.value = minuteIndex.coerceIn(0, 11)
        isSyncingTimePicker = false
    }

    private fun hourDelta(oldValue: Int, newValue: Int): Int {
        return when {
            oldValue == 23 && newValue == 0 -> 1
            oldValue == 0 && newValue == 23 -> -1
            else -> newValue - oldValue
        }
    }

    private fun minuteDelta(oldValue: Int, newValue: Int): Int {
        return when {
            oldValue == 11 && newValue == 0 -> 5
            oldValue == 0 && newValue == 11 -> -5
            else -> (newValue - oldValue) * 5
        }
    }

    private fun enforceValidTimeRange(changedInput: ActiveInput? = activeInput) {
        if (isAllDay) return

        val start = parseDateTime(startDate, binding.tvStartTime.text)
        val end = parseDateTime(endDate ?: startDate, binding.tvEndTime.text)
        if (start == null || end == null || start.isBefore(end)) return

        if (changedInput == ActiveInput.END_TIME) {
            endDate = end.toLocalDate()
            applyStartDateTime(end.minusHours(1))
        } else {
            applyEndDateTime(start.plusHours(1))
        }

        binding.calendarPicker.notifyCalendarChanged()
        updateDateDisplay()
    }

    private fun parseDateTime(date: LocalDate?, timeText: CharSequence?): LocalDateTime? {
        val targetDate = date ?: return null
        val parts = timeText?.toString()?.split(":") ?: return null
        if (parts.size != 2) return null

        return runCatching {
            targetDate.atTime(parts[0].trim().toInt(), parts[1].trim().toInt())
        }.getOrNull()
    }

    private fun applyStartDateTime(dateTime: LocalDateTime) {
        startDate = dateTime.toLocalDate()
        binding.tvStartTime.text = dateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
    }

    private fun applyEndDateTime(dateTime: LocalDateTime) {
        endDate = dateTime.toLocalDate()
        binding.tvEndTime.text = dateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
    }

    private fun isTimeAfter(t1: String, t2: String): Boolean {
        val s = t1.split(":").map { it.trim().toInt() }
        val e = t2.split(":").map { it.trim().toInt() }

        val sMin = s[0] * 60 + s[1]
        val eMin = e[0] * 60 + e[1]

        return sMin > eMin
    }

    private fun selectDate(date: LocalDate) {
        if (activeInput == ActiveInput.END_DATE) {
            endDate = date
            if (startDate == null || startDate!!.isAfter(date)) {
                startDate = date
            }
            binding.calendarPicker.notifyCalendarChanged()
            onDateSelectionComplete()
            return
        }

        startDate = date
        endDate = null
        activeInput = ActiveInput.END_DATE
        binding.calendarPicker.notifyCalendarChanged()
        updateDateDisplay()
    }
    private fun onDateSelectionComplete() {
        animateLayoutChange()
        if (isAllDay) {
            binding.calendarContainer.visibility = View.GONE
            expandedPicker = ExpandedPicker.NONE
            activeInput = null
        } else {
            binding.calendarContainer.visibility = View.GONE
            isEditingStartTime = true // 기본 포커스를 시작 시간으로 설정
            activeInput = ActiveInput.START_TIME
            updateTimeVisibility()     // 색상 반영
            showTimePicker()
        }
        updateDateDisplay()
    }
    private fun updateDateDisplay() {
        val formatter = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)
        val highlightColor = Color.parseColor("#8BC34A")
        val defaultColor = Color.BLACK

        // 1. 시작일 업데이트
        val tvStart = binding.btnStartDate.findViewById<TextView>(R.id.tv_start_date)
        startDate?.let {
            tvStart.text = it.format(formatter)
            // 수정 모드이거나 선택 완료 상태면 검정색, 선택 중이면 초록색
            tvStart.setTextColor(if (activeInput == ActiveInput.START_DATE) highlightColor else defaultColor)
        }

        // 2. 종료일 업데이트
        val tvEnd = binding.btnEndDate.findViewById<TextView>(R.id.tv_end_date)
        // endDate가 null이면 startDate를 대신 보여줌
        val endToShow = endDate ?: startDate

        endToShow?.let {
            tvEnd.text = it.format(formatter)
            // 시작일은 있는데 종료일이 아직 선택 안 된 상태에서만 초록색 강조
            tvEnd.setTextColor(if (activeInput == ActiveInput.END_DATE) highlightColor else defaultColor)
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
                if (i == 0) setTextColor(ContextCompat.getColor(requireContext(), R.color.semantic_error))
                else if (i == 6) setTextColor(ContextCompat.getColor(requireContext(), R.color.semantic_success))
            }
        }
    }

    // 2. 캘린더 설정 및 스크롤 리스너
    private fun setupCalendar() {
        val currentMonth = java.time.YearMonth.now()
        val startMonth = currentMonth.minusMonths(12) // 1년 전
        val endMonth = currentMonth.plusMonths(12)   // 1년 후 (총 2년)
        val firstDayOfWeek = java.time.DayOfWeek.SUNDAY

        val titleFormatter = DateTimeFormatter.ofPattern("yyyy년 M월", Locale.KOREAN)
        binding.tvCurrentMonth.text = currentMonth.format(titleFormatter)

        binding.calendarPicker.setup(startMonth, endMonth, firstDayOfWeek)
        binding.calendarPicker.scrollToMonth(currentMonth)

        // 스크롤 시 상단 텍스트(2026년 2월) 업데이트
        binding.calendarPicker.monthScrollListener = { month ->
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
        val rangeLeft = container.rangeLeft
        val rangeRight = container.rangeRight

        textView.background = null
        textView.backgroundTintList = null
        root.background = null
        rangeLeft.visibility = View.GONE
        rangeRight.visibility = View.GONE

        if (day.position != com.kizitonwose.calendar.core.DayPosition.MonthDate) {
            textView.setTextColor(Color.LTGRAY)
        } else {
            val selectedColor = ContextCompat.getColor(requireContext(), R.color.semantic_info)

            when {
                // [CASE 1] 시작일과 종료일이 모두 선택되었고, 두 날짜가 서로 다를 때만 막대 표시
                startDate != null && endDate != null && startDate != endDate -> {
                    when (date) {
                        startDate -> {
                            textView.setBackgroundResource(R.drawable.drawable_circle_green)
                            textView.backgroundTintList = ColorStateList.valueOf(selectedColor)
                            textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                            rangeRight.visibility = View.VISIBLE
                        }
                        endDate -> {
                            textView.setBackgroundResource(R.drawable.drawable_circle_green)
                            textView.backgroundTintList = ColorStateList.valueOf(selectedColor)
                            textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                            rangeLeft.visibility = View.VISIBLE
                        }
                        else -> {
                            if (date.isAfter(startDate) && date.isBefore(endDate)) {
                                textView.setTextColor(selectedColor)
                                rangeLeft.visibility = View.VISIBLE
                                rangeRight.visibility = View.VISIBLE
                            } else {
                                textView.setTextColor(getCalendarDateTextColor(date))
                            }
                        }
                    }
                }

                // [CASE 2] 시작일만 선택되었거나, 시작일과 종료일이 같은 날짜일 때 (원만 표시)
                date == startDate || date == endDate -> {
                    textView.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                    textView.setBackgroundResource(R.drawable.drawable_circle_green)
                    textView.backgroundTintList = ColorStateList.valueOf(selectedColor)
                    root.background = null // 막대 제거
                }

                else -> {
                    textView.setTextColor(getCalendarDateTextColor(date))
                }
            }
        }
    }

    // 마진을 조절하여 배경 높이를 깎는 보조 함수
    private fun getCalendarDateTextColor(date: LocalDate): Int {
        val colorRes = when (date.dayOfWeek) {
            DayOfWeek.SUNDAY -> R.color.semantic_error
            DayOfWeek.SATURDAY -> R.color.semantic_success
            else -> R.color.text_primary
        }
        return ContextCompat.getColor(requireContext(), colorRes)
    }

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

    private fun animateColorSelectorChange() {
        android.transition.TransitionManager.beginDelayedTransition(
            binding.layoutScheduleName,
            android.transition.AutoTransition().apply {
                duration = 180
            }
        )
    }

    private fun observeCreateEvent() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.createScheduleEvent.collect { isSuccess ->
                when (isSuccess) {
                    true -> {
                        Toast.makeText(context, "일정이 성공적으로 저장되었습니다.", Toast.LENGTH_SHORT).show()
                        requireActivity().setResult(
                            Activity.RESULT_OK,
                            Intent().putExtra(
                                "SAVED_SCHEDULE_DATE",
                                startDate?.toString() ?: LocalDate.now().toString()
                            )
                        )
                        viewModel.resetCreateEvent()
                        requireActivity().finish()
                    }
                    false -> {
                        Toast.makeText(context, "일정 저장에 실패했습니다.", Toast.LENGTH_SHORT).show()
                        setSubmitInProgress(false)
                        viewModel.resetCreateEvent()
                    }
                    null -> {}
                }
            }
        }
    }

    private fun observeUserSettings() {

        if (isEditMode) return

        viewLifecycleOwner.lifecycleScope.launch {
            // collect가 아닌 first()를 사용하여 화면 진입 시점에 딱 한 번만 데이터를 가져옵니다.
            val settings = viewModel.userSettings.filterNotNull().first()

            // 알람: 사용자가 아직 수정 안 했다면 기본값 표시
            if (currentSelectedAlarms == null) {
                currentSelectedAlarms = settings.scheduleAlarms.toIntArray()
                updateAlarmText(currentSelectedAlarms!!)
                updateReminderFieldColor(currentSelectedAlarms!!)
            }

            // 캘린더: 사용자가 아직 수정 안 했다면 기본값 표시
            if (currentSelectedCalendarId == null) {
                val resolvedCalendarId = resolveDefaultCalendarId(settings.calendarId)
                currentSelectedCalendarId = resolvedCalendarId
                val calendarName = viewModel.getCalendarNameById(resolvedCalendarId)
                binding.tvCalendarStatus.text = calendarName
                binding.tvCalendarStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
                applyCalendarColor(resolvedCalendarId)
            }

            binding.root.post { resetInitialFormSnapshot() }
        }
    }
    private fun updateAlarmText(alarms: IntArray) {
        if (alarms.isEmpty()) {
            binding.tvRemindStatus.text = "일정 알림 안함"
        } else {
            val texts = alarms.sorted().map { minutesToText(it) } // minutesToText 함수를 여기도 복사하거나 유틸로 분리
            binding.tvRemindStatus.text = texts.joinToString(", ")
        }
    }

    private fun applyCalendarColor(calendarId: Long?) {
        if (calendarId == null || calendarId == -1L) return
        val color = viewModel.getCalendarColorById(calendarId) ?: return
        if (color == 0) return
        currentSelectedCalendarColor = color
        hasUserSelectedEventColor = false
        binding.viewColorDot.backgroundTintList = ColorStateList.valueOf(color)
        colorAdapter?.selectColor(colorIntToHex(color))
    }

    private fun resolveDefaultCalendarId(preferredId: Long): Long {
        if (preferredId == -1L) return -1L

        val projection = arrayOf(android.provider.CalendarContract.Calendars._ID)
        val cursor = requireContext().contentResolver.query(
            android.provider.CalendarContract.Calendars.CONTENT_URI,
            projection,
            null,
            null,
            null
        )

        val availableIds = mutableListOf<Long>()
        cursor?.use {
            while (it.moveToNext()) {
                availableIds.add(it.getLong(0))
            }
        }

        return when {
            preferredId in availableIds -> preferredId
            availableIds.isNotEmpty() -> availableIds.first()
            else -> -1L
        }
    }

    private fun getSaveColorInt(): Int {
        return getSelectedEventColorInt()
            ?: currentSelectedCalendarColor
            ?: Color.parseColor(selectedColorHex)
    }

    private fun getSelectedEventColorInt(): Int? {
        return if (hasUserSelectedEventColor) Color.parseColor(selectedColorHex) else null
    }

    private fun buildColorItems(selectedColorForPalette: String): List<ColorItem> {
        val colors = mutableListOf(
            ColorItem(R.color.schedule_5, "#DC354B", "#DC354B".equals(selectedColorForPalette, ignoreCase = true)),
            ColorItem(R.color.route_line_3, "#D8643F", "#D8643F".equals(selectedColorForPalette, ignoreCase = true)),
            ColorItem(R.color.route_suin_bundang, "#FFBB00", "#FFBB00".equals(selectedColorForPalette, ignoreCase = true)),
            ColorItem(R.color.route_branch_bus, "#53B332", "#53B332".equals(selectedColorForPalette, ignoreCase = true)),
            ColorItem(R.color.schedule_14, "#51AEED", "#51AEED".equals(selectedColorForPalette, ignoreCase = true)),
            ColorItem(R.color.schedule_12, "#2A4ABF", "#2A4ABF".equals(selectedColorForPalette, ignoreCase = true)),
            ColorItem(R.color.schedule_8, "#5F46DD", "#5F46DD".equals(selectedColorForPalette, ignoreCase = true)),
            ColorItem(R.color.route_line_8, "#F14C82", "#F14C82".equals(selectedColorForPalette, ignoreCase = true)),
            ColorItem(R.color.gray_600, "#666666", "#666666".equals(selectedColorForPalette, ignoreCase = true))
        )
        if (colors.none { it.isSelected }) {
            colors.add(0, ColorItem(R.color.gray_600, selectedColorForPalette, true))
        }
        return colors
    }

    private fun colorIntToHex(color: Int): String {
        return String.format("#%06X", 0xFFFFFF and color)
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

    private fun setupEditMode(){
        if (isEditMode && scheduleIdForEdit != -1L) {
            binding.btnConfirm.text = "수정하기"
            loadExistingSchedule(scheduleIdForEdit)
        } else {
            // 기존 신규 생성 로직 (오늘 날짜 기본값 설정)
            val today = LocalDate.now()
            startDate = today
            endDate = today
            binding.tvStartDate.text = today.format(dateFormatter)
            binding.tvEndDate.text = today.format(dateFormatter)
            binding.root.post { resetInitialFormSnapshot() }
        }
    }

    private fun loadExistingSchedule(id: Long) {
        viewLifecycleOwner.lifecycleScope.launch {
            val schedule = viewModel.getScheduleById(id)

            // 1. 헤더 변경
            (activity as? AddScheduleActivity)?.let { act ->
                val titleView = act.findViewById<TextView>(R.id.tv_toolbar_title)
                titleView?.text = "일정 편집"
            }

            schedule?.let { s ->
                // 1. 이름 및 메모
                binding.etScheduleName.setText(s.title)
                moveTitleCursorToEnd()
                binding.etMemo.setText(s.memo)
                moveMemoCursorToEnd()
                binding.etScheduleName.setTextColor(Color.BLACK)
                binding.etMemo.setTextColor(Color.BLACK)

                // 3. 날짜 (안전한 파싱)
                try {
                    Log.d("FixCheck", "DB에서 가져온 시작일: ${s.startDate}, 종료일: ${s.endDate}")
                    startDate = LocalDate.parse(s.startDate)
                    endDate = if (!s.endDate.isNullOrEmpty()) LocalDate.parse(s.endDate) else startDate
                    if (!s.repeatRule.isNullOrEmpty() && occurrenceDateForEdit != null && startDate != null && endDate != null) {
                        val spanDays = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate).coerceAtLeast(0)
                        startDate = occurrenceDateForEdit
                        endDate = occurrenceDateForEdit?.plusDays(spanDays)
                    }
                    Log.d("FixCheck", "파싱 후 변수 - startDate: $startDate, endDate: $endDate")
                } catch (e: Exception) {
                    Log.e("FixCheck", "파싱 에러 발생: ${e.message}")
                    val today = LocalDate.now()
                    startDate = today
                    endDate = today
                }

                // 4. UI 갱신 (반드시 변수 할당 후 호출)
                updateDateDisplay()
                binding.calendarPicker.notifyCalendarChanged()
                binding.calendarPicker.scrollToMonth(java.time.YearMonth.from(startDate!!))

                // 5. 시간 및 하루종일 여부
                isAllDay = s.isAllDay
                binding.addscheMyPhoneIv.setImageResource(
                    if (isAllDay) R.drawable.ic_toggle_selected else R.drawable.ic_toggle_unselected
                )

                // 시간 텍스트 직접 할당
                binding.tvStartTime.text = if (isAllDay) "00:00" else s.startTime
                binding.tvEndTime.text = if (isAllDay) "11:59" else s.endTime
                binding.tvStartTime.setTextColor(Color.BLACK)
                binding.tvEndTime.setTextColor(Color.BLACK)
                updateTimeVisibility()

                // 4. 반복 필드(repeatRule) 파싱 (RRULE -> RepeatInfo)
                if (!s.repeatRule.isNullOrEmpty()) {
                    // ViewModel을 통해 RepeatInfo 객체를 바로 받아옵니다.
                    currentRepeatInfo = viewModel.parseRepeatRule(s.repeatRule, s.endDate)

                    if (currentRepeatInfo != null) {
                        binding.tvRepeatStatus.text = viewModel.getRepeatDescription(currentRepeatInfo)
                        updateRepeatFieldColor(true)
                    }
                } else {
                    currentRepeatInfo = null
                    binding.tvRepeatStatus.text = "반복 안 함"
                    updateRepeatFieldColor(false)
                }

                // 5. 장소 정보 복원 (Pace 전용 JSON 우선, 없으면 일반 location 텍스트)
                if (!s.placeJson.isNullOrEmpty()) {
                    // Pace 상세 장소 정보가 있는 경우
                    try {
                        val placeRequest = com.google.gson.Gson().fromJson(s.placeJson, PlaceRequest::class.java)
                        selectedPlaceName = placeRequest.targetName
                        selectedLat = placeRequest.targetLat
                        selectedLng = placeRequest.targetLng

                        binding.tvLocationStatus.text = if (!selectedPlaceName.isNullOrBlank()) {
                            selectedPlaceName
                        } else {
                            s.location ?: "장소 정보 없음"
                        }
                        updateLocationFieldColor(true)
                    } catch (e: Exception) {
                        // 파싱 실패 시 일반 텍스트로라도 보여줌
                        binding.tvLocationStatus.text = s.location ?: "장소 정보 없음"
                        updateLocationFieldColor(true)
                    }
                } else if (!s.location.isNullOrEmpty()) {
                    // 구글 캘린더 등 외부에서 온 일반 장소 텍스트만 있는 경우
                    selectedPlaceName = s.location
                    binding.tvLocationStatus.text = s.location
                    updateLocationFieldColor(true)
                } else {
                    // 둘 다 없는 경우
                    binding.tvLocationStatus.text = "장소를 선택해 주세요"
                    updateLocationFieldColor(false)
                }

                // 6. 색상 및 알람
                val effectiveColor = when {
                    s.eventColor != null && s.eventColor != 0 -> s.eventColor
                    s.calendarColor != null && s.calendarColor != 0 -> s.calendarColor
                    else -> null
                }
                if (effectiveColor != null) {
                    val hexColor = String.format("#%06X", (0xFFFFFF and effectiveColor))
                    if (s.eventColor != null && s.eventColor != 0) {
                        changeSelectedColor(hexColor)
                        selectedColorHex = hexColor
                    } else {
                        currentSelectedCalendarColor = effectiveColor
                        hasUserSelectedEventColor = false
                        binding.viewColorDot.backgroundTintList = ColorStateList.valueOf(effectiveColor)
                        colorAdapter?.selectColor(colorIntToHex(effectiveColor))
                    }
                } else {
                    applyCalendarColor(s.calendarId)
                }
                if (s.reminders.isNotEmpty()) {
                    currentSelectedAlarms = s.reminders.toIntArray()
                    updateAlarmText(currentSelectedAlarms!!)
                }
                updateReminderFieldColor(currentSelectedAlarms ?: intArrayOf())

                // 7. 캘린더 정보
                currentSelectedCalendarId = s.calendarId
                binding.tvCalendarStatus.text = viewModel.getCalendarNameById(s.calendarId)
                binding.tvCalendarStatus.setTextColor(Color.BLACK)
                if (s.eventColor == null || s.eventColor == 0) {
                    applyCalendarColor(s.calendarId)
                }

                binding.root.post { resetInitialFormSnapshot() }
            }
        }
    }

    private fun dismissKeyboard() {
        val focusedView = requireActivity().currentFocus ?: return
        val imm =
            requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(focusedView.windowToken, 0)
        focusedView.clearFocus()
        binding.etScheduleName.clearFocus()
        binding.etMemo.clearFocus()
    }

    private fun isTouchInsideInput(event: MotionEvent): Boolean {
        return isTouchInsideView(binding.etScheduleName, event) || isTouchInsideView(binding.etMemo, event)
    }

    private fun isTouchInsideView(view: View, event: MotionEvent): Boolean {
        val rect = android.graphics.Rect()
        view.getGlobalVisibleRect(rect)
        return rect.contains(event.rawX.toInt(), event.rawY.toInt())
    }


    // --- 별도의 메서드로 정의할 업데이트 관찰 로직 ---
    private fun observeUpdateEvent() {
        viewLifecycleOwner.lifecycleScope.launch {
            // ViewModel에 updateScheduleEvent가 있다고 가정 (createScheduleEvent와 유사한 구조)
            viewModel.updateScheduleEvent.collect { isSuccess ->
                when (isSuccess) {
                    true -> {
                        Toast.makeText(context, "일정이 수정되었습니다.", Toast.LENGTH_SHORT).show()
                        viewModel.resetUpdateEvent() // 이벤트 초기화
                        applyEditActivityResult()
                        requireActivity().finish()   // 액티비티 종료 및 홈으로 복귀
                    }
                    false -> {
                        Toast.makeText(context, "일정 수정에 실패했습니다.", Toast.LENGTH_SHORT).show()
                        setSubmitInProgress(false)
                        viewModel.resetUpdateEvent()
                    }
                    null -> {}
                }
            }
        }
    }

    private fun applyEditActivityResult() {
        viewModel.lastEditResult.value?.let { result ->
            requireActivity().setResult(
                Activity.RESULT_OK,
                Intent().apply {
                    putExtra("UPDATED_SCHEDULE_ID", result.scheduleId)
                    putExtra("UPDATED_OCCURRENCE_DATE", result.occurrenceDate)
                    putExtra("UPDATED_SCHEDULE_TYPE", result.scheduleType)
                }
            )
            viewModel.clearLastEditResult()
        }
    }

    private fun setSubmitInProgress(inProgress: Boolean) {
        isSubmitInProgress = inProgress
        if (_binding == null) return
        binding.btnConfirm.isClickable = !inProgress
        binding.btnCancel.isClickable = !inProgress
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class DayViewContainer(view: View, val onDateSelected: (LocalDate) -> Unit) : com.kizitonwose.calendar.view.ViewContainer(view) {
    val rootLayout: androidx.constraintlayout.widget.ConstraintLayout = view.findViewById(R.id.root_layout)
    val textView: android.widget.TextView = view.findViewById(R.id.calendarDayText)
    val rangeLeft: View = view.findViewById(R.id.rangeLeft)
    val rangeRight: View = view.findViewById(R.id.rangeRight)

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

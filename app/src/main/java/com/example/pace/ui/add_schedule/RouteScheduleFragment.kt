package com.example.pace.ui.add_schedule

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.text.TextUtils.replace
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Delete
import com.example.pace.R
import com.example.pace.data.model.request.CreateScheduleRequest
import com.example.pace.data.model.request.PlaceRequest
import com.example.pace.data.model.request.ReminderRequest
import com.example.pace.data.model.request.RouteDetail
import com.example.pace.data.model.request.RouteDetailRequest
import com.example.pace.data.model.request.RouteRequest
import com.example.pace.data.model.request.TransitDetailRequest
import com.example.pace.data.model.request.UpdateScheduleEditRouteRequest
import com.example.pace.data.model.request.UpdateScheduleRequest
import com.example.pace.data.model.request.UpdateScheduleRouteRequest
import com.example.pace.data.model.response.RouteResponse
import com.example.pace.data.util.RouteConstants
import com.example.pace.data.viewmodel.SettingsViewModel
import com.example.pace.databinding.FragmentRouteScheduleBinding
import com.example.pace.databinding.ItemRouteDetailBriefBinding
import com.example.pace.databinding.ItemRouteVehicleBinding
import com.example.pace.ui.RouteCalculator
import com.example.pace.ui.main.MainActivity
import com.example.pace.data.viewmodel.ScheduleViewModel
import com.example.pace.ui.onboarding.CalendarSelectFragment
import com.google.gson.Gson
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.view.MonthDayBinder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.compareTo

@AndroidEntryPoint
class RouteScheduleFragment : Fragment() {

    // 바인딩
    private var _binding: FragmentRouteScheduleBinding? = null
    private val binding get() = _binding!!

    // ViewModel
    private val viewModel: ScheduleViewModel by viewModels()

    // 수정 모드 관련 변수
    private var isEditMode = false
    private var scheduleId: Long = -1L

    // 경로 검색 -> 일정 추가 시 받는 ROUTE_DETAIL
    private var route: RouteResponse? = null

    // 경로탐색으로 전환될 때 같이 보낼 색깔(선택된 일정 색)
    private var selectedColor: String = "#53B332"

    // 경로 탐색에서 받아온 데이터
    private var routeJson: String? = null
    private var earlyArriveTime: Int = -1

    // 출발지 정보
    private var lastStartName: String? = null
    private var lastStartLat: Double = Double.NaN
    private var lastStartLng: Double = Double.NaN

    // 도착지 정보
    private var lastDestName: String? = null
    private var lastDestLat: Double = Double.NaN
    private var lastDestLng: Double = Double.NaN
    private var lastRouteJson: String? = null

    // 날짜/시간
    private var isEditingStartTime: Boolean = true
    private var startDate: LocalDate? = null
    private var endDate: LocalDate? = null
    private val dateFormatter = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)

    // 색상/알림/캘린더
    private var selectedColorHex: String = "#53B332" // 기본 색상

    private var isFirstLoad = true // 최상단 멤버 변수로 추가
    private var currentSelectedAlarms: IntArray? = null
    private var currentSelectedStartAlarms: IntArray? = null
    private var currentSelectedCalendarId: Long? = null
    private var currentSelectedCalendarName: String? = null
    private var currentSelectedCalendarColor: Int? = null

    // 런처/콜백
    private val routeSearchLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult

            // 1. 클래스 멤버 변수(last...)에 직접 할당하여 데이터 저장
            lastStartName = data.getStringExtra("START_NAME")
            lastStartLat = data.getDoubleExtra("START_LAT", Double.NaN)
            lastStartLng = data.getDoubleExtra("START_LNG", Double.NaN)

            lastDestName = data.getStringExtra("END_NAME")
            lastDestLat = data.getDoubleExtra("END_LAT", Double.NaN)
            lastDestLng = data.getDoubleExtra("END_LNG", Double.NaN)

            earlyArriveTime = data.getIntExtra("EARLY_ARRIVE_TIME", 0)
            lastRouteJson = data.getStringExtra("ROUTE_DETAIL")

            // 2. UI 업데이트 (저장된 전역 변수 사용)
            val gson = Gson()
            val routeObj = gson.fromJson(lastRouteJson, RouteResponse::class.java)
            updateRouteInfo(lastStartName ?: "출발지", lastDestName ?: "도착지", routeObj)

            // 3. UI 가시성 처리
            binding.deleteRouteIv.visibility = View.VISIBLE
            binding.deleteRouteIv.bringToFront()
        }
    }

    private val backPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            val dialog = AddCancelDialog(requireContext()) {
                if (parentFragmentManager.backStackEntryCount > 0) {
                    parentFragmentManager.popBackStack()
                } else {
                }
            }
            dialog.show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentRouteScheduleBinding.inflate(inflater, container, false)
        requireActivity().onBackPressedDispatcher.addCallback(backPressedCallback)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.viewColorDot.alpha = 0f

        arguments?.let { bundle ->
            isEditMode = bundle.getBoolean("isEdit", false)
            scheduleId = bundle.getLong("SCHEDULE_ID", -1L)

            Log.d("ConfirmMode", "Fragment에서 데이터 수신 - Edit: $isEditMode, ID: $scheduleId")
        }

        // 캘린더뷰와 온보딩 값 초기화
        setupCalendar()
        setupLegend()
        setupMonthNavigation()
        observeUserSettings()


        val incomingId = arguments?.getLong("SCHEDULE_ID", -1L) ?: -1L
        if (incomingId != -1L) {
            setupEditMode(incomingId)
        }

        binding.deleteRouteIv.setOnClickListener {
            Log.d("ROUTE_DELETE", "삭제 버튼 클릭")
            val dialog = DeleteRouteDialog(requireContext()) {
                // 1. 모든 관련 데이터 변수를 "진짜" 초기 상태로 리셋
                this.route = null           // 경로 객체
                this.lastRouteJson = null   // JSON 문자열

                // 출발지 정보 초기화
                this.lastStartName = null
                this.lastStartLat = Double.NaN
                this.lastStartLng = Double.NaN

                // 도착지 정보 초기화
                this.lastDestName = null
                this.lastDestLat = Double.NaN
                this.lastDestLng = Double.NaN

                // 2. UI를 초기 상태로 되돌리기 (null을 넘기면 내부의 removeAllViews()가 작동함)
                updateRouteInfo("", "", null)
            }
            dialog.show()
        }
        val today = LocalDate.now()
        startDate = today
        endDate = today
        binding.tvStartDate.text = today.format(dateFormatter)
        binding.tvEndDate.text = today.format(dateFormatter)

        val startName = arguments?.getString("START_NAME") ?: "미지정"
        val endName = arguments?.getString("END_NAME") ?: "미지정"
        val earlyTime = arguments?.getInt("EARLY_ARRIVE_TIME", 0)
        // 비어있을 때 문자열로 넣으면 에러남
        val routeDetail = arguments?.getString("ROUTE_DETAIL") // 뒤에 ?: "데이터 없음" 삭제

        // routeDetail 파싱해 경로 동적 바인딩 + route의 값에 따라 UI 업데이트
        val gson = Gson()
        route = gson.fromJson(routeDetail, RouteResponse::class.java)
        updateRouteInfo(startName, endName, route)
        if (!routeDetail.isNullOrEmpty()) {
            try {
                val gson = Gson()
                route = gson.fromJson(routeDetail, RouteResponse::class.java)

                // 파싱된 route 객체로 UI 업데이트
                updateRouteInfo(startName, endName, route)

                // 경로가 확실히 있으므로 삭제 버튼 활성화
                binding.deleteRouteIv.visibility = View.VISIBLE
                binding.deleteRouteIv.bringToFront() // ⭐ 다른 뷰들보다 위로 올리기
            } catch (e: Exception) {
                // 만약 JSON 형식이 잘못되었다면 safe하게 처리
                Log.e("RouteError", "JSON 파싱 실패: ${e.message}")
                route = null
                binding.deleteRouteIv.visibility = View.GONE
                updateRouteInfo(startName, endName, null)
            }
        } else {
            // 2. routeDetail이 null이거나 비어있을 때
            route = null
            binding.deleteRouteIv.visibility = View.GONE
            updateRouteInfo(startName, endName, null)
        }
        // routeDetail이 null일 경우를 대비해 ?와 ?: 를 사용합니다.
        val detailSummary = if ((routeDetail?.length ?: 0) > 20) {
            routeDetail?.take(20) + "..."
        } else {
            routeDetail ?: "데이터 없음"
        }

        initTimePickers()

        updateTimeVisibility()



        //일정 알람 화면으로 갔다가 올 때 데이터 받는 부분
        parentFragmentManager.setFragmentResultListener("ROUTE_ALARM_KEY", viewLifecycleOwner) { _, bundle ->
            // 경로 일정 로직 수행
            val resultText = bundle.getString("selectedAlarm")
            val resultMinutes = bundle.getIntArray("selectedAlarmMinutes")

            if (resultMinutes != null) {
                // 프래그먼트 내부 변수 업데이트
                currentSelectedAlarms = resultMinutes

                // UI 업데이트 (ID: tvAlarmStatus 확인)
                binding.tvAlarmStatus.text = resultText
                binding.tvAlarmStatus.setTextColor(Color.BLACK)
            }
        }


        // 출발 알림 결과 받기
        parentFragmentManager.setFragmentResultListener("startAlarmKey", viewLifecycleOwner) { _, bundle ->
            val resultText = bundle.getString("selectedAlarm") ?: "출발 알림 안함"
            val resultMinutes = bundle.getIntArray("selectedAlarmMinutes")

            if (resultMinutes != null) {
                currentSelectedStartAlarms = resultMinutes
                binding.tvStartalarmStatus.text = resultText
                binding.tvStartalarmStatus.setTextColor(Color.BLACK)
            }
        }

        // 캘린더 선택 결과 받기
        parentFragmentManager.setFragmentResultListener("ROUTE_CALENDAR_KEY", viewLifecycleOwner) { _, bundle ->
            val selectedId = bundle.getLong("calendarId", -1L)
            val selectedName = bundle.getString("calendarName") ?: "내 일정"
            val calendarColor = bundle.getInt("selectedCalendarColor", -1)

            if (selectedId != -1L) {
                currentSelectedCalendarId = selectedId
                currentSelectedCalendarName = selectedName

                binding.tvCalendarStatus.text = selectedName
                binding.tvCalendarStatus.setTextColor(Color.BLACK)

                if (calendarColor != -1) {
                    currentSelectedCalendarColor = calendarColor
                    showColorDot(calendarColor)
                }
            }
        }

        binding.btnCalendar.setOnClickListener {
            // 여기도 SelectCalendarFragment로 통일!
            val fragment = SelectCalendarFragment()
            val bundle = Bundle().apply {
                putLong("currentCalendarId", currentSelectedCalendarId ?: -1L)
                putString("requestKey", "ROUTE_CALENDAR_KEY") // 루트용 키
            }
            fragment.arguments = bundle

            parentFragmentManager.beginTransaction()
                .setCustomAnimations(android.R.anim.slide_in_left, android.R.anim.slide_out_right, android.R.anim.slide_in_left, android.R.anim.slide_out_right)
                .replace(android.R.id.content, fragment)
                .addToBackStack(null)
                .commit()
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

        // 1. 결과 관찰 (성공 시 화면 닫기)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.createScheduleEvent.collect { isSuccess ->
                    when (isSuccess) {
                        true -> {
                            // 일정 저장 성공 시 알람 예약 실행
                            scheduleSavedAlarms()
                            Toast.makeText(context, "일정이 저장되었습니다.", Toast.LENGTH_SHORT).show()
                            viewModel.resetCreateEvent() // 이벤트 초기화
                            requireActivity().finish()   // 화면 종료
                        }

                        false -> {
                            //Toast.makeText(context, "일정 저장에 실패했습니다.", Toast.LENGTH_SHORT).show()
                            viewModel.resetCreateEvent()
                        }

                        null -> { /* 대기 상태 */
                        }
                    }
                }
            }
        }

        binding.calendarPicker.dayBinder = object : MonthDayBinder<DayViewContainer> {
            override fun create(view: View) = DayViewContainer(view) { date ->
                // 클릭 시에도 범위 체크를 해서 작동하지 않도록 방어
                val today = LocalDate.now()
                val maxDate = today.plusDays(29)
                if (date in today..maxDate) {
                    selectDate(date)
                }
            }
            override fun bind(container: DayViewContainer, day: CalendarDay) {
                container.currentDay = day
                val date = day.date
                val textView = container.textView
                val root = container.rootLayout

                //경로 일정 30일까지만 가능하게함
                val today = LocalDate.now()
                val maxDate = today.plusDays(29)


                textView.text = date.dayOfMonth.toString()
                if (day.position != com.kizitonwose.calendar.core.DayPosition.MonthDate || date < today || date > maxDate) {
                    textView.setTextColor(Color.LTGRAY)
                    textView.background = null
                    root.background = null
                    // 범위를 벗어난 날짜는 클릭 리스너를 무효화하거나 처리하지 않음
                    container.view.isEnabled = false // 클릭 방지
                    container.view.alpha = 0.4f     // 비활성화 시각화
                } else {
                    // 범위를 만족하는 날짜들
                    container.view.isEnabled = true
                    container.view.alpha = 1f

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
                        // 기간 사이
                        startDate != null && endDate != null && date.isAfter(startDate) && date.isBefore(endDate) -> {
                            textView.setTextColor(Color.BLACK)
                            textView.background = null
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

        binding.btnConfirm.setOnClickListener {
            val scheduleName = binding.etScheduleName.text.toString().trim()
            val finalStartDate = startDate ?: LocalDate.now()
            val finalEndDate = endDate ?: finalStartDate
            val startTime = binding.tvStartTime.text.toString()
            val endTime = binding.tvEndTime.text.toString()
            val saveColorInt = getSaveColorInt()
            val saveColorHex = colorIntToHex(saveColorInt)

            // [디버깅] 현재 모드와 ID 확인 - 로그캣에서 "ConfirmMode"를 검색하세요.
            Log.d("ConfirmMode", "isEditMode: $isEditMode, scheduleId: $scheduleId, selectedColor: $selectedColorHex, calendarId: $currentSelectedCalendarId")
            // 경로가 없다면 일반 일정으로 저장
            if(lastRouteJson == null && route == null){
                val dialog = NoRouteDialog(requireContext()){
                    // todo: 수정 시 기존 경로 일정 삭제되는 지 확인
                    if(isEditMode && scheduleId != -1L){
                        viewModel.deleteSchedule(scheduleId, true)
                    }
                    saveAsNormalSchedule()
                }
                dialog.show()
            }else{
                // 1. 리마인더 조립
                val reminders = mutableListOf<ReminderRequest>()
                currentSelectedAlarms?.forEach { reminders.add(ReminderRequest("EVENT", it)) }
                currentSelectedStartAlarms?.forEach { reminders.add(ReminderRequest("DEPARTURE", it)) }

                // 2. JSON 문자열 객체 파싱
                val routeObject: RouteResponse? = try {
                    if (!lastRouteJson.isNullOrBlank()) {
                        Gson().fromJson(lastRouteJson, RouteResponse::class.java)
                    } else {
                        route
                    }
                } catch (e: Exception) {
                    Log.e("RouteParseError", "JSON 파싱 중 에러 발생: ${e.message}", e) // 💡 반드시 추가!
                    null
                }

                // 3. routeDetails 변환
                val routeDetailsRequest = routeObject?.routeDetails?.map { detail ->
                    RouteDetailRequest(
                        sequence = detail.sequence,
                        startLat = detail.startLat,
                        startLng = detail.startLng,
                        endLat = detail.endLat,
                        endLng = detail.endLng,
                        duration = detail.duration,
                        distance = detail.distance,
                        description = detail.description ?: "",
                        points = detail.points,
                        transitDetail = detail.transitDetail?.let { transit ->
                            TransitDetailRequest(
                                transitType = transit.transitType,
                                lineName = transit.lineName,
                                lineColor = transit.lineColor,
                                stopCount = transit.stopCount ?: 0,
                                departureStop = transit.departureStop,
                                arrivalStop = transit.arrivalStop,
                                departureTime = transit.departureTime,
                                arrivalTime = transit.arrivalTime,
                                shortName = transit.shortName,
                                locationLat = transit.locationLat ?: 0.0,
                                locationLng = transit.locationLng ?: 0.0,
                                headsign = transit.headsign,
                                stationPath = transit.stationPath
                            )
                        }
                    )
                } ?: emptyList()

                // 4. [수정] 모드 판정 및 호출
                // 여기서 scheduleId가 정상적으로 (예: 54) 찍히는지 로그를 확인해야 합니다.
                if (isEditMode && scheduleId != -1L) {
                    Log.d("ConfirmMode", "수정 로직 실행 - ID: $scheduleId")

                    val routeRequest = UpdateScheduleEditRouteRequest(
                        originName = lastStartName ?: "",
                        originLat = lastStartLat,
                        originLng = lastStartLng,
                        destName = lastDestName ?: "",
                        destLat = lastDestLat,
                        destLng = lastDestLng,
                        totalTime = routeObject?.totalTime ?: 0,
                        totalDistance = routeObject?.totalDistance ?: 0,
                        arrivalTime = routeObject?.arrivalTime,
                        departureTime = routeObject?.departureTime,
                        routeDetails = routeDetailsRequest
                    )

                    val generalRequest = UpdateScheduleRequest(
                        title = scheduleName,
                        memo = binding.etMemo.text?.toString(),
                        isAllDay = false,
                        startDate = finalStartDate.toString(),
                        endDate = finalEndDate.toString(),
                        startTime = startTime,
                        endTime = endTime,
                        calendarId = currentSelectedCalendarId?.toString(), // 💡 캘린더 반영
                        color = saveColorHex,                               // 💡 선택한 캘린더 색상 반영
                        isPathIncluded = true,
                        repeatInfo = null,
                        place = PlaceRequest(lastDestName ?: "", lastDestLat, lastDestLng),
                        reminders = reminders
                    )

                    viewModel.updateRouteScheduleCombined(scheduleId, generalRequest, routeRequest)

                } else {
                    Log.d("ConfirmMode", "생성 로직 실행 (신규 일정 생성)")

                    val routeObj = if (routeObject != null) {
                        RouteRequest(
                            originName = lastStartName ?: "",
                            originLat = lastStartLat,
                            originLng = lastStartLng,
                            destName = lastDestName ?: "",
                            destLat = lastDestLat,
                            destLng = lastDestLng,
                            totalTime = routeObject.totalTime,
                            totalDistance = routeObject.totalDistance,
                            arrivalTime = routeObject.arrivalTime,
                            departureTime = routeObject.departureTime,
                            routeDetails = routeDetailsRequest
                        )
                    } else null

                    val createRequest = CreateScheduleRequest(
                        title = scheduleName,
                        isAllDay = false,
                        startDate = finalStartDate.toString(),
                        endDate = finalEndDate.toString(),
                        startTime = startTime,
                        endTime = endTime,
                        memo = binding.etMemo.text.toString(),
                        isPathIncluded = (routeObj != null),
                        isRepeat = false,
                        repeatInfo = null,
                        place = PlaceRequest(lastDestName ?: "", lastDestLat, lastDestLng),
                        reminders = reminders,
                        route = routeObj,
                        color = saveColorHex, // 💡 선택한 캘린더 색상 반영
                        calendarId = currentSelectedCalendarId?.toString() // 💡 서버 명세에 맞춰 String으로 추가
                    )
                    // ViewModel의 createSchedule 호출
                    Log.d("ConfirmMode", "..., calendarId: $currentSelectedCalendarId")
                    viewModel.createSchedule(createRequest, null, currentSelectedCalendarId, saveColorInt)
                }

                // 서버 응답 여부와 관계없이 로컬 알람 예약 로직 즉시 실행
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.updateScheduleEvent.collect { isSuccess ->
                    if (isSuccess == true) {
                        // 💡 일정 수정 성공 시 알람 예약 실행
                        scheduleSavedAlarms()
                        Toast.makeText(context, "일정이 수정되었습니다.", Toast.LENGTH_SHORT).show()
                        viewModel.resetUpdateEvent() // 이벤트 소모
                        activity?.finish()
                    } else if (isSuccess == false) {
                        Toast.makeText(context, "수정에 실패했습니다.", Toast.LENGTH_SHORT).show()
                        viewModel.resetUpdateEvent()
                    }
                }
            }
        }

        binding.btnCancel.setOnClickListener {
            val dialog = AddCancelDialog(requireContext()){
                if (parentFragmentManager.backStackEntryCount > 0) {
                    parentFragmentManager.popBackStack()
                } else {
                    requireActivity().finish()
                }
            }
            dialog.show()
        }

        setupKeyboardVisibilityListener()

        binding.btnRemindalarm.setOnClickListener {
            val alarmFragment = AlarmScheduleFragment()
            val bundle = Bundle().apply {
                // 💡 키 이름을 "selectedAlarmMinutes"로 통일!
                putIntArray("selectedAlarmMinutes", currentSelectedAlarms)
                putString("requestKey", "ROUTE_ALARM_KEY") // 💡 전용 키 전달
            }
            alarmFragment.arguments = bundle

            requireActivity().supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, alarmFragment)
                .addToBackStack(null)
                .commit()
        }

// 출발 알림 버튼 클릭 시
        binding.btnStartalarm.setOnClickListener {
            val startFragment = AlarmStartFragment() // 또는 사용하는 알람 프래그먼트
            val bundle = Bundle().apply {
                // 현재 이미 선택된 알람 리스트가 있다면 넘겨줌
                putIntArray("selectedAlarmMinutes", currentSelectedStartAlarms)
                // 만약 공용 알람창을 쓴다면 키도 함께 전달
                putString("requestKey", "startAlarmKey")
            }
            startFragment.arguments = bundle

            parentFragmentManager.beginTransaction()
                .setCustomAnimations(
                    android.R.anim.slide_in_left,
            android.R.anim.slide_out_right,
            android.R.anim.slide_in_left,
            android.R.anim.slide_out_right)
                .replace(android.R.id.content, startFragment)
                .addToBackStack(null)
                .commit()
        }


        setFragmentResultListener("scheduleAlarmKey") { _, bundle ->
            val result = bundle.getString("selectedAlarm")
            result?.let {
                binding.tvAlarmStatus.text = it
                binding.tvAlarmStatus.setTextColor(Color.BLACK)
            }
        }


        binding.btnStartDate.setOnClickListener { showCalendar() }
        binding.tvStartTime.setOnClickListener {
            isEditingStartTime = true
            updateTimeVisibility()
            showTimePicker()
        }


        binding.btnEndDate.setOnClickListener { showCalendar() }
        binding.tvEndTime.setOnClickListener {
            isEditingStartTime = false
            updateTimeVisibility()
            showTimePicker()
        }


        binding.viewColorDot.setOnClickListener {
            if (binding.layoutColorSelector.visibility == View.GONE) {
                binding.layoutColorSelector.visibility = View.VISIBLE

                // 💡 중요: 캘린더 뷰 자체가 아니라, '감싸고 있는 컨테이너'를 GONE 시킵니다.
                // XML에서 calendarPicker를 감싸는 레이아웃 ID를 확인하세요. (예: calendarPickerContainer)
                binding.calendarContainer.visibility = View.GONE
                binding.timePickerContainer.visibility = View.GONE
            } else {
                binding.layoutColorSelector.visibility = View.GONE

                // 💡 색상창을 닫을 때 다시 보여주기
                binding.calendarContainer.visibility = View.VISIBLE

                // 다시 나타날 때 높이를 재계산하도록 강제 호출
                binding.calendarPicker.post {
                    binding.calendarPicker.requestLayout()
                }
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

        updateTimeVisibility()

        // 일정 추가 -> 루트 프래그먼트로 데이터 전달
        binding.btnRoute.setOnClickListener {
            Log.d("DEBUG_TAG", "btnRoute 클릭")
            val dateToPass = startDate?.toString() ?: LocalDate.now().toString()
            val rawTime = binding.tvStartTime.text.toString() // 예: "10:00"
            val timeToPass = if (rawTime.length == 5) "$rawTime:00" else rawTime


            val scheduleName = binding.etScheduleName.text.toString()
            val startTime = binding.tvStartTime.text.toString()
            val intent = Intent(
                requireContext(),
                MainActivity::class.java
            ).apply {
                putExtra("ACTION_MODE", "SCHEDULE_ROUTE")

                putExtra("SCHEDULE_NAME", scheduleName)
                putExtra("SCHEDULE_COLOR", colorIntToHex(getSaveColorInt()))
                putExtra("SCHEDULE_DATE", dateToPass) // 받는 쪽에서 "SCHEDULE_DATE"로 꺼냄
                putExtra("SCHEDULE_TIME", timeToPass) // 받는 쪽에서 "SCHEDULE_TIME"으로 꺼냄
                putExtra("EARLY_ARRIVE_TIME", earlyArriveTime)

                // ⭐ 출발지 정보 전달
                if (lastStartName != null) {
                    putExtra("START_NAME", lastStartName)
                    putExtra("START_LAT", lastStartLat)
                    putExtra("START_LNG", lastStartLng)
                }

                // ⭐ 도착지 정보 전달
                if (lastDestName != null) {
                    putExtra("END_NAME", lastDestName)
                    putExtra("END_LAT", lastDestLat)
                    putExtra("END_LNG", lastDestLng)
                }
            }
            routeSearchLauncher.launch(intent)
        }
    }


    private fun parseRouteData(json: String?): RouteRequest? {
        return try {
            Gson().fromJson(json, RouteRequest::class.java)
        } catch (e: Exception) {
            null
        }
    }

    // [추가] 알람 텍스트 매핑 함수
    private fun mapAlarmTextToMinutes(statusText: String): Int {
        return when {
            statusText.contains("10분") -> 10
            statusText.contains("30분") -> 30
            statusText.contains("1시간") -> 60
            else -> 0
        }
    }

    // 선택된 날짜/시간과 알림 설정을 기반으로 실제 알람을 예약
    private fun scheduleSavedAlarms() {
        val finalStartDate = startDate ?: LocalDate.now()
        val startTime = binding.tvStartTime.text.toString() // "HH:mm"
        
        try {
            // 날짜와 시간을 합쳐서 로컬 시간 타임스탬프 생성
            val dateTimeStr = "${finalStartDate}T${startTime}:00"
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())

            val scheduleTimeMillis = sdf.parse(dateTimeStr)?.time ?: System.currentTimeMillis()
            
            Log.d("PaceAlarm", "알람 예약 프로세스 시작: $dateTimeStr (현지시간 Millis: $scheduleTimeMillis)")

            // 일정 알림 예약
            val eventAlarms = currentSelectedAlarms
            if (eventAlarms != null && eventAlarms.isNotEmpty()) {
                eventAlarms.forEach { minutes ->
                    com.example.pace.data.util.AlarmScheduler.schedulePaceAlarm(
                        requireContext(),
                        scheduleTimeMillis,
                        minutes
                    )
                    Log.d("PaceAlarm", "일정 알림 예약 명령 전송: $minutes 분 전")
                }
            } else {
                Log.w("PaceAlarm", "예약할 '일정 알림' 데이터가 없습니다.")
            }
            
            // 출발 알림(DEPARTURE) 예약
            val departAlarms = currentSelectedStartAlarms
            if (departAlarms != null && departAlarms.isNotEmpty()) {
                departAlarms.forEach { minutes ->
                    com.example.pace.data.util.AlarmScheduler.schedulePaceAlarm(
                        requireContext(),
                        scheduleTimeMillis,
                        minutes
                    )
                    Log.d("PaceAlarm", "출발 알람 예약 명령 전송: $minutes 분 전")
                }
            } else {
                Log.w("PaceAlarm", "예약할 '출발 알림' 데이터가 없습니다.")
            }
        } catch (e: Exception) {
            Log.e("PaceAlarm", "알람 예약 로직 실행 중 오류 발생: ${e.message}")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun setRouteToNull() {
        this.route = null
    }

    // 데이터 동적 바인딩
    @SuppressLint("SetTextI18n")
    private fun updateRouteInfo(startName: String, endName: String, route: RouteResponse?) {
        binding.routeBriefLl.removeAllViews()
        binding.routeVehicleLl.removeAllViews()

        if (route != null && route.routeDetails.isNotEmpty()) {
            // 1. 전체 경로의 첫 번째 상세 정보에서 출발지 좌표 추출
            val firstStep = route.routeDetails.first()
            this.lastStartName = startName
            this.lastStartLat = firstStep.startLat
            this.lastStartLng = firstStep.startLng

            // 2. 전체 경로의 마지막 상세 정보에서 도착지 좌표 추출
            val lastStep = route.routeDetails.last()
            this.lastDestName = endName
            this.lastDestLat = lastStep.endLat
            this.lastDestLng = lastStep.endLng

            // 경로 출발, 도착지 추가
            binding.routeIv.setColorFilter(R.color.black)
            binding.routeTv.text = "$startName -> $endName"
            binding.routeTv.setTextColor(requireContext().getColor(R.color.text_primary))
            // 일직선 경로 추가
            binding.routeInfoCl.visibility = View.VISIBLE
            binding.timeTv.text = RouteCalculator.convertUtcToKst(route.departureTime) + " - " + RouteCalculator.convertUtcToKst(route.arrivalTime)
            binding.totalTimeTv.text = if(route.totalTime / 3600L > 0 ){
                val time = route.totalTime % 3600L
                if (time / 60L > 0) {
                    "${route.totalTime / 3600L}시간 ${time / 60L}분"
                } else {
                    "${route.totalTime / 3600L}시간"
                }
            } else {
                "${route.totalTime / 60L}분"
            }

            route.routeDetails.forEachIndexed { index, data ->
                val briefBinding = ItemRouteDetailBriefBinding.inflate(
                    LayoutInflater.from(context),
                    binding.routeBriefLl,
                    false
                )

                // 3-1. 걷기 (TransitDetail이 null인 경우)
                if (data.transitDetail == null) {
                    if (data.sequence == 1) { // 첫 번째 순서면 사람 아이콘
                        briefBinding.itemRouteDetailBriefIv.setImageResource(R.drawable.ic_people)
                    } else {
                        briefBinding.itemRouteDetailBriefIv.visibility = View.GONE
                        briefBinding.itemRouteDetailBriefTv.updatePadding(0)
                    }
                    briefBinding.itemRouteDetailBriefTv.text = "${data.duration / 60}분"
                    briefBinding.itemRouteDetailBriefTv.setTextColor(
                        ContextCompat.getColor(
                            context,
                            R.color.gray_600
                        )
                    )

                    // 마지막 단계(하차) 처리
                    // 리스트의 마지막 인덱스인지 확인
                    if (index == route.routeDetails.size - 1) {
                        val vehicleBinding = ItemRouteVehicleBinding.inflate(
                            LayoutInflater.from(context),
                            binding.routeVehicleLl,
                            false
                        )
                        vehicleBinding.itemRouteVehicleIv.setImageResource(R.drawable.ic_route_item_arrival_icon)
                        vehicleBinding.itemRouteVehicleLineTv.text = "도착"
                        vehicleBinding.itemRouteVehicleLineTv.setTextColor(
                            ContextCompat.getColor(
                                context,
                                R.color.black
                            )
                        )
                        vehicleBinding.itemRouteVehicleView.visibility = View.GONE
                        vehicleBinding.itemRouteVehicleTv.text = endName

                        binding.routeVehicleLl.addView(vehicleBinding.root)
                    }
                }
                // 3-2. 대중교통 (버스, 지하철)
                else {
                    val vehicleBinding = ItemRouteVehicleBinding.inflate(
                        LayoutInflater.from(context),
                        binding.routeVehicleLl,
                        false
                    )

                    // 아이콘 및 색상 설정
                    val layoutDrawable =
                        ContextCompat.getDrawable(requireContext(), R.drawable.ic_route_detail)
                            ?.mutate() as LayerDrawable
                    val iconShape = layoutDrawable.findDrawableByLayerId(R.id.ic_route_detail_color)
                        .mutate() as GradientDrawable
                    val briefBg =
                        briefBinding.itemRouteDetailBriefTv.background.mutate() as GradientDrawable

                    // 색상 파싱 (서버에서 #RRGGBB 형태로 온다고 가정, 실패 시 기본값 검정)
                    val lineColorCode = try {
                        Color.parseColor(data.transitDetail.lineColor ?: "#000000")
                    } catch (e: Exception) {
                        Color.BLACK
                    }

                    // 교통 수단별 아이콘 변경
                    when (data.transitDetail.transitType) {
                        "BUS" -> {
                            val busDrawable = ContextCompat.getDrawable(context, R.drawable.ic_bus)
                            layoutDrawable.setDrawableByLayerId(
                                R.id.ic_route_detail_vehicle,
                                busDrawable
                            )
                        }

                        "SUBWAY" -> {
                            val subwayDrawable =
                                ContextCompat.getDrawable(context, R.drawable.ic_subway)
                            layoutDrawable.setDrawableByLayerId(
                                R.id.ic_route_detail_vehicle,
                                subwayDrawable
                            )
                        }
                    }

                    // 색상 적용
                    iconShape.setColor(lineColorCode)
                    briefBg.setColor(lineColorCode)

                    // [상단 바] 정보 설정
                    briefBinding.itemRouteDetailBriefIv.setImageDrawable(layoutDrawable)
                    briefBinding.itemRouteDetailBriefTv.text = "${data.duration / 60}분"

                    // [하단 리스트] 상세 정보 설정
                    vehicleBinding.itemRouteVehicleIv.setImageDrawable(layoutDrawable)
                    vehicleBinding.itemRouteVehicleLineTv.text =
                        data.transitDetail.lineName // shortName -> lineName (데이터 모델 확인 필요)
                    vehicleBinding.itemRouteVehicleLineTv.setTextColor(lineColorCode)
                    vehicleBinding.itemRouteVehicleTv.text =
                        "${data.transitDetail.departureStop} 승차"

                    binding.routeVehicleLl.addView(vehicleBinding.root)
                }

                // 상단 바(Brief) 뷰 추가 (Weight 적용)
                val weight = RouteCalculator.calculateWeight(data.duration)
                val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
                binding.routeBriefLl.addView(briefBinding.root, params)
            }
        } else {
            Log.d("DEBUG_TAG", "route == null")
            // 기본 상태
            binding.deleteRouteIv.visibility = View.GONE
            binding.routeTv.text = "경로"
            binding.routeTv.setTextColor(requireContext().getColor(R.color.gray_500))
            binding.divider.visibility = View.GONE
            binding.routeInfoCl.visibility = View.GONE
        }
    }


    private fun changeSelectedColor(colorStr: String) {
        selectedColor = colorStr
        selectedColorHex = colorStr

        val color = Color.parseColor(colorStr)
        showColorDot(color)
        // 수동 색상 선택 시 캘린더 색상 우선순위 해제
        currentSelectedCalendarColor = null

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
        val minutes =
            Array(12) { i -> String.format("%02d", i * 5) } // ["00", "05", "10", ..., "55"]
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
        val imm =
            requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
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
        // 1. 항상 보이도록 설정
        binding.tvStartTime.visibility = View.VISIBLE
        binding.tvEndTime.visibility = View.VISIBLE

        val highlightColor = Color.parseColor("#8BC34A")
        val defaultColor = Color.BLACK

        // 2. 현재 편집 중인 시간에만 초록색 하이라이트 적용
        if (isEditingStartTime) {
            // 시작 시간 편집 중
            binding.tvStartTime.setTextColor(highlightColor)
            binding.tvStartTime.setTypeface(null, Typeface.BOLD) // 가독성을 위해 볼드 처리 추천

            binding.tvEndTime.setTextColor(defaultColor)
            binding.tvEndTime.setTypeface(null, Typeface.NORMAL)
        } else {
            // 종료 시간 편집 중
            binding.tvStartTime.setTextColor(defaultColor)
            binding.tvStartTime.setTypeface(null, Typeface.NORMAL)

            binding.tvEndTime.setTextColor(highlightColor)
            binding.tvEndTime.setTypeface(null, Typeface.BOLD)
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

        // 1. 캘린더는 닫고 시간 선택 모드로 전환
        binding.calendarContainer.visibility = View.GONE

        // 2. 시간 설정 모드 초기화 (시작 시간부터 편집)
        isEditingStartTime = true
        updateTimeVisibility()
        showTimePicker() // 기존에 구현된 showTimePicker() 호출
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
            binding.btnStartDate.findViewById<TextView>(R.id.tv_start_date)
                .setTextColor(highlightColor)
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

    private fun setupCalendar() {
        val currentMonth = java.time.YearMonth.now() // 이번 달

        // [수정] 종료일을 오늘로부터 29일 뒤가 속한 달로 설정
        val maxDate = java.time.LocalDate.now().plusDays(29)
        val endMonth = java.time.YearMonth.from(maxDate)

        // 시작 요일 설정
        val firstDayOfWeek = java.time.DayOfWeek.SUNDAY

        // [수정] 시작월을 이번달(currentMonth)로, 종료월을 계산된 endMonth로 설정
        binding.calendarPicker.setup(currentMonth, endMonth, firstDayOfWeek)

        // 이번 달로 초기 스크롤
        binding.calendarPicker.scrollToMonth(currentMonth)

        // 스크롤 시 상단 텍스트(2026년 2월) 업데이트
        binding.calendarPicker.monthScrollListener = { month ->
            val titleFormatter = DateTimeFormatter.ofPattern("yyyy년 M월", Locale.KOREAN)
            binding.tvCurrentMonth.text = month.yearMonth.format(titleFormatter)
        }
    }
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

    // 사용자의 온보딩 값을 가져오기 위한 부분
    private fun observeUserSettings() {
        viewLifecycleOwner.lifecycleScope.launch {
            // filterNotNull().first()를 사용하여 진입 시 '단 한 번'만 설정을 긁어옵니다.
            val settings = viewModel.userSettings.filterNotNull().first()

            // 1. 일반 일정 알림 (수동 수정이 없을 때만)
            if (currentSelectedAlarms == null) {
                currentSelectedAlarms = settings.scheduleAlarms.toIntArray()
                updateAlarmText(currentSelectedAlarms!!)
            }

            // 2. 출발 알람 (수동 수정이 없을 때만)
            if (currentSelectedStartAlarms == null) {
                currentSelectedStartAlarms = settings.departureAlarms.toIntArray()
                updateDepartureAlarmText(currentSelectedStartAlarms!!)
                Log.d("ROUTE_INIT", "온보딩 값 로드: ${currentSelectedStartAlarms?.contentToString()}")
            }

            // 3. 캘린더 초기화
            if (currentSelectedCalendarId == null) {
                currentSelectedCalendarId = settings.calendarId
                val calendarName = viewModel.getCalendarNameById(settings.calendarId)
                binding.tvCalendarStatus.text = calendarName
                applyCalendarColor(settings.calendarId)
            }
        }
    }
    private fun animateLayoutChange() {
        // 뷰의 크기, 위치, 가시성 변화를 부드럽게 처리합니다.
        android.transition.TransitionManager.beginDelayedTransition(
            binding.root as ViewGroup,
            android.transition.AutoTransition().apply {
                duration = 200 // 애니메이션 속도 (0.2초)
            }
        )
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
            val colorPrimary300 =
                androidx.core.content.ContextCompat.getColor(requireContext(), R.color.primary_300)
            val verticalInset = (8 * resources.displayMetrics.density).toInt()

            when {
                // [CASE 1] 시작일과 종료일이 모두 선택되었고, 두 날짜가 서로 다를 때만 막대 표시
                startDate != null && endDate != null && startDate != endDate -> {
                    when (date) {
                        startDate -> {
                            textView.setBackgroundResource(R.drawable.drawable_circle_green)
                            textView.backgroundTintList = ColorStateList.valueOf(colorPrimary300)
                            val startBg = androidx.core.content.ContextCompat.getDrawable(
                                requireContext(),
                                R.drawable.bg_calendar_range_start
                            )
                            root.background = android.graphics.drawable.InsetDrawable(
                                startBg,
                                0,
                                verticalInset,
                                0,
                                verticalInset
                            )
                        }

                        endDate -> {
                            textView.setBackgroundResource(R.drawable.drawable_circle_green)
                            textView.backgroundTintList = ColorStateList.valueOf(colorPrimary300)
                            val endBg = androidx.core.content.ContextCompat.getDrawable(
                                requireContext(),
                                R.drawable.bg_calendar_range_end
                            )
                            root.background = android.graphics.drawable.InsetDrawable(
                                endBg,
                                0,
                                verticalInset,
                                0,
                                verticalInset
                            )
                        }

                        else -> {
                            if (date.isAfter(startDate) && date.isBefore(endDate)) {
                                val middleBg = androidx.core.content.ContextCompat.getDrawable(
                                    requireContext(),
                                    R.drawable.bg_calendar_range_middle
                                )
                                root.background = android.graphics.drawable.InsetDrawable(
                                    middleBg,
                                    0,
                                    verticalInset,
                                    0,
                                    verticalInset
                                )
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

    private fun updateAlarmText(minutesArray: IntArray) {
        if (minutesArray.isEmpty()) {
            binding.tvAlarmStatus.text = "알림 안함"
            return
        }
        val texts = minutesArray.sorted().map { minutesToText(it) }
        binding.tvAlarmStatus.text = texts.joinToString(", ")
        binding.tvAlarmStatus.setTextColor(Color.BLACK)
    }

    private fun updateDepartureAlarmText(minutesArray: IntArray) {
        if (minutesArray.isEmpty()) {
            binding.tvStartalarmStatus.text = "출발 알림 안함"
            binding.tvStartalarmStatus.setTextColor(requireContext().getColor(R.color.gray_500))
            return
        }
        // minutesToText 함수를 활용해 "10분 전, 30분 전" 형태로 변환
        val texts = minutesArray.sorted().map { minutesToText(it) }
        binding.tvStartalarmStatus.text = texts.joinToString(", ")
        binding.tvStartalarmStatus.setTextColor(Color.BLACK) // 선택되면 검정색으로 변경
    }

    private fun applyCalendarColor(calendarId: Long?) {
        if (calendarId == null || calendarId == -1L) return
        val color = viewModel.getCalendarColorById(calendarId) ?: return
        if (color == 0) return
        currentSelectedCalendarColor = color
        showColorDot(color)
    }

    private fun showColorDot(color: Int) {
        binding.viewColorDot.backgroundTintList = ColorStateList.valueOf(color)
        binding.viewColorDot.alpha = 1f
    }

    private fun getSaveColorInt(): Int {
        return currentSelectedCalendarColor ?: Color.parseColor(selectedColorHex)
    }

    private fun colorIntToHex(color: Int): String {
        return String.format("#%06X", 0xFFFFFF and color)
    }


    // [함수 추가] 기존 데이터 로드 및 UI 세팅
    private fun setupEditMode(id: Long) {
        // 1. 헤더 변경 (가장 먼저 실행)
        (activity as? AddScheduleActivity)?.let { act ->
            act.findViewById<TextView>(R.id.tv_toolbar_title)?.text = "일정 수정"
        }

        // 2. ViewModel에서 상세 데이터 요청
        viewModel.getScheduleDetail(id)

        // 3. ViewModel의 StateFlow 관찰 (Lifecycle 대응)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.scheduleDetailInfo.collect { detail ->
                    if (detail != null) {
                        // (1) 기본 정보 (ScheduleInfo) 및 색상/캘린더 복원 💡 핵심 추가
                        binding.etScheduleName.setText(detail.scheduleInfo.title)
                        binding.etMemo.setText(detail.scheduleInfo.memo)

                        // 서버 색상 반영
                        selectedColorHex = detail.scheduleInfo.color ?: selectedColorHex
                        selectedColor = selectedColorHex
                        val colorInt = Color.parseColor(selectedColorHex)
                        showColorDot(colorInt)

                        // 서버 캘린더 정보 반영
                        currentSelectedCalendarId = detail.scheduleInfo.calendarId?.toLongOrNull()
                        val calendarName = currentSelectedCalendarId?.let { id ->
                            viewModel.getCalendarNameById(id)
                        }
                        binding.tvCalendarStatus.text = calendarName ?: "내 일정"
                        binding.tvCalendarStatus.setTextColor(Color.BLACK)
                        applyCalendarColor(currentSelectedCalendarId)

                        // (2) 날짜 및 시간 정보
                        startDate = LocalDate.parse(detail.scheduleInfo.startDate)
                        endDate = LocalDate.parse(detail.scheduleInfo.endDate)
                        binding.tvStartTime.text = detail.scheduleInfo.startTime?.take(5) ?: "10:00"
                        binding.tvEndTime.text = detail.scheduleInfo.endTime?.take(5) ?: "11:00"
                        updateDateDisplay()

                        // (3) 경로 및 장소 정보 복원
                        detail.place?.let { p ->
                            lastDestName = p.targetName
                            lastDestLat = p.targetLat
                            lastDestLng = p.targetLng
                        }

                        detail.route?.let { r ->
                            lastStartName = r.originName
                            lastStartLat = r.originLat
                            lastStartLng = r.originLng

                            lastDestName = detail.place?.targetName ?: r.destName
                            lastDestLat = detail.place?.targetLat ?: r.destLat
                            lastDestLng = detail.place?.targetLng ?: r.destLng

                            val routeObj = RouteResponse(
                                totalDistance = r.totalDistance,
                                totalTime = r.totalTime,
                                departureTime = r.departureTime ?: "",
                                arrivalTime = r.arrivalTime ?: "",
                                routeDetails = r.routeDetails ?: emptyList()
                            )
                            lastRouteJson = Gson().toJson(routeObj)
                            updateRouteInfo(lastStartName!!, lastDestName!!, routeObj)
                            binding.deleteRouteIv.visibility = View.VISIBLE
                        }

                        // (4) 알림 정보 복원
                        currentSelectedAlarms = detail.reminders
                            ?.filter { it.reminderType == "EVENT" }
                            ?.map { it.minutesBefore }
                            ?.toIntArray()

                        currentSelectedStartAlarms = detail.reminders
                            ?.filter { it.reminderType == "DEPARTURE" }
                            ?.map { it.minutesBefore }
                            ?.toIntArray()

                        updateAlarmText(currentSelectedAlarms ?: intArrayOf())
                        updateDepartureAlarmText(currentSelectedStartAlarms ?: intArrayOf())

                        // (5) UI 상태 확정
                        binding.btnConfirm.text = "수정 완료"
                        binding.calendarPicker.notifyCalendarChanged()
                    }
                }
            }
        }
    }

    // 일반 일정으로 저장
    private fun saveAsNormalSchedule(){
        viewModel.createScheduleWithDefaultSettings(
            title = binding.etScheduleName.text.toString(),
            memo = binding.etMemo.text?.toString(),
            isAllDay = false,
            startDate = startDate.toString(),
            startTime = binding.tvStartTime.text.toString(),
            endDate = (endDate ?: startDate).toString(),
            endTime = binding.tvEndTime.text.toString(),
            place = null,
            placeId = null,
            customAlarms = currentSelectedAlarms?.toList(),
            calendarId = currentSelectedCalendarId,
            selectedColor = getSaveColorInt(),
            repeatInfo = null // 보정된 RepeatInfo 전달
        )
    }
}

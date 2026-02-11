package com.example.pace.ui.add_schedule

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
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
import com.example.pace.data.model.request.RouteRequest
import com.example.pace.data.model.response.RouteResponse
import com.example.pace.data.util.RouteConstants
import com.example.pace.databinding.FragmentRouteScheduleBinding
import com.example.pace.databinding.ItemRouteDetailBriefBinding
import com.example.pace.databinding.ItemRouteVehicleBinding
import com.example.pace.ui.WeightCalculator
import com.example.pace.ui.main.calendar.ScheduleViewModel
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RouteScheduleFragment : Fragment() {

    private var _binding: FragmentRouteScheduleBinding? = null
    private val binding get() = _binding!!

    // 경로 검색 -> 일정 추가 시 받는 ROUTE_DETAIL
    private var route: RouteResponse? = null

    private var isEditingStartTime: Boolean = true
    // 경로탐색으로 전환될 때 같이 보낼 색깔(선택된 일정 색)
    private var selectedColor: String = "#DC354B"

    // 경로 탐색에서 받아온 데이터
    private var routeJson: String? = null
    private var earlyArriveTime: Int = 0

    // [수정] 런처에서 받아온 경로 정보를 저장할 멤버 변수 선언
    private var lastDestName: String? = null
    private var lastDestLat: Double = 0.0
    private var lastDestLng: Double = 0.0
    private var lastRouteJson: String? = null
    private val viewModel: ScheduleViewModel by viewModels()
    private val routeSearchLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult

            val startName = data.getStringExtra("START_NAME")
            val startLat = data.getDoubleExtra("START_LAT", Double.NaN)
            val startLng = data.getDoubleExtra("START_LNG", Double.NaN)

            val endName = data.getStringExtra("END_NAME")
            val endLat = data.getDoubleExtra("END_LAT", Double.NaN)
            val endLng = data.getDoubleExtra("END_LNG", Double.NaN)

            val earlyArriveTime = data.getStringExtra("EARLY_ARRIVE_TIME")?.toIntOrNull()

            val routeDetailJson = data.getStringExtra("ROUTE_DETAIL")

            lastDestName = endName
            lastDestLat = endLat ?: 0.0
            lastDestLng = endLng ?: 0.0
            lastRouteJson = routeDetailJson

            Toast.makeText(
                context,
                """
            출발지: $startName
            출발좌표: $startLat , $startLng
            
            도착지: $endName
            도착좌표: $endLat , $endLng
            
            빠른 도착 시간: $earlyArriveTime
            
            ROUTE_DETAIL:
            $routeDetailJson
            """.trimIndent(),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentRouteScheduleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val startName = arguments?.getString("START_NAME") ?: "미지정"
        val endName = arguments?.getString("END_NAME") ?: "미지정"
        val earlyTime = arguments?.getInt("EARLY_ARRIVE_TIME", 0)
        val routeDetail = arguments?.getString("ROUTE_DETAIL") ?: "데이터 없음"

        // routeDetail 파싱해 경로 동적 바인딩 + route의 값에 따라 UI 업데이트
        val gson = Gson()
        route = gson.fromJson(routeDetail, RouteResponse::class.java)
        updateRouteInfo(startName, endName, route)
        if(route != null){
            // 삭제 버튼 활성화
            binding.deleteRouteIv.visibility = View.VISIBLE
            binding.deleteRouteIv.setOnClickListener {
                val dialog = DeleteRouteDialog(requireContext()) {
                    setRouteToNull()
                    Log.d("DEBUG_TAG", route.toString())
                    updateRouteInfo(startName, endName, route)
                }
                dialog.show()
            }
        }

        val toastMessage = """
     출발: $startName
     도착: $endName
     미리 도착: ${earlyTime}분
     경로 상세: ${if (routeDetail!!.length > 20) routeDetail!!.take(20) + "..." else routeDetail}
""".trimIndent()

        Toast.makeText(requireContext(), toastMessage, Toast.LENGTH_LONG).show()

        initTimePickers()

        updateTimeVisibility()


        binding.layoutScheduleName.setOnClickListener {
            binding.etScheduleName.requestFocus()

            val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(binding.etScheduleName, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }

        // 1. 결과 관찰 (성공 시 화면 닫기)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.createScheduleEvent.collect { isSuccess ->
                    when (isSuccess) {
                        true -> {
                            Toast.makeText(context, "일정이 저장되었습니다.", Toast.LENGTH_SHORT).show()
                            viewModel.resetCreateEvent() // 이벤트 초기화
                            requireActivity().finish()   // 화면 종료
                        }
                        false -> {
                            Toast.makeText(context, "일정 저장에 실패했습니다.", Toast.LENGTH_SHORT).show()
                            viewModel.resetCreateEvent()
                        }
                        null -> { /* 대기 상태 */ }
                    }
                }
            }
        }

        binding.btnConfirm.setOnClickListener {
            val scheduleName = binding.etScheduleName.text.toString().trim()
            val selectedDate = arguments?.getString("selected_date") ?: "2026-02-09"

            // 1. 유효성 검사
            if (scheduleName.isEmpty()) {
                Toast.makeText(context, "일정명을 입력해 주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val start = binding.tvStartTime.text.toString()
            val end = binding.tvEndTime.text.toString()
            if (isTimeAfter(start, end)) {
                Toast.makeText(context, "종료 시간이 시작 시간보다 빨라야 합니다.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedColorInt = try {
                android.graphics.Color.parseColor(selectedColor)
            } catch (e: Exception) {
                android.graphics.Color.parseColor("#DC354B") // 기본 레드
            }

            // 2. Request 객체 생성
            val request = CreateScheduleRequest(
                title = scheduleName,
                isAllDay = false,
                startDate = selectedDate,
                endDate = selectedDate,
                startTime = start, // HH:mm 형식 유지
                endTime = end,     // HH:mm 형식 유지
                memo = binding.etMemo.text.toString(),
                isPathIncluded = true,
                isRepeat = false,
                repeatInfo = null,
                place = PlaceRequest(
                    targetName = lastDestName ?: "미지정 장소",
                    targetLat = lastDestLat,
                    targetLng = lastDestLng
                ),
                reminders = listOf(
                    ReminderRequest(
                        "SCHEDULE", // "EVENT" 대신 인터페이스 규격에 맞게 "SCHEDULE" 사용 권장
                        mapAlarmTextToMinutes(binding.tvAlarmStatus.text.toString())
                    )
                ),
                route = parseRouteData(lastRouteJson) // 경로 데이터 주입
            )
            viewModel.createSchedule(
                request = request,
                placeId = null, // Route는 별도의 placeId를 쓰지 않거나 필요시 전달
                calendarId = null, // 기본 캘린더 사용 시 null
                selectedColor = selectedColorInt // 변환된 색상 전달
            )
            // TODO: 여기서 ViewModel.createSchedule(request) 호출
            android.util.Log.d("RouteSchedule", """
    [일정 데이터 추출 결과]
    제목: ${request.title}
    메모: ${request.memo}
    날짜: ${request.startDate} ~ ${request.endDate}
    시간: ${request.startTime} ~ ${request.endTime}
    도착지: ${request.place?.targetName} (Lat: ${request.place?.targetLat}, Lng: ${request.place?.targetLng})
    알림 설정: ${request.reminders.firstOrNull()?.minutesBefore}분 전
    경로 포함 여부: ${request.route != null}
""".trimIndent())
            Toast.makeText(context, "일정이 저장되었습니다.", Toast.LENGTH_SHORT).show()
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

        binding.btnRemindalarm.setOnClickListener {
            val alarmFragment = AlarmScheduleFragment()

            requireActivity().supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, alarmFragment)
                .addToBackStack(null)
                .commit()
        }

        binding.btnStartalarm.setOnClickListener {
            val startAlarmFragment = AlarmStartFragment()

            requireActivity().supportFragmentManager.beginTransaction()
                .replace(android.R.id.content,startAlarmFragment)
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


        setFragmentResultListener("startAlarmKey") { _, bundle ->
            val result = bundle.getString("selectedAlarm")
            result?.let {
                binding.tvStartalarmStatus.text = it
                binding.tvStartalarmStatus.setTextColor(Color.BLACK)
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

        updateTimeVisibility()

        // 일정 추가 -> 루트 프래그먼트로 데이터 전달
        binding.btnRoute.setOnClickListener {
            val scheduleName = binding.etScheduleName.text.toString()
            val startTime = binding.tvStartTime.text.toString()
            val intent = android.content.Intent(requireContext(), com.example.pace.ui.main.MainActivity::class.java).apply {
                putExtra("ACTION_MODE", "SCHEDULE_ROUTE")

                putExtra("SCHEDULE_NAME", scheduleName)
                putExtra("SCHEDULE_COLOR", selectedColor)
                putExtra("SCHEDULE_TIME", "12:21:11") // "hh:mm:ss”

                putExtra("SCHEDULE_DATE", "2026-02-12") // “yyyy-mm-dd”
                putExtra("EARLY_ARRIVE_TIME", earlyArriveTime)

                // ⭐ 좌표 & 장소명 같이 넘기기
                putExtra("START_NAME", "스타벅스 사당")
                putExtra("END_NAME", "강남역")

                putExtra("START_LAT", 37.33)
                putExtra("START_LNG", 126.84)

                putExtra("END_LAT", 37.56)
                putExtra("END_LNG", 126.99)

            }
            routeSearchLauncher.launch(intent)
        }

    }

    private fun isTimeAfter(t1: String, t2: String): Boolean {
        val s = t1.split(":").map { it.trim().toInt() }
        val e = t2.split(":").map { it.trim().toInt() }

        val sMin = s[0] * 60 + s[1]
        val eMin = e[0] * 60 + e[1]

        return sMin > eMin
    }

    private fun changeSelectedColor(colorStr: String) {
        val color = Color.parseColor(colorStr)
        selectedColor = colorStr
        binding.viewColorDot.backgroundTintList = ColorStateList.valueOf(color)
        binding.layoutColorSelector.visibility = View.GONE
    }

    private fun initTimePickers() {
        // (00 ~ 23)
        binding.pickerHour.apply {
            minValue = 0
            maxValue = 23

            setFormatter { String.format("%02d", it) }
            // 순환(23시 다음 00시)
            wrapSelectorWheel = true
        }

        // (00 ~ 59)
        binding.pickerMinute.apply {
            minValue = 0
            maxValue = 59
            setFormatter { String.format("%02d", it) }
            wrapSelectorWheel = true
        }

        val timeChangeListener = NumberPicker.OnValueChangeListener { _, _, _ ->
            val hour = String.format("%02d", binding.pickerHour.value)
            val minute = String.format("%02d", binding.pickerMinute.value)
            val formattedTime = "$hour:$minute"

            if (isEditingStartTime) {

                binding.tvStartTime.text = formattedTime
                binding.tvStartTime.setTextColor(Color.parseColor("#8BC34A"))
            } else {

                binding.tvEndTime.text = formattedTime
                binding.tvEndTime.setTextColor(Color.parseColor("#8BC34A"))
            }
        }

        binding.pickerHour.setOnValueChangedListener(timeChangeListener)
        binding.pickerMinute.setOnValueChangedListener(timeChangeListener)
    }


    private fun showCalendar() {
        binding.calendarPicker.visibility = View.VISIBLE
        binding.timePickerContainer.visibility = View.GONE
    }

    private fun showTimePicker() {
        binding.timePickerContainer.visibility = View.VISIBLE
        binding.calendarPicker.visibility = View.GONE

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

                binding.pickerHour.value = if (h in 0..23) h else 0
                binding.pickerMinute.value = if (m in 0..59) m else 0
            }
        } catch (e: Exception) {
            binding.pickerHour.value = 10
            binding.pickerMinute.value = 0
        }
    }

    private fun updateTimeVisibility() {
            binding.tvStartTime.visibility = View.VISIBLE
            binding.tvEndTime.visibility = View.VISIBLE

            if (isEditingStartTime) {
                binding.tvStartTime.setTextColor(Color.parseColor("#8BC34A"))
                binding.tvEndTime.setTextColor(Color.BLACK)
            } else {
                binding.tvStartTime.setTextColor(Color.BLACK)
                binding.tvEndTime.setTextColor(Color.parseColor("#8BC34A"))
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun setRouteToNull(){
        this.route = null
    }
    // 데이터 동적 바인딩
    private fun updateRouteInfo(startName: String, endName: String, route: RouteResponse?){
        if(route != null){
            // 경로 출발, 도착지 추가
            binding.routeIv.setColorFilter(R.color.black)
            binding.routeTv.text = startName + " -> " + endName
            binding.routeTv.setTextColor(requireContext().getColor(R.color.text_primary))
            // 일직선 경로 추가
            binding.routeInfoCl.visibility = View.VISIBLE
            binding.timeTv.text = route.departureTime.split("T").last().take(5) + " - " + route.arrivalTime.split("T").last().take(5)
            binding.totalTimeTv.text = if(route.totalTime / 3600L > 0 ){
                val time = route.totalTime % 3600L
                if(time / 60L > 0){
                    "${route.totalTime / 3600L}시간 ${time / 60L}분"
                }else{
                    "${route.totalTime / 3600L}시간"
                }
            }else{
                "${route.totalTime / 60L}분"
            }

            route.routeDetails.forEachIndexed { index, data ->
                val briefBinding = ItemRouteDetailBriefBinding.inflate(LayoutInflater.from(context), binding.routeBriefLl, false)

                // 3-1. 걷기 (TransitDetail이 null인 경우)
                if (data.transitDetail == null) {
                    if (data.sequence == 1) { // 첫 번째 순서면 사람 아이콘
                        briefBinding.itemRouteDetailBriefIv.setImageResource(R.drawable.ic_people)
                    } else {
                        briefBinding.itemRouteDetailBriefIv.visibility = View.GONE
                        briefBinding.itemRouteDetailBriefTv.updatePadding(0)
                    }
                    briefBinding.itemRouteDetailBriefTv.text = "${data.duration / 60}분"
                    briefBinding.itemRouteDetailBriefTv.setTextColor(ContextCompat.getColor(context, R.color.gray_600))

                    // 마지막 단계(하차) 처리
                    // 리스트의 마지막 인덱스인지 확인
                    if (index == route.routeDetails.size - 1) {
                        val vehicleBinding = ItemRouteVehicleBinding.inflate(LayoutInflater.from(context), binding.routeVehicleLl, false)
                        vehicleBinding.itemRouteVehicleIv.setImageResource(R.drawable.ic_route_item_arrival_icon)
                        vehicleBinding.itemRouteVehicleLineTv.text = "도착"
                        vehicleBinding.itemRouteVehicleLineTv.setTextColor(ContextCompat.getColor(context, R.color.black))
                        vehicleBinding.itemRouteVehicleView.visibility = View.GONE
                        vehicleBinding.itemRouteVehicleTv.text = endName

                        binding.routeVehicleLl.addView(vehicleBinding.root)
                    }
                }
                // 3-2. 대중교통 (버스, 지하철)
                else {
                    val vehicleBinding = ItemRouteVehicleBinding.inflate(LayoutInflater.from(context), binding.routeVehicleLl, false)

                    // 아이콘 및 색상 설정
                    val layoutDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.ic_route_detail)?.mutate() as LayerDrawable
                    val iconShape = layoutDrawable.findDrawableByLayerId(R.id.ic_route_detail_color).mutate() as GradientDrawable
                    val briefBg = briefBinding.itemRouteDetailBriefTv.background.mutate() as GradientDrawable

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
                            layoutDrawable.setDrawableByLayerId(R.id.ic_route_detail_vehicle, busDrawable)
                        }
                        "SUBWAY" -> {
                            val subwayDrawable = ContextCompat.getDrawable(context, R.drawable.ic_subway)
                            layoutDrawable.setDrawableByLayerId(R.id.ic_route_detail_vehicle, subwayDrawable)
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
                    vehicleBinding.itemRouteVehicleLineTv.text = data.transitDetail.lineName // shortName -> lineName (데이터 모델 확인 필요)
                    vehicleBinding.itemRouteVehicleLineTv.setTextColor(lineColorCode)
                    vehicleBinding.itemRouteVehicleTv.text = "${data.transitDetail.departureStop} 승차"

                    binding.routeVehicleLl.addView(vehicleBinding.root)
                }

                // 상단 바(Brief) 뷰 추가 (Weight 적용)
                val weight = WeightCalculator.forRouteDetailBrief(data.duration)
                val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
                binding.routeBriefLl.addView(briefBinding.root, params)
            }
        }else{
            Log.d("DEBUG_TAG", "route == null")
            // 기본 상태
            binding.deleteRouteIv.visibility = View.GONE
            binding.routeTv.text = "경로"
            binding.routeTv.setTextColor(requireContext().getColor(R.color.gray_500))
            binding.divider.visibility = View.GONE
            binding.routeInfoCl.visibility = View.GONE
        }
    }
}
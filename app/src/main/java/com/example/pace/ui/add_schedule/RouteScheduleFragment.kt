package com.example.pace.ui.add_schedule

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.NumberPicker
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.color.colorChooser
import com.example.pace.R
import com.example.pace.databinding.FragmentRouteScheduleBinding

class RouteScheduleFragment : Fragment() {

    private var _binding: FragmentRouteScheduleBinding? = null
    private val binding get() = _binding!!

    private var isEditingStartTime: Boolean = true
    // 경로탐색으로 전환될 때 같이 보낼 색깔(선택된 일정 색)
    private var selectedColor: String = "#DC354B"

    // 경로 탐색에서 받아온 데이터
    private var routeJson: String? = null
    private var earlyArriveTime: Int = 0
    private var sortOption: String = "최적 경로순"
    private val routeSearchLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult

            val startName = data.getStringExtra("startPlaceName")
            val startId = data.getStringExtra("startPlaceId")
            val endName = data.getStringExtra("endPlaceName")
            val endId = data.getStringExtra("endPlaceId")

            val routeJson = data.getStringExtra("routeData")
            val earlyTime = data.getIntExtra("earlyArriveTime", 10)
            val sortOpt = data.getStringExtra("sortOption") ?: "최적 경로순"

//            Toast.makeText(context, "출발 장소: $startName - $startId", Toast.LENGTH_SHORT).show()
            Toast.makeText(context, "정렬: $sortOpt", Toast.LENGTH_SHORT).show()
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

        initTimePickers()

        updateTimeVisibility()


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

                val start = binding.tvStartTime.text.toString()
                val end = binding.tvEndTime.text.toString()
                if (isTimeAfter(start, end)) {
                    Toast.makeText(context, "종료 시간이 시작 시간보다 빨라야 합니다.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
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

        binding.btnRoute.setOnClickListener {
            val scheduleName = binding.etScheduleName.text.toString()
            val startTime = binding.tvStartTime.text.toString()
            val intent = android.content.Intent(requireContext(), com.example.pace.ui.main.MainActivity::class.java).apply {
                putExtra("ACTION_MODE", "SCHEDULE_ROUTE")

                putExtra("SCHEDULE_NAME", scheduleName)
                putExtra("SCHEDULE_COLOR", selectedColor)
                putExtra("SCHEDULE_TIME", startTime)
                //여기부터 저장되어 있는 값으로 수정 필요
                putExtra("SEARCH_TIME", "") // 년도까지 반영된  구글 Directions API는 Unix Timestamp 형식(String)
                putExtra("EARLY_ARRIVE_TIME", 10) // 디폴트는 온보딩값으로 넣어주세여
                putExtra("SORT_OPTION", "최소 시간순") // "최적 경로순", "최소 시간순", "최소 환승순", "최소 도보순"
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
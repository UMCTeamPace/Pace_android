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
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.color.colorChooser
import com.example.pace.R
import com.example.pace.databinding.FragmentGeneralScheduleBinding

class GeneralScheduleFragment : Fragment() {

    private var _binding: FragmentGeneralScheduleBinding? = null
    private val binding get() = _binding!!

    private var isEditingStartTime: Boolean = true

    private var isAllDay = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setFragmentResultListener("repeatKey") { _, bundle ->
            val result = bundle.getString("selectedRepeat")
            binding.tvRepeatStatus.text = result // 레이아웃의 텍스트 변경
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

        initTimePickers()

        updateTimeVisibility()

        // 일정명 레이아웃 클릭 시 EditText에 포커스 주기
        // (XML에서 해당 레이아웃에 id를 @+id/layout_schedule_name으로 설정했다고 가정합니다)
        binding.layoutScheduleName.setOnClickListener {
            binding.etScheduleName.requestFocus() // EditText로 포커스 이동

            // 키보드 강제로 올리기
            val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(binding.etScheduleName, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }

        binding.btnConfirm.setOnClickListener {
            val scheduleName = binding.etScheduleName.text.toString().trim()

            // 이름 확인
            if (scheduleName.isEmpty()) {
                Toast.makeText(context, "일정명을 입력해 주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 시간 유효성 확인
            if (!isAllDay) {
                val start = binding.tvStartTime.text.toString()
                val end = binding.tvEndTime.text.toString()
                if (isTimeAfter(start, end)) {
                    Toast.makeText(context, "종료 시간이 시작 시간보다 빨라야 합니다.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            // 모든 검증 통과 시 저장 후 종료
            Toast.makeText(context, "일정이 저장되었습니다.", Toast.LENGTH_SHORT).show()
            // 1. 뒤로가기 스택이 있다면 뒤로가기
            if (parentFragmentManager.backStackEntryCount > 0) {
                parentFragmentManager.popBackStack()
            } else {
                // 2. 스택이 없다면 현재 Fragment를 호스팅하는 Activity를 종료하여 메인으로 복귀
                requireActivity().finish()
            }
        }

        // 2. 취소 버튼 로직
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

        // 3. 키보드 상태에 따른 버튼 레이아웃 제어
        setupKeyboardVisibilityListener()

        setFragmentResultListener("repeatKey") { _, bundle ->
            val result = bundle.getString("selectedRepeat")
            binding.tvRepeatStatus.text = result // 화면에 반영
        }


        binding.btnRepeat.setOnClickListener {
            val repeatFragment = ScheduleRepeatFragment()

            requireActivity().supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, repeatFragment)
                .addToBackStack(null) // 뒤로가기 시 GeneralScheduleFragment로 복귀
                .commit()
        }

        binding.btnStartDate.setOnClickListener { showCalendar() }
        binding.tvStartTime.setOnClickListener {
            isEditingStartTime = true
            showTimePicker()
        }


        binding.btnEndDate.setOnClickListener { showCalendar() }
        binding.tvEndTime.setOnClickListener {
            isEditingStartTime = false
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

        binding.addscheMyPhoneIv.setOnClickListener {
            isAllDay = !isAllDay

            if (isAllDay) {
                binding.addscheMyPhoneIv.setImageResource(R.drawable.ic_toggle_selected)
            } else {
                binding.addscheMyPhoneIv.setImageResource(R.drawable.ic_toggle_unselected) // OFF 이미지 필요
            }

            updateTimeVisibility()
        }

    }

    private fun changeSelectedColor(colorStr: String) {
        val color = Color.parseColor(colorStr)
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
            val hour = binding.pickerHour.value
            val minute = binding.pickerMinute.value
            val formattedTime = String.format("%02d:%02d", hour, minute)

            if (isEditingStartTime) {
                binding.tvStartTime.text = formattedTime
                binding.tvStartTime.setTextColor(Color.parseColor("#8BC34A"))

                // [검증] 시작 시간이 종료 시간보다 늦어졌는지 확인
                val endTime = binding.tvEndTime.text.toString()
                if (isTimeAfter(formattedTime, endTime)) {
                    // 시작 시간이 더 늦다면 종료 시간을 시작 시간 + 1시간 등으로 자동 조정
                    val newEndHour = if (hour < 23) hour + 1 else 23
                    val newEndTime = String.format("%02d:%02d", newEndHour, minute)
                    binding.tvEndTime.text = newEndTime
                }
            } else {
                // [검증] 종료 시간을 수정할 때 시작 시간보다 앞서는지 확인
                val startTime = binding.tvStartTime.text.toString()
                if (isTimeAfter(startTime, formattedTime)) {
                    // 종료 시간이 더 빠르면 토스트 메시지를 띄우거나 수정을 막음
                    // 여기서는 일단 텍스트는 바꾸되, 색상을 빨간색으로 표시해 경고를 줄 수 있습니다.
                    binding.tvEndTime.text = formattedTime
                    binding.tvEndTime.setTextColor(Color.RED)
                } else {
                    binding.tvEndTime.text = formattedTime
                    binding.tvEndTime.setTextColor(Color.parseColor("#8BC34A"))
                }
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
        if (isAllDay) {
            // 하루 종일 ON: 시간 숨김
            binding.tvStartTime.visibility = View.GONE
            binding.tvEndTime.visibility = View.GONE
        } else {
            // 하루 종일 OFF: 시간 표시
            binding.tvStartTime.visibility = View.VISIBLE
            binding.tvEndTime.visibility = View.VISIBLE

            // 현재 수정 중인 시간 텍스트만 강조 (연두색)
            if (isEditingStartTime) {
                binding.tvStartTime.setTextColor(Color.parseColor("#8BC34A"))
                binding.tvEndTime.setTextColor(Color.BLACK)
            } else {
                binding.tvStartTime.setTextColor(Color.BLACK)
                binding.tvEndTime.setTextColor(Color.parseColor("#8BC34A"))
            }
        }
    }

    private fun setupKeyboardVisibilityListener() {
        val rootView = binding.root
        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            val rect = android.graphics.Rect()
            rootView.getWindowVisibleDisplayFrame(rect)

            // 전체 화면 높이와 현재 보이는 화면 높이의 차이를 계산
            val screenHeight = rootView.rootView.height
            val keypadHeight = screenHeight - rect.bottom

            // 차이가 200dp 이상이면 키보드가 올라온 것으로 판단
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
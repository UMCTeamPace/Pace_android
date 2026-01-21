package com.example.pace.ui.add_schedule

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.NumberPicker
import androidx.fragment.app.Fragment
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.color.colorChooser
import com.example.pace.databinding.FragmentRouteScheduleBinding// 바인딩 클래스 임포트

class RouteScheduleFragment : Fragment() {

    // 1. 메모리 누수 방지를 위한 바인딩 객체 선언
    private var _binding: FragmentRouteScheduleBinding? = null
    private val binding get() = _binding!!

    private var isEditingStartTime: Boolean = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // fragment_route_schedule.xml 레이아웃 연결
        _binding = FragmentRouteScheduleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 초기 설정: NumberPicker 범위 지정 등 (필수)
        initTimePickers()

        // 1. 시작 날짜/시간 클릭 리스너
        binding.btnStartDate.setOnClickListener { showCalendar() }
        binding.tvStartTime.setOnClickListener {
            isEditingStartTime = true // 시작 시간 수정 모드
            showTimePicker()
        }

        // 2. 종료 날짜/시간 클릭 리스너 (추가)
        binding.btnEndDate.setOnClickListener { showCalendar() }
        binding.tvEndTime.setOnClickListener {
            isEditingStartTime = false // 종료 시간 수정 모드
            showTimePicker()
        }

        // 3. 컬러 피커(view_color_dot) 클릭 리스너 설정
        binding.viewColorDot.setOnClickListener {
            openColorPicker()
        }

    }

    private fun openColorPicker() {
        val colors = intArrayOf(
            Color.parseColor("#F44336"), Color.parseColor("#E91E63"),
            Color.parseColor("#FF9800"), Color.parseColor("#4CAF50"),
            Color.parseColor("#2196F3"), Color.parseColor("#9C27B0")
        )

        MaterialDialog(requireContext()).show {
            title(text = "색상 선택")
            colorChooser(colors) { _, color ->
                // 선택한 색상을 view_color_dot에 즉시 적용
                binding.viewColorDot.backgroundTintList = ColorStateList.valueOf(color)
            }
            positiveButton(text = "확인")
        }
    }

    private fun initTimePickers() {
        // 시간 설정 (00 ~ 23)
        binding.pickerHour.apply {
            minValue = 0
            maxValue = 23

            setFormatter { String.format("%02d", it) }
            // 순환 모드 (23시 다음 00시)
            wrapSelectorWheel = true
        }

        // 분 설정 (00 ~ 59)
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
                // 시작 시간 업데이트
                binding.tvStartTime.text = formattedTime
                binding.tvStartTime.setTextColor(Color.parseColor("#8BC34A"))
            } else {
                // 종료 시간 업데이트 (추가)
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

        // 현재 텍스트 뷰에 있는 시간 정보를 안전하게 가져오기
        val timeText = if (isEditingStartTime) {
            binding.tvStartTime.text.toString()
        } else {
            binding.tvEndTime.text.toString()
        }

        try {
            // "10:00" 형태를 ":" 기준으로 분리
            val parts = timeText.split(":")
            if (parts.size == 2) {
                val h = parts[0].trim().toInt()
                val m = parts[1].trim().toInt()

                // NumberPicker 범위(0~23, 0~59)를 벗어나지 않는지 체크 후 세팅
                binding.pickerHour.value = if (h in 0..23) h else 0
                binding.pickerMinute.value = if (m in 0..59) m else 0
            }
        } catch (e: Exception) {
            // 형식 에러가 나면 기본값(현재 시간 등)으로 세팅하여 튕김 방지
            binding.pickerHour.value = 10
            binding.pickerMinute.value = 0
        }
    }

    // 4. 프래그먼트 파괴 시 바인딩 해제
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
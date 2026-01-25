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
import androidx.fragment.app.setFragmentResultListener
import androidx.recyclerview.widget.LinearLayoutManager
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.color.colorChooser
import com.example.pace.R
import com.example.pace.databinding.FragmentRouteScheduleBinding

class RouteScheduleFragment : Fragment() {

    private var _binding: FragmentRouteScheduleBinding? = null
    private val binding get() = _binding!!

    private var isEditingStartTime: Boolean = true

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

        binding.btnRemindalarm.setOnClickListener {
            val alarmFragment = AlarmScheduleFragment()

            // android.R.id.content를 사용하여 화면 전체를 덮어씌웁니다.
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, alarmFragment)
                .addToBackStack(null) // 뒤로가기 시 다시 RouteScheduleFragment로 복귀
                .commit()
        }

        setFragmentResultListener("alarmKey") { _, bundle ->
            val result = bundle.getString("selectedAlarm")

            result?.let {
                // 1. 텍스트 내용 변경 (예: "일정 알림 10분 전")
                binding.tvAlarmStatus.text = "일정 알림 $it"

                // 2. 텍스트 색상을 검정색으로 변경
                binding.tvAlarmStatus.setTextColor(Color.BLACK)
            }
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
            openColorPicker()
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
        }


    }

    private fun changeSelectedColor(colorStr: String) {
        val color = Color.parseColor(colorStr)
        binding.viewColorDot.backgroundTintList = ColorStateList.valueOf(color)
        binding.layoutColorSelector.visibility = View.GONE
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



    // 4. 프래그먼트 파괴 시 바인딩 해제
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
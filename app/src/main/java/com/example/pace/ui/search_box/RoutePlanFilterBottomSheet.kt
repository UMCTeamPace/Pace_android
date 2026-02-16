package com.example.pace.ui.search_box

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.pace.databinding.DialogRoutePlanFilterBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.text.SimpleDateFormat
import java.util.*

class RoutePlanFilterBottomSheet(
    private val initialCalendar: Calendar,
    private val initialMode: Int,
    private val onSave: (Calendar, Int) -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: DialogRoutePlanFilterBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogRoutePlanFilterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupTabLayout()
        setupPickers()

        // 취소 버튼
        binding.btnCancelRoutePlanFilter.setOnClickListener {
            dismiss()
        }

        // 저장 버튼
        binding.btnSaveRoutePlanFilter.setOnClickListener {
            val resultCalendar = Calendar.getInstance().apply {
                // 날짜 더하기
                add(Calendar.DAY_OF_YEAR, binding.npDateRoutePlanFilter.value)
                // 선택된 시/분 설정
                set(Calendar.HOUR_OF_DAY, binding.npHourRoutePlanFilter.value)
                set(Calendar.MINUTE, binding.npMinuteRoutePlanFilter.value)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            if (resultCalendar.before(Calendar.getInstance())) {
                // 토스트 메시지를 띄우거나 현재 시간으로 강제 설정
                onSave(Calendar.getInstance(), binding.tlTimeModeRoutePlanFilter.selectedTabPosition)
            } else {
                onSave(resultCalendar, binding.tlTimeModeRoutePlanFilter.selectedTabPosition)
            }
            dismiss()
        }
    }

    private fun setupTabLayout() {
        val tab = binding.tlTimeModeRoutePlanFilter.getTabAt(initialMode)
        tab?.select()
    }

    private fun setupPickers() {
        val now = Calendar.getInstance()
        val dateStrings = mutableListOf<String>()
        val sdf = SimpleDateFormat("M월 d일(E)", Locale.KOREAN)

        for (i in 0..29) {
            when (i) {
                0 -> dateStrings.add("오늘")
                1 -> dateStrings.add("내일")
                else -> {
                    val cal = Calendar.getInstance()
                    cal.add(Calendar.DAY_OF_YEAR, i)
                    dateStrings.add(sdf.format(cal.time))
                }
            }
        }

        val initialDiff = ((initialCalendar.timeInMillis - now.timeInMillis) / (24 * 60 * 60 * 1000)).toInt().coerceIn(0, 29)

        binding.npDateRoutePlanFilter.apply {
            minValue = 0
            maxValue = dateStrings.size - 1
            displayedValues = dateStrings.toTypedArray()
            wrapSelectorWheel = false
            value = initialDiff

            setOnValueChangedListener { _, _, _ ->
                handlePastTimeSelection() // 날짜 바뀌면 체크
            }
        }

        binding.npHourRoutePlanFilter.apply {
            minValue = 0
            maxValue = 23
            wrapSelectorWheel = true // 숫자가 다 보이도록 설정
            setOnValueChangedListener { _, _, _ ->
                handlePastTimeSelection() // 시간 바뀌면 체크
            }
        }

        binding.npMinuteRoutePlanFilter.apply {
            minValue = 0
            maxValue = 59
            setFormatter { value -> String.format("%02d", value) }
            wrapSelectorWheel = true
            setOnValueChangedListener { _, _, _ ->
                handlePastTimeSelection() // 분 바뀌면 체크
            }
        }

        // 첫 진입 시 제한 적용
        handlePastTimeSelection()
    }

    private fun handlePastTimeSelection() {
        val now = Calendar.getInstance()
        val selectedDateIdx = binding.npDateRoutePlanFilter.value
        val isToday = selectedDateIdx == 0

        if (isToday) {
            val currentHour = now.get(Calendar.HOUR_OF_DAY)
            val currentMinute = now.get(Calendar.MINUTE)

            // 1. 시간 체크: 선택한 시간이 현재 시보다 작으면 현재 시로 튕기기
            if (binding.npHourRoutePlanFilter.value < currentHour) {
                binding.npHourRoutePlanFilter.value = currentHour
            }

            // 2. 분 체크: 같은 시간대인데 선택한 분이 현재 분보다 작으면 현재 분으로 튕기기
            if (binding.npHourRoutePlanFilter.value == currentHour) {
                if (binding.npMinuteRoutePlanFilter.value < currentMinute) {
                    binding.npMinuteRoutePlanFilter.value = currentMinute
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
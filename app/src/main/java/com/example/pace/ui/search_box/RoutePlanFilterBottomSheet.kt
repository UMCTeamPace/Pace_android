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
            val resultCalendar = Calendar.getInstance()
            resultCalendar.add(Calendar.DAY_OF_YEAR, binding.npDateRoutePlanFilter.value)
            resultCalendar.set(Calendar.HOUR_OF_DAY, binding.npHourRoutePlanFilter.value)
            resultCalendar.set(Calendar.MINUTE, binding.npMinuteRoutePlanFilter.value)
            resultCalendar.set(Calendar.SECOND, 0)

            val selectedMode = binding.tlTimeModeRoutePlanFilter.selectedTabPosition
            onSave(resultCalendar, selectedMode)
            dismiss()
        }
    }

    private fun setupTabLayout() {
        val tab = binding.tlTimeModeRoutePlanFilter.getTabAt(initialMode)
        tab?.select()
    }

    private fun setupPickers() {
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

        binding.npDateRoutePlanFilter.apply {
            minValue = 0
            maxValue = dateStrings.size - 1
            displayedValues = dateStrings.toTypedArray()
            wrapSelectorWheel = false
            value = 0
        }

        binding.npHourRoutePlanFilter.apply {
            minValue = 0
            maxValue = 23
            value = initialCalendar.get(Calendar.HOUR_OF_DAY)
        }

        binding.npMinuteRoutePlanFilter.apply {
            displayedValues = null
            minValue = 0
            maxValue = 59
            value = initialCalendar.get(Calendar.MINUTE)
            setFormatter { value -> String.format("%02d", value) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
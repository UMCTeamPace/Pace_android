package com.example.pace.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import com.example.pace.databinding.FragmentSettingDepartureBinding

class SettingDepartureFragment : Fragment() {
    private var _binding: FragmentSettingDepartureBinding? = null
    private val binding get() = _binding!!

    private var selectedAlarmList = mutableListOf<Int>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingDepartureBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. 기존 데이터 복원
        val initialAlarms = arguments?.getIntegerArrayList("currentAlarms") ?: arrayListOf()
        selectedAlarmList = initialAlarms.toMutableList()

        // 2. 체크박스 설정
        setupCheckBoxes()
    }

    private fun setupCheckBoxes() {
        // XML ID에 맞춰 매핑 (분 단위)
        val alarmMap = mapOf(
            binding.rbNone to -1,
            binding.rbStart5mago to 5,
            binding.rbStart10mago to 10,
            binding.rbStart15mago to 15,
            binding.rbStart20mago to 20,
            binding.rbStart25mago to 25,
            binding.rbStart30mago to 30,
            binding.rbStart35mago to 35,
            binding.rbStart40mago to 40,
            binding.rbStart45mago to 45,
            binding.rbStart50mago to 50,
            binding.rbStart55mago to 55,
            binding.rbStart1hago to 60
        )

        alarmMap.forEach { (checkBox, minutes) ->
            if (minutes == -1) {
                checkBox.isChecked = selectedAlarmList.isEmpty()
            } else {
                checkBox.isChecked = selectedAlarmList.contains(minutes)
            }

            checkBox.setOnClickListener {
                handleAlarmClick(checkBox, minutes)
            }
        }
    }

    private fun handleAlarmClick(checkBox: CheckBox, minutes: Int) {
        if (minutes == -1) {
            // '안함' 클릭 시
            selectedAlarmList.clear()
            uncheckAllExcept(checkBox)
            checkBox.isChecked = true
        } else {
            // 시간 선택 시 '안함' 체크 해제
            binding.rbNone.isChecked = false

            if (checkBox.isChecked) {
                // 5개 제한 체크
                if (selectedAlarmList.size >= 5) {
                    checkBox.isChecked = false
                    Toast.makeText(requireContext(), "알림은 최대 5개까지 설정 가능합니다.", Toast.LENGTH_SHORT).show()
                    return
                }
                if (!selectedAlarmList.contains(minutes)) {
                    selectedAlarmList.add(minutes)
                }
            } else {
                selectedAlarmList.remove(minutes)
                if (selectedAlarmList.isEmpty()) {
                    binding.rbNone.isChecked = true
                }
            }
        }

        selectedAlarmList.sort()
        sendResultToParent()
    }

    private fun uncheckAllExcept(exception: CheckBox) {
        val allBoxes = listOf(
            binding.rbNone, binding.rbStart5mago, binding.rbStart10mago,
            binding.rbStart15mago, binding.rbStart20mago, binding.rbStart25mago,
            binding.rbStart30mago, binding.rbStart35mago, binding.rbStart40mago,
            binding.rbStart45mago, binding.rbStart50mago, binding.rbStart55mago,
            binding.rbStart1hago
        )
        allBoxes.forEach { if (it != exception) it.isChecked = false }
    }

    private fun sendResultToParent() {
        setFragmentResult("departureAlarmKey", bundleOf("selectedAlarms" to ArrayList(selectedAlarmList)))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
package com.example.pace.ui.settings

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import com.example.pace.R
import com.example.pace.databinding.FragmentSettingReminderBinding

class SettingReminderFragment : Fragment() {
    private var _binding: FragmentSettingReminderBinding? = null
    private val binding get() = _binding!!

    // 현재 선택된 알림(분 단위)을 담는 리스트
    private var selectedAlarmList = mutableListOf<Int>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingReminderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val initialAlarms = arguments?.getIntegerArrayList("currentAlarms") ?: arrayListOf()
        selectedAlarmList = initialAlarms.toMutableList()

        // 체크박스 초기 세팅 및 리스너 연결
        setupCheckBoxes()
    }

    private fun setupCheckBoxes() {
        // 체크박스와 매핑될 분(minute) 값 정의
        val alarmMap = mapOf(
            binding.rbNone to -1, // 안함
            binding.rbStarttime to 0,
            binding.rbStart5mago to 5,
            binding.rbStart10mago to 10,
            binding.rbStart15mago to 15,
            binding.rbStart30mago to 30,
            binding.rbStart1hago to 60,
            binding.rbStart2hago to 120,
            binding.rbStart1dago to 1440,
            binding.rbStart2dago to 2880,
            binding.rbStart1wago to 10080
        )

        // 초기 상태 반영: DB에 있는 값이면 체크 표시
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
            // '안함' 클릭 시: 모든 선택 해제
            selectedAlarmList.clear()
            uncheckAllExcept(checkBox)
            checkBox.isChecked = true
        } else {
            // 일반 알람 클릭 시
            binding.rbNone.isChecked = false // '안함' 해제

            if (checkBox.isChecked) {
                // 체크하려는 경우: 5개 제한 확인
                if (selectedAlarmList.size >= 5) {
                    checkBox.isChecked = false // 체크 취소
                    updateGuideText()
                    return
                }
                if (!selectedAlarmList.contains(minutes)) {
                    selectedAlarmList.add(minutes)
                }
            } else {
                // 체크 해제하려는 경우
                selectedAlarmList.remove(minutes)
                // 만약 아무것도 선택 안된 상태면 다시 '안함'에 체크
                if (selectedAlarmList.isEmpty()) {
                    binding.rbNone.isChecked = true
                }
            }
        }

        // 데이터 전달 및 정렬
        selectedAlarmList.sort()
        updateGuideText()
        sendResultToParent()
    }

    private fun updateGuideText() {
        if (selectedAlarmList.size >= 5) {
            // 5개일 때 빨간색으로 강조
            binding.tvAlarmDescription.setTextColor(Color.RED)
        } else {
            binding.tvAlarmDescription.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.text_secondary)
            )
        }
    }

    private fun uncheckAllExcept(exception: CheckBox) {
        val allCheckBoxes = listOf(
            binding.rbNone, binding.rbStarttime, binding.rbStart5mago,
            binding.rbStart10mago, binding.rbStart15mago, binding.rbStart30mago,
            binding.rbStart1hago, binding.rbStart2hago, binding.rbStart1dago,
            binding.rbStart2dago, binding.rbStart1wago
        )
        allCheckBoxes.forEach { if (it != exception) it.isChecked = false }
    }

    private fun sendResultToParent() {
        setFragmentResult("scheduleAlarmKey", bundleOf("selectedAlarms" to ArrayList(selectedAlarmList)))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
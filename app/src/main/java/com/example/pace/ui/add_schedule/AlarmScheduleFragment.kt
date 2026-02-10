package com.example.pace.ui.add_schedule

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import com.example.pace.R
import com.example.pace.databinding.FragmentAlarmScheduleBinding

class AlarmScheduleFragment : Fragment() {

    private var _binding: FragmentAlarmScheduleBinding? = null
    private val binding get() = _binding!!

    private val selectedOptions = mutableSetOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAlarmScheduleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.alarmScheduleToolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                parentFragmentManager.popBackStack()
            }
        })

        // "안함"을 제외한 알림 옵션 리스트
        val alarmOptions = listOf(
            binding.rbStarttime, binding.rbStart5mago, binding.rbStart10mago,
            binding.rbStart15mago, binding.rbStart30mago, binding.rbStart1hago,
            binding.rbStart2hago, binding.rbStart1dago, binding.rbStart2dago, binding.rbStart1wago
        )

        // 1. 알림 옵션들 클릭 리스너
        alarmOptions.forEach { checkBox ->
            checkBox.setOnClickListener {
                val isChecked = checkBox.isChecked
                val text = checkBox.text.toString()

                if (isChecked) {
                    // 5개 제한 체크
                    if (selectedOptions.size >= 5) {
                        checkBox.isChecked = false
                        return@setOnClickListener
                    }
                    // 알림 옵션을 선택하면 "안함"은 해제
                    binding.rbNone.isChecked = false
                    selectedOptions.add(text)
                } else {
                    selectedOptions.remove(text)
                }
                updateUIAndResult()
            }
        }

        // 2. "안함" 버튼 클릭 리스너
        binding.rbNone.setOnClickListener {
            if (binding.rbNone.isChecked) {
                // "안함" 체크 시 모든 옵션 해제
                alarmOptions.forEach { it.isChecked = false }
                selectedOptions.clear()
            }
            updateUIAndResult()
        }
    }

    private fun updateUIAndResult() {
        // 5개 꽉 찼을 때 설명 텍스트 색상 변경
        if (selectedOptions.size >= 5) {
            binding.tvAlarmDescription.setTextColor(Color.RED)
        } else {
            binding.tvAlarmDescription.setTextColor(Color.parseColor("#666666"))
        }

        // 결과 전달
        val resultText = if (binding.rbNone.isChecked || selectedOptions.isEmpty()) {
            "일정 알림 안함"
        } else {
            selectedOptions.joinToString(", ")
        }

        val bundle = Bundle().apply { putString("selectedAlarm", resultText) }
        parentFragmentManager.setFragmentResult("scheduleAlarmKey", bundle)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
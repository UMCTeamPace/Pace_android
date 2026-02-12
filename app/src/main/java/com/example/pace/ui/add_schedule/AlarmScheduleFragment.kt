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

        // 1. 번들 데이터 수신 (Key: "selectedAlarmMinutes")
        // RouteScheduleFragment에서 넘겨준 이름과 일치해야 합니다.
        val initialAlarms = arguments?.getIntArray("selectedAlarmMinutes")?.toList() ?: emptyList()
        if (initialAlarms.isNotEmpty()) {
            binding.rbNone.isChecked = false
            initialAlarms.forEach { minutes ->
                val targetText = minutesToText(minutes)

                // 💡 trim()을 추가하여 공백 차이를 방지합니다.
                val foundCheckBox = alarmOptions.find {
                    it.text.toString().trim() == targetText.trim()
                }

                if (foundCheckBox != null) {
                    foundCheckBox.isChecked = true
                    // 💡 체크박스에 실제로 적힌 텍스트를 담아야 나중에 textToMinutes가 인식합니다.
                    selectedOptions.add(foundCheckBox.text.toString())
                } else {
                    // 여기에 로그를 찍어보세요. targetText가 체크박스 텍스트와 왜 다른지 알 수 있습니다.
                    android.util.Log.e("ALARM_CHECK", "매칭 실패: $targetText")
                }
            }
            updateUIAndResult()
        }else {
            binding.rbNone.isChecked = true
        }

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

        binding.alarmScheduleToolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

    }

    private fun updateUIAndResult() {
        val requestKey = arguments?.getString("requestKey") ?: "scheduleAlarmKey" // 기본값 유지
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

        // 💡 선택된 텍스트들을 다시 숫자로 변환
        val selectedMinutes = selectedOptions.map { textToMinutes(it) }.toIntArray()

        val bundle = Bundle().apply {
            putString("selectedAlarm", resultText)
            putIntArray("selectedAlarmMinutes", selectedMinutes) // 💡 숫자 데이터 추가!
        }
        parentFragmentManager.setFragmentResult(requestKey, bundle) // 💡 받은 키로 전달!
    }


    private fun textToMinutes(text: String): Int {
        return when (text) {
            "일정 시작 시간", "정시" -> 0 // XML 텍스트가 "일정 시작 시간"이므로 추가
            "5분 전" -> 5
            "10분 전" -> 10
            "15분 전" -> 15
            "30분 전" -> 30
            "1시간 전" -> 60
            "2시간 전" -> 120
            "1일 전" -> 1440
            "2일 전" -> 2880
            "1주일 전" -> 10080
            else -> 0
        }
    }

    private fun minutesToText(minutes: Int): String {
        return when (minutes) {
            0 -> "일정 시작 시간" // XML 텍스트와 일치시킴
            5 -> "5분 전"
            10 -> "10분 전"
            15 -> "15분 전"
            30 -> "30분 전"
            60 -> "1시간 전"
            120 -> "2시간 전"
            1440 -> "1일 전"
            2880 -> "2일 전"
            10080 -> "1주일 전"
            else -> "${minutes}분 전"
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
package com.example.pace.ui.add_schedule

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import android.graphics.Color
import androidx.fragment.app.Fragment
import com.example.pace.R
import com.example.pace.databinding.FragmentAlarmStartBinding

class AlarmStartFragment : Fragment() {

    private var _binding: FragmentAlarmStartBinding? = null
    private val binding get() = _binding!!
    private val selectedOptions = mutableSetOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAlarmStartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. 체크박스 리스트 초기화
        val checkBoxes = listOf(
            binding.rbStarttime,
            binding.rbStart5mago, binding.rbStart10mago, binding.rbStart15mago,
            binding.rbStart20mago, binding.rbStart25mago, binding.rbStart30mago,
            binding.rbStart35mago, binding.rbStart40mago, binding.rbStart45mago,
            binding.rbStart50mago, binding.rbStart55mago, binding.rbStart1hago
        )

        // 2. 부모로부터 받은 초기 데이터 세팅 (리스너 등록 전 수행)
        val initialAlarms = arguments?.getIntArray("selectedAlarmMinutes")?.toList() ?: emptyList()
        android.util.Log.d("ALARM_DEBUG", "전달받은 숫자들: $initialAlarms")

        selectedOptions.clear() // 진입 시점에 딱 한 번만 비우기

        if (initialAlarms.isNotEmpty()) {
            binding.rbNone.isChecked = false
            initialAlarms.forEach { minutes ->
                var isMatched = false
                checkBoxes.forEach { checkBox ->
                    val cbMinutes = textToMinutes(checkBox.text.toString())
                    if (cbMinutes == minutes) {
                        checkBox.isChecked = true
                        selectedOptions.add(checkBox.text.toString())
                        isMatched = true
                    }
                }
                if (!isMatched) {
                    android.util.Log.e("ALARM_DEBUG", "매칭 실패한 숫자: $minutes")
                }
            }
        } else {
            binding.rbNone.isChecked = true
        }
        updateUIOnly()
        // 3. 개별 체크박스 리스너 등록
        checkBoxes.forEach { checkBox ->
            checkBox.setOnClickListener {
                val text = checkBox.text.toString()
                if (checkBox.isChecked) {
                    if (selectedOptions.size >= 5) {
                        checkBox.isChecked = false
                        return@setOnClickListener
                    }
                    binding.rbNone.isChecked = false
                    selectedOptions.add(text)
                } else {
                    selectedOptions.remove(text)
                    // 💡 아무것도 선택 안된 경우 '안함'에 체크
                    if (selectedOptions.isEmpty()) binding.rbNone.isChecked = true
                }
                sendResultToParent()
            }
        }

// 4. "안함" 버튼 리스너
        binding.rbNone.setOnClickListener {
            if (binding.rbNone.isChecked) {
                checkBoxes.forEach { it.isChecked = false }
                selectedOptions.clear()
            } else {
                // '안함'을 다시 눌러서 해제하려고 할 때 방어 로직 (최소 하나는 선택되게 하거나 유지)
                if (selectedOptions.isEmpty()) binding.rbNone.isChecked = true
            }
            sendResultToParent()
        }

        // 5. 나가기/뒤로가기 설정 (나갈 때 최종 상태 확정)
        binding.alarmStartToolbar.setNavigationOnClickListener {
            sendResultToParent()
            parentFragmentManager.popBackStack()
        }


        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                sendResultToParent()
                parentFragmentManager.popBackStack()
            }
        })

        updateUIOnly()
    }

    private fun updateUIOnly() {
        binding.tvAlarmDescription.setTextColor(
            if (selectedOptions.size >= 5) Color.RED else Color.parseColor("#666666")
        )
        // 로그만 찍어보고 전송은 하지 않음
        android.util.Log.d("ALARM_INIT_CHECK", "현재 선택된 옵션들: $selectedOptions")
    }

    // 2. 부모에게 결과를 전송하는 함수 (클릭 시 & 나갈 때용)
    private fun sendResultToParent() {
        val requestKey = arguments?.getString("requestKey") ?: "startAlarmKey"
        val resultText = if (binding.rbNone.isChecked || selectedOptions.isEmpty()) {
            "출발 알림 안함"
        } else {
            selectedOptions.map { textToMinutes(it) }.sorted().map { minutesToText(it) }.joinToString(", ")
        }
        val selectedMinutes = selectedOptions.map { textToMinutes(it) }.toIntArray()

        val bundle = Bundle().apply {
            putString("selectedAlarm", resultText)
            putIntArray("selectedAlarmMinutes", selectedMinutes)
        }
        parentFragmentManager.setFragmentResult(requestKey, bundle)
        android.util.Log.d("ALARM_SEND", "최종 전송: $resultText")
    }


    // 💡 텍스트 <-> 분 변환 함수 (출발 알림용)
    private fun textToMinutes(text: String): Int {
        return when {
            text.contains("1시간") -> 60
            else -> {
                // "출발 5분 전" 또는 "5분 전"에서 숫자만 추출하는 가장 안전한 방법
                text.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0
            }
        }
    }

    private fun minutesToText(minutes: Int): String {
        return when (minutes) {
            60 -> "출발 1시간 전"
            else -> "출발 ${minutes}분 전"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
package com.example.pace.ui.add_schedule

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import com.example.pace.R
import com.example.pace.databinding.FragmentAlarmScheduleBinding

class AlarmScheduleFragment : Fragment() {

    private var _binding: FragmentAlarmScheduleBinding? = null
    private val binding get() = _binding!!

    private val selectedOptions = mutableSetOf<String>()

    private lateinit var alarmOptionPairs: List<Pair<LinearLayout, CheckBox>>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAlarmScheduleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. XML에서 추가한 ID를 바탕으로 레이아웃-체크박스 페어 구성
        alarmOptionPairs = listOf(
            binding.layoutStarttime to binding.cbStarttime,
            binding.layoutStart5mago to binding.cbStart5mago,
            binding.layoutStart10mago to binding.cbStart10mago,
            binding.layoutStart15mago to binding.cbStart15mago,
            binding.layoutStart30mago to binding.cbStart30mago,
            binding.layoutStart1hago to binding.cbStart1hago,
            binding.layoutStart2hago to binding.cbStart2hago,
            binding.layoutStart1dago to binding.cbStart1dago,
            binding.layoutStart2dago to binding.cbStart2dago,
            binding.layoutStart1wago to binding.cbStart1wago
        )

        // 툴바 뒤로가기 설정
        binding.alarmScheduleToolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                parentFragmentManager.popBackStack()
            }
        })

        // 2. 초기 데이터 수신 및 체크 상태 설정
        val initialAlarms = arguments?.getIntArray("selectedAlarmMinutes")?.toList() ?: emptyList()
        if (initialAlarms.isNotEmpty()) {
            binding.cbNone.isChecked = false
            initialAlarms.forEach { minutes ->
                val targetText = minutesToText(minutes)

                // 해당 텍스트를 가진 체크박스 찾아서 체크
                alarmOptionPairs.find { (parent, _) ->
                    // 레이아웃의 첫 번째 자식인 TextView의 텍스트 확인
                    val textView = parent.getChildAt(0) as? android.widget.TextView
                    textView?.text.toString().trim() == targetText.trim()
                }?.let { (_, cb) ->
                    cb.isChecked = true
                    selectedOptions.add(targetText)
                }
            }
            updateUIAndResult()
        } else {
            binding.cbNone.isChecked = true
        }

        // 3. 각 알림 옵션 레이아웃 클릭 리스너 설정
        alarmOptionPairs.forEach { (layout, checkBox) ->
            layout.setOnClickListener {
                val nextState = !checkBox.isChecked
                val textView = layout.getChildAt(0) as android.widget.TextView
                val text = textView.text.toString()

                if (nextState) {
                    // 5개 제한 로직
                    if (selectedOptions.size >= 5) {
                        return@setOnClickListener
                    }
                    binding.cbNone.isChecked = false // 옵션 선택 시 '안함' 해제
                    selectedOptions.add(text)
                } else {
                    selectedOptions.remove(text)
                }

                checkBox.isChecked = nextState
                updateUIAndResult()
            }
        }

        // 4. "안함" 레이아웃 클릭 리스너 설정
        binding.layoutNone.setOnClickListener {
            if (!binding.cbNone.isChecked) {
                binding.cbNone.isChecked = true
                // 모든 다른 알림 옵션 해제
                alarmOptionPairs.forEach { (_, cb) -> cb.isChecked = false }
                selectedOptions.clear()
                updateUIAndResult()
            }
        }
    }

    private fun updateUIAndResult() {
        val requestKey = arguments?.getString("requestKey") ?: "scheduleAlarmKey"

        // 5개 꽉 찼을 때 안내 문구 강조
        if (selectedOptions.size >= 5) {
            binding.tvAlarmDescription.setTextColor(Color.RED)
        } else {
            binding.tvAlarmDescription.setTextColor(resources.getColor(R.color.text_secondary))
        }

        // 결과 텍스트 생성
        val resultText = if (binding.cbNone.isChecked || selectedOptions.isEmpty()) {
            "일정 알림 안함"
        } else {
            selectedOptions.joinToString(", ")
        }

        // 선택된 텍스트를 숫자로 변환하여 결과 전달
        val selectedMinutes = selectedOptions.map { textToMinutes(it) }.toIntArray()

        val bundle = Bundle().apply {
            putString("selectedAlarm", resultText)
            putIntArray("selectedAlarmMinutes", selectedMinutes)
        }
        parentFragmentManager.setFragmentResult(requestKey, bundle)
    }

    private fun textToMinutes(text: String): Int {
        return when (text.trim()) {
            "일정 시작 시간" -> 0
            "5분 전" -> 5
            "10분 전" -> 10
            "15분 전" -> 15
            "30분 전" -> 30
            "1시간 전" -> 60
            "2시간 전" -> 120
            "1일 전" -> 1440
            "2일 전" -> 2880
            "1주 전" -> 10080
            else -> 0
        }
    }

    private fun minutesToText(minutes: Int): String {
        return when (minutes) {
            0 -> "일정 시작 시간"
            5 -> "5분 전"
            10 -> "10분 전"
            15 -> "15분 전"
            30 -> "30분 전"
            60 -> "1시간 전"
            120 -> "2시간 전"
            1440 -> "1일 전"
            2880 -> "2일 전"
            10080 -> "1주 전"
            else -> "${minutes}분 전"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
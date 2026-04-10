package com.example.pace.ui.add_schedule

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import android.graphics.Color
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.pace.R
import com.example.pace.databinding.FragmentAlarmStartBinding

class AlarmStartFragment : Fragment() {

    private var _binding: FragmentAlarmStartBinding? = null
    private val binding get() = _binding!!
    private val selectedOptions = mutableSetOf<String>()

    // 레이아웃과 체크박스를 묶은 리스트
    private lateinit var alarmOptionPairs: List<Pair<LinearLayout, CheckBox>>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAlarmStartBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. XML ID에 맞춰 레이아웃-체크박스 페어 리스트 초기화
        alarmOptionPairs = listOf(
            binding.layoutStarttime to binding.cbStarttime,
            binding.layoutStart5mago to binding.cbStart5mago,
            binding.layoutStart10mago to binding.cbStart10mago,
            binding.layoutStart15mago to binding.cbStart15mago,
            binding.layoutStart20mago to binding.cbStart20mago,
            binding.layoutStart25mago to binding.cbStart25mago,
            binding.layoutStart30mago to binding.cbStart30mago,
            binding.layoutStart35mago to binding.cbStart35mago,
            binding.layoutStart40mago to binding.cbStart40mago,
            binding.layoutStart45mago to binding.cbStart45mago,
            binding.layoutStart50mago to binding.cbStart50mago,
            binding.layoutStart55mago to binding.cbStart55mago,
            binding.layoutStart1hago to binding.cbStart1hago
        )

        // 2. 초기 데이터 수신 및 체크 상태 설정
        val initialAlarms = arguments?.getIntArray("selectedAlarmMinutes")?.toList() ?: emptyList()
        selectedOptions.clear()

        if (initialAlarms.isNotEmpty()) {
            binding.cbNone.isChecked = false
            initialAlarms.forEach { minutes ->
                val targetText = minutesToText(minutes)

                alarmOptionPairs.find { (layout, _) ->
                    val textView = layout.getChildAt(0) as? TextView
                    textView?.text.toString().trim() == targetText.trim()
                }?.let { (_, cb) ->
                    cb.isChecked = true
                    selectedOptions.add(targetText)
                }
            }
        } else {
            binding.cbNone.isChecked = true
        }
        updateUIOnly()

        // 3. 개별 옵션 레이아웃 클릭 리스너 설정
        alarmOptionPairs.forEach { (layout, checkBox) ->
            layout.setOnClickListener {
                val nextState = !checkBox.isChecked
                val textView = layout.getChildAt(0) as TextView
                val text = textView.text.toString()

                if (nextState) {
                    if (selectedOptions.size >= 5) return@setOnClickListener
                    binding.cbNone.isChecked = false
                    selectedOptions.add(text)
                } else {
                    selectedOptions.remove(text)
                    if (selectedOptions.isEmpty()) binding.cbNone.isChecked = true
                }
                checkBox.isChecked = nextState
                sendResultToParent()
                updateUIOnly()
            }
        }

        // 4. "안함" 레이아웃 클릭 리스너
        binding.layoutNone.setOnClickListener {
            if (!binding.cbNone.isChecked) {
                binding.cbNone.isChecked = true
                alarmOptionPairs.forEach { (_, cb) -> cb.isChecked = false }
                selectedOptions.clear()
                sendResultToParent()
                updateUIOnly()
            }
        }

        // 5. 툴바 및 뒤로가기 설정
        binding.alarmStartToolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                parentFragmentManager.popBackStack()
            }
        })
    }

    private fun updateUIOnly() {
        binding.tvAlarmDescription.setTextColor(
            if (selectedOptions.size >= 5) Color.RED else resources.getColor(R.color.text_secondary)
        )
    }

    private fun sendResultToParent() {
        val requestKey = arguments?.getString("requestKey") ?: "startAlarmKey"
        val resultText = if (binding.cbNone.isChecked || selectedOptions.isEmpty()) {
            "출발 알림 안함"
        } else {
            // 정렬해서 깔끔하게 보여주기
            selectedOptions.map { textToMinutes(it) }.sorted().map { minutesToText(it) }.joinToString(", ")
        }
        val selectedMinutes = selectedOptions.map { textToMinutes(it) }.toIntArray()

        val bundle = Bundle().apply {
            putString("selectedAlarm", resultText)
            putIntArray("selectedAlarmMinutes", selectedMinutes)
        }
        parentFragmentManager.setFragmentResult(requestKey, bundle)
    }

    private fun textToMinutes(text: String): Int {
        val cleanText = text.trim()
        return when {
            cleanText == "출발시각" -> 0
            cleanText.contains("1시간") -> 60
            else -> cleanText.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0
        }
    }

    private fun minutesToText(minutes: Int): String {
        return when (minutes) {
            0 -> "출발시각"
            60 -> "출발 1시간 전"
            else -> "출발 ${minutes}분 전"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
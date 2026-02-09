package com.example.pace.ui.onboarding

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.pace.R
import com.example.pace.databinding.FragmentScheduleAlarmBinding


class ScheduleAlarmFragment : Fragment() {
    private var _binding: FragmentScheduleAlarmBinding? = null
    private val binding get() = _binding!!

    // 체크박스 리스트 관리
    private lateinit var checkBoxes: List<CheckBox>

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentScheduleAlarmBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. 체크박스들을 리스트로 묶어서 관리
        checkBoxes = listOf(
            binding.rbStarttime, binding.rbStart5mago, binding.rbStart10mago,
            binding.rbStart15mago, binding.rbStart30mago, binding.rbStart1hago, binding.rbStart2hago
        )

        setupCheckBoxLogic()

        // 2. 다음 버튼 클릭 리스너
        binding.btnNext.setOnClickListener {
            saveSelectedAlarms()
            navigateToNextPage()
        }

        binding.tvDescription.setBoldText(
            "일정 알림을 언제 보내 드릴까요?",
            listOf("일정 알림")
        )
    }

    private fun setupCheckBoxLogic() {
        // "안함" 체크박스 로직
        binding.rbNone.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // "안함"을 누르면 나머지 모든 체크박스 해제
                checkBoxes.forEach { it.isChecked = false }
            }
        }

        // 나머지 체크박스들 로직 (최대 5개 제한)
        checkBoxes.forEach { checkBox ->
            checkBox.setOnClickListener {
                val selectedCount = checkBoxes.count { it.isChecked }

                if (checkBox.isChecked) {
                    // "안함"은 해제
                    binding.rbNone.isChecked = false

                    // 5개 초과 시 체크 방지
                    if (selectedCount > 5) {
                        checkBox.isChecked = false
                        Toast.makeText(context, "알림은 최대 5개까지 설정 가능합니다.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun saveSelectedAlarms() {
        val selectedAlarms = mutableSetOf<String>()

        if (binding.rbNone.isChecked) {
            selectedAlarms.add("NONE")
        } else {
            checkBoxes.filter { it.isChecked }.forEach {
                selectedAlarms.add(it.text.toString())
            }
        }

        // SharedPreferences에 Set 형태로 저장
        val sharedPref = requireActivity().getSharedPreferences("PaceSettings", Context.MODE_PRIVATE)
        sharedPref.edit().putStringSet("schedule_alarm_list", selectedAlarms).apply()
    }

    private fun navigateToNextPage() {
        val viewPager = activity?.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.app_setting_viewpager)
        viewPager?.let { it.currentItem = it.currentItem + 1 }
    }

    fun TextView.setBoldText(fullText: String, boldKeywords: List<String>) {
        val spannable = SpannableStringBuilder(fullText)

        boldKeywords.forEach { keyword ->
            val start = fullText.indexOf(keyword)
            if (start != -1) {
                spannable.setSpan(
                    StyleSpan(Typeface.BOLD),
                    start,
                    start + keyword.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        this.text = spannable
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
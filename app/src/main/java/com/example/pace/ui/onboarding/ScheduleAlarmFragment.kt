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
import androidx.fragment.app.activityViewModels
import androidx.viewpager2.widget.ViewPager2
import com.example.pace.R
import com.example.pace.data.viewmodel.OnboardingViewModel
import com.example.pace.databinding.FragmentScheduleAlarmBinding


class ScheduleAlarmFragment : Fragment() {
    private var _binding: FragmentScheduleAlarmBinding? = null
    private val binding get() = _binding!!

    private val viewModel: OnboardingViewModel by activityViewModels()
    private lateinit var alarmMap: Map<CheckBox, Int>
    // 체크박스 리스트 관리
    private lateinit var checkBoxes: List<CheckBox>

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentScheduleAlarmBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. 체크박스와 정수 값 매핑 초기화
        alarmMap = mapOf(
            binding.rbStarttime to 0,
            binding.rbStart5mago to 5,
            binding.rbStart10mago to 10,
            binding.rbStart15mago to 15,
            binding.rbStart30mago to 30,
            binding.rbStart1hago to 60,
            binding.rbStart2hago to 120
        )

        setupCheckBoxLogic()

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
        // "안함" 클릭 시 나머지 모두 해제
        binding.rbNone.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                alarmMap.keys.forEach { it.isChecked = false }
            }
        }

        // 개별 알림 클릭 시 로직
        alarmMap.keys.forEach { checkBox ->
            checkBox.setOnClickListener {
                if (checkBox.isChecked) {
                    binding.rbNone.isChecked = false // "안함" 해제

                    // 최대 5개 제한
                    val selectedCount = alarmMap.keys.count { it.isChecked }
                    if (selectedCount > 5) {
                        checkBox.isChecked = false
                        Toast.makeText(context, "알림은 최대 5개까지 설정 가능합니다.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun saveSelectedAlarms() {
        val selectedMinutes = mutableListOf<Int>()

        if (binding.rbNone.isChecked) {
            // "안함" 선택 시 빈 리스트 혹은 특정 처리 (현재 ViewModel 구조상 빈 리스트 전달)
            viewModel.isReminderActive = false // 필요 시 Reminder 비활성화
        } else {
            viewModel.isReminderActive = true
            // 체크된 항목의 '분' 값만 추출하여 리스트 생성
            alarmMap.forEach { (checkBox, minutes) ->
                if (checkBox.isChecked) {
                    selectedMinutes.add(minutes)
                }
            }
        }

        // 2. 뷰모델의 scheduleAlarms(MutableList<Int>) 교체
        viewModel.scheduleAlarms.clear()
        viewModel.scheduleAlarms.addAll(selectedMinutes)
    }

    private fun navigateToNextPage() {
        val viewPager = activity?.findViewById<ViewPager2>(R.id.app_setting_viewpager)
        viewPager?.let { it.currentItem = it.currentItem + 1 }
    }
    fun TextView.setBoldText(fullText: String, boldKeywords: List<String>) {
        val spannable = SpannableStringBuilder(fullText)
        boldKeywords.forEach { keyword ->
            val start = fullText.indexOf(keyword)
            if (start != -1) {
                spannable.setSpan(StyleSpan(Typeface.BOLD), start, start + keyword.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        this.text = spannable
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
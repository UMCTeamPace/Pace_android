package com.example.pace.ui.onboarding

import android.content.Context
import android.graphics.Color
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
import com.example.pace.databinding.FragmentDepartAlarmBinding

class DepartAlarmFragment : Fragment() {
    private var _binding: FragmentDepartAlarmBinding? = null
    private val binding get() = _binding!!

    // 공유 뷰모델 주입 (Activity 범위)
    private val viewModel: OnboardingViewModel by activityViewModels()

    // 체크박스 ID와 실제 '분(Minute)' 값 매핑
    private lateinit var departAlarmMap: Map<CheckBox, Int>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDepartAlarmBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. 체크박스와 정수 값 매핑 (출발 알림용)
        departAlarmMap = mapOf(
            binding.rbDepartureTime to 0,
            binding.rbStart5mago to 5,
            binding.rbStart10mago to 10,
            binding.rbStart15mago to 15,
            binding.rbStart20mago to 20,
            binding.rbStart25mago to 25,
            binding.rbStart30mago to 30
        )

        resetDescription()
        setupCheckBoxLogic()

        binding.btnNext.setOnClickListener {
            // 검증 통과 시에만 이동
            if (validateSelection()) {
                saveDepartAlarms()
                navigateToNextPage()
            }
        }

        binding.tvDescription.setBoldText(
            "출발 알림을 언제 보내 드릴까요?",
            listOf("출발 알림")
        )
    }

    private fun setupCheckBoxLogic() {
        // "안함" 클릭 시 나머지 모두 해제
        binding.rbNone.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                departAlarmMap.keys.forEach { it.isChecked = false }
                resetDescription()
            }
        }

        // 개별 알림 클릭 시 로직
        departAlarmMap.keys.forEach { checkBox ->
            checkBox.setOnClickListener {
                val selectedCount = departAlarmMap.keys.count { it.isChecked }

                if (checkBox.isChecked && selectedCount > 5) {
                    checkBox.isChecked = false

                    binding.tvAlarmDescription.text = "*알림은 최대 5개까지만 선택 가능합니다."
                    binding.tvAlarmDescription.setTextColor(Color.RED)

                } else {
                    if (checkBox.isChecked) {
                        binding.rbNone.isChecked = false
                    }
                    resetDescription()
                }
            }
        }
    }

    private fun updateDescriptionBasedOnSelection() {
        val selectedCount = departAlarmMap.keys.count { it.isChecked }
        if (selectedCount > 5) {
            binding.tvAlarmDescription.text = "*알림은 총 5개 까지 설정할 수 있습니다."
            binding.tvAlarmDescription.setTextColor(Color.RED)
        } else {
            resetDescription()
        }
    }

    private fun resetDescription() {
        binding.tvAlarmDescription.text = "*알림은 총 5개 까지 설정할 수 있습니다."
        binding.tvAlarmDescription.setTextColor(Color.GRAY)
    }

    private fun validateSelection(): Boolean {
        val selectedCount = departAlarmMap.keys.count { it.isChecked }
        val isNoneChecked = binding.rbNone.isChecked

        if (selectedCount == 0 && !isNoneChecked) {
            binding.tvAlarmDescription.text = "*최소 1개 이상 선택해야 합니다."
            binding.tvAlarmDescription.setTextColor(Color.RED)
            return false
        }

        if (selectedCount > 5) {
            binding.tvAlarmDescription.text = "*알림은 총 5개 까지 설정할 수 있습니다."
            binding.tvAlarmDescription.setTextColor(Color.RED)
            return false
        }

        return true
    }
    private fun saveDepartAlarms() {
        val selectedMinutes = mutableListOf<Int>()
        if (!binding.rbNone.isChecked) {
            departAlarmMap.forEach { (checkBox, minutes) ->
                if (checkBox.isChecked) selectedMinutes.add(minutes)
            }
        }
        viewModel.departureAlarms.clear()
        viewModel.departureAlarms.addAll(selectedMinutes)
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
package com.example.pace.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.pace.databinding.FragmentCalendarSelectBinding
import com.example.pace.ui.main.MainActivity
import androidx.fragment.app.activityViewModels // 추가
import com.example.pace.data.viewmodel.OnboardingViewModel
import dagger.hilt.android.AndroidEntryPoint // 추가


@AndroidEntryPoint // 1. Hilt 사용을 위해 추가
class CalendarSelectFragment : Fragment() {
    private var _binding: FragmentCalendarSelectBinding? = null
    private val binding get() = _binding!!

    // 2. Activity 범위의 뷰모델 공유 (온보딩의 모든 데이터를 들고 있음)
    private val viewModel: OnboardingViewModel by activityViewModels()

    private lateinit var calendarCheckBoxes: List<CheckBox>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalendarSelectBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        calendarCheckBoxes = listOf(
            binding.rbMyphone, binding.rbSamsungac,
            binding.rbGoogleac, binding.rbGoogleac2
        )

        setupSingleSelectionLogic()

        // 3. 버튼 클릭 시 통합 저장 로직 실행
        binding.btnStart.setOnClickListener {
            handleCompleteOnboarding()
        }

        binding.tvDescription.setBoldText(
            "어떤 캘린더에 일정을 담아 드릴까요?",
            listOf("어떤 캘린더")
        )
    }

    private fun handleCompleteOnboarding() {
        // A. 선택된 캘린더 타입을 뷰모델 변수에 직접 할당
        val selectedCalendar = when {
            binding.rbGoogleac.isChecked || binding.rbGoogleac2.isChecked -> "GOOGLE"
            binding.rbSamsungac.isChecked -> "SAMSUNG"
            else -> "LOCAL"
        }

        // 에러 해결: 함수 대신 변수에 직접 저장합니다.
        viewModel.calendarType = selectedCalendar

        // B. 최종 저장 로직 실행
        viewModel.completeOnboarding()

        // C. 메인 화면으로 이동
        moveToMainActivity()
    }
    private fun setupSingleSelectionLogic() {
        calendarCheckBoxes.forEach { checkBox ->
            checkBox.setOnClickListener {
                if (checkBox.isChecked) {
                    // 하나를 선택하면 나머지는 모두 해제 (단일 선택 구현)
                    calendarCheckBoxes.forEach { other ->
                        if (other != checkBox) other.isChecked = false
                    }
                }
            }
        }
    }

    private fun saveSelectedCalendar() {
        // 선택된 캘린더의 텍스트 저장
        val selected = calendarCheckBoxes.find { it.isChecked }?.text?.toString() ?: "내 휴대전화"

        val sharedPref = requireActivity().getSharedPreferences("PaceSettings", Context.MODE_PRIVATE)
        sharedPref.edit().putString("default_calendar", selected).apply()
    }

    private fun moveToMainActivity() {
        val intent = Intent(requireContext(), MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
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
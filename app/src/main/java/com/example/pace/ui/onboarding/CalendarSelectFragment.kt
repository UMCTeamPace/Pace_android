package com.example.pace.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.pace.databinding.FragmentCalendarSelectBinding
import com.example.pace.ui.main.MainActivity

class CalendarSelectFragment : Fragment() {
    private var _binding: FragmentCalendarSelectBinding? = null
    private val binding get() = _binding!!

    // 체크박스 단일 선택 관리를 위한 리스트
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

        // 1. 체크박스 리스트 초기화
        calendarCheckBoxes = listOf(
            binding.rbMyphone, binding.rbSamsungac,
            binding.rbGoogleac, binding.rbGoogleac2
        )

        setupSingleSelectionLogic()

        // 2. Pace 시작하기 버튼 클릭 리스너
        binding.btnStart.setOnClickListener {
            saveSelectedCalendar()
            moveToMainActivity()
        }
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
        // 메인 액티비티로 이동하며 이전 스택 모두 제거
        val intent = Intent(requireContext(), MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)

        // 현재 온보딩 액티비티 종료
        requireActivity().finish()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
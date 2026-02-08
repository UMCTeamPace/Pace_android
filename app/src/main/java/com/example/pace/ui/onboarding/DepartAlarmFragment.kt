package com.example.pace.ui.onboarding

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.example.pace.R
import com.example.pace.databinding.FragmentDepartAlarmBinding

class DepartAlarmFragment : Fragment() {
    private var _binding: FragmentDepartAlarmBinding? = null
    private val binding get() = _binding!!

    // 체크박스 관리를 위한 리스트
    private lateinit var departCheckBoxes: List<CheckBox>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDepartAlarmBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. 체크박스들을 리스트로 초기화 (rb_none 제외)
        departCheckBoxes = listOf(
            binding.rbStart5mago, binding.rbStart10mago, binding.rbStart15mago,
            binding.rbStart20mago, binding.rbStart25mago, binding.rbStart30mago
        )

        setupCheckBoxLogic()

        // 2. 다음 버튼 클릭 시 저장 및 페이지 이동
        binding.btnNext.setOnClickListener {
            saveDepartAlarms()
            navigateToNextPage()
        }
    }

    private fun setupCheckBoxLogic() {
        // "안함" 체크박스 클릭 시 나머지 해제
        binding.rbNone.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                departCheckBoxes.forEach { it.isChecked = false }
            }
        }

        // 개별 알림 체크박스 로직
        departCheckBoxes.forEach { checkBox ->
            checkBox.setOnClickListener {
                if (checkBox.isChecked) {
                    // 개별 옵션 선택 시 "안함" 해제
                    binding.rbNone.isChecked = false

                    // 최대 5개 제한 체크
                    val selectedCount = departCheckBoxes.count { it.isChecked }
                    if (selectedCount > 5) {
                        checkBox.isChecked = false
                        Toast.makeText(context, "알림은 최대 5개까지 설정 가능합니다.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun saveDepartAlarms() {
        val selectedSet = mutableSetOf<String>()

        if (binding.rbNone.isChecked) {
            selectedSet.add("NONE")
        } else {
            departCheckBoxes.filter { it.isChecked }.forEach {
                selectedSet.add(it.text.toString())
            }
        }

        // SharedPreferences에 저장
        val sharedPref = requireActivity().getSharedPreferences("PaceSettings", Context.MODE_PRIVATE)
        sharedPref.edit().putStringSet("depart_alarm_list", selectedSet).apply()
    }

    private fun navigateToNextPage() {
        val viewPager = activity?.findViewById<ViewPager2>(R.id.app_setting_viewpager)
        viewPager?.let {
            it.currentItem = it.currentItem + 1
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
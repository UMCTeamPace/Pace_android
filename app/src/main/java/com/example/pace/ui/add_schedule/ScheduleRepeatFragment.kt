package com.example.pace.ui.add_schedule

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import android.text.Editable
import android.text.TextWatcher
import androidx.fragment.app.setFragmentResult
import android.content.Context
import android.view.inputmethod.InputMethodManager
import com.example.pace.R
import com.example.pace.databinding.FragmentScheduleRepeatBinding

class ScheduleRepeatFragment : Fragment() {

    private var _binding: FragmentScheduleRepeatBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScheduleRepeatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 툴바의 뒤로가기 버튼을 누를 때 현재 선택된 값을 전달하고 나감
        binding.repeatToolbar.setNavigationOnClickListener {
            sendResultAndBack() // 결과 전달 함수 호출
        }

        binding.etInterval.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                // 현재 선택된 라디오 버튼이 무엇인지 파악
                val type = when (binding.rgRepeatOptions.checkedRadioButtonId) {
                    R.id.rb_daily -> "일간"
                    R.id.rb_week -> "주간"
                    R.id.rb_month -> "월간"
                    R.id.rb_year -> "연간"
                    else -> "안함"
                }
                // 입력된 숫자와 함께 문구 업데이트
                updateRepeatDescription(type, s.toString())
            }
        })

        fun setupRepeatLogic() {
            // 1. 라디오 그룹(안함, 일간, 주간 등) 선택 리스너
            binding.rgRepeatOptions.setOnCheckedChangeListener { _, checkedId ->
                when (checkedId) {
                    binding.rbWeek.id, binding.rbMonth.id, binding.rbYear.id -> {
                        // 주간, 월간, 연간 선택 시 요일 레이아웃 보이기
                        binding.layoutDayOfWeek.visibility = View.VISIBLE
                    }
                    else -> {
                        // 안함, 일간 선택 시 요일 레이아웃 숨기기
                        binding.layoutDayOfWeek.visibility = View.GONE
                    }
                }
                updateRepeatText() // 문구 업데이트
            }

            // 2. 모든 요일 체크박스에 리스너 등록
            val dayCheckBoxes = listOf(
                binding.cbSun, binding.cbMon, binding.cbTue,
                binding.cbWed, binding.cbThu, binding.cbFri, binding.cbSat
            )
            dayCheckBoxes.forEach { cb ->
                cb.setOnCheckedChangeListener { _, _ -> updateRepeatText() }
            }
        }

        binding.rgRepeatOptions.setOnCheckedChangeListener { _, checkedId ->
            val currentInterval = binding.etInterval.text.toString()

            binding.layoutRepeatInterval.visibility = View.VISIBLE
            binding.layoutDayOfWeek.visibility = View.GONE
            binding.layoutRepeatEnd.visibility = View.GONE

            when (checkedId) {
                R.id.rb_none -> {
                    updateRepeatDescription("안함", "")
                    binding.layoutRepeatInterval.visibility = View.GONE
                    hideKeyboard()
                }
                R.id.rb_daily -> {
                    updateRepeatDescription("일간", currentInterval)
                    showIntervalInput("일마다")
                    // 일간일 때는 반복 종료를 보여주지 않음 (요청 사항 반영)
                }
                R.id.rb_week -> {
                    updateRepeatDescription("주간", currentInterval)
                    showIntervalInput("주마다")
                    binding.layoutDayOfWeek.visibility = View.VISIBLE // 요일 선택 보이기
                    binding.layoutRepeatEnd.visibility = View.VISIBLE // 반복 종료 보이기
                }
                R.id.rb_month -> {
                    updateRepeatDescription("월간", currentInterval)
                    showIntervalInput("월마다")
                    binding.layoutRepeatEnd.visibility = View.VISIBLE // 반복 종료 보이기
                }
                R.id.rb_year -> {
                    updateRepeatDescription("연간", currentInterval)
                    showIntervalInput("년마다")
                    binding.layoutRepeatEnd.visibility = View.VISIBLE // 반복 종료 보이기
                }
            }
        }
    }

    // 결과를 전달하고 화면을 닫는 단일 책임 함수
    private fun sendResultAndBack() {
        val selectedId = binding.rgRepeatOptions.checkedRadioButtonId
        val interval = binding.etInterval.text.toString()

        val selectedValue = when (selectedId) {
            R.id.rb_none -> "안함"
            R.id.rb_daily -> "${interval}일마다"
            R.id.rb_week -> "${interval}주마다"
            R.id.rb_month -> "${interval}월마다"
            R.id.rb_year -> "${interval}년마다"
            else -> "안함"
        }

        // "repeatKey"라는 이름으로 Bundle 결과 전달
        // Fragment Result API 사용
        parentFragmentManager.setFragmentResult(
            "repeatKey",
            bundleOf("selectedRepeat" to selectedValue)
        )

        // 이전 프래그먼트(GeneralScheduleFragment)로 복귀
        parentFragmentManager.popBackStack()
    }

    private fun updateRepeatDescription(type: String, interval: String) {
        // 숫자가 비어있으면 기본값 "1"로 취급
        val displayInterval = if (interval.isEmpty()) "1" else interval

        val description = when (type) {
            "안함" -> "일정 반복을 진행하지 않습니다."
            "일간" -> {
                if (displayInterval == "1") "매일 반복됩니다."
                else "매 ${displayInterval}일마다 반복됩니다."
            }
            "주간" -> {
                if (displayInterval == "1") "매주 해당 요일마다 반복됩니다."
                else "매 ${displayInterval}주마다 반복됩니다."
            }
            "월간" -> {
                if (displayInterval == "1") "매월 해당 일마다 반복됩니다."
                else "매 ${displayInterval}개월마다 반복됩니다."
            }
            "연간" -> {
                if (displayInterval == "1") "매년 해당 날짜마다 반복됩니다."
                else "매 ${displayInterval}년마다 반복됩니다."
            }
            else -> ""
        }
        binding.tvRepeatDescription.text = description
    }

    // 반복 주기 입력창을 보여주고 키보드를 올리는 함수
    private fun showIntervalInput(unitText: String) {
        binding.layoutRepeatInterval.visibility = View.VISIBLE
        binding.tvIntervalUnit.text = unitText

        // EditText에 포커스를 주고 키보드를 올림
        binding.etInterval.requestFocus()

        // 약간의 딜레이를 주어 뷰가 완전히 그려진 후 키보드가 올라오게 함
        binding.etInterval.postDelayed({
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.etInterval, InputMethodManager.SHOW_IMPLICIT)
        }, 100)
    }

    fun updateRepeatText() {
        // 1. EditText 등에서 주기(예: 1, 2)를 가져옵니다.
        // binding.etInterval 부분은 실제 본인의 EditText ID로 수정하세요.
        val displayInterval = binding.etInterval.text.toString().ifEmpty { "1" }

        val selectedDays = mutableListOf<String>()
        val days = listOf(
            binding.cbSun to "일", binding.cbMon to "월", binding.cbTue to "화",
            binding.cbWed to "수", binding.cbThu to "목", binding.cbFri to "금", binding.cbSat to "토"
        )

        days.forEach { (checkBox, name) ->
            if (checkBox.isChecked) selectedDays.add(name)
        }

        // 요일이 하나도 선택되지 않았을 때의 처리 추가
        if (selectedDays.isEmpty()) {
            binding.tvRepeatDescription.text = "반복할 요일을 선택해주세요."
            return
        }

        val daysText = selectedDays.joinToString(", ") + "요일"

        val finalDescription = when {
            binding.rbWeek.isChecked -> {
                if (displayInterval == "1") "매주 $daysText 마다 반복됩니다."
                else "매 ${displayInterval}주 $daysText 마다 반복됩니다."
            }
            binding.rbMonth.isChecked -> "매월 $daysText 마다 반복됩니다."
            // ... 나머지 연간 등 로직 추가
            else -> "일정 반복을 진행하지 않습니다."
        }

        binding.tvRepeatDescription.text = finalDescription
    }


    // 키보드를 숨기는 유틸리티 함수
    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view?.windowToken, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
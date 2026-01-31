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
import android.graphics.Color
import android.widget.RadioButton

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

        setupListeners()

        updateFullDescription()

        binding.repeatToolbar.setNavigationOnClickListener {
            sendResultAndBack()
        }
    }

    private fun setupListeners() {
        binding.repeatToolbar.setNavigationOnClickListener { sendResultAndBack() }


        binding.rgRepeatOptions.setOnCheckedChangeListener { group, checkedId ->
            handleLayoutVisibility(checkedId)
            updateFullDescription()
        }

        val endRadioButtons = listOf(binding.rbEndNever, binding.rbEndCount, binding.rbEndDate)
        endRadioButtons.forEach { rb ->
            rb.setOnClickListener { clickedView ->
                endRadioButtons.forEach { it.isChecked = (it == clickedView) }

                handleEndLayoutVisibility()
                updateFullDescription()


                if (clickedView == binding.rbEndCount) {
                    binding.etEndCount.requestFocus()
                    val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(binding.etEndCount, InputMethodManager.SHOW_IMPLICIT)
                } else {
                    hideKeyboard()
                }
            }
        }

        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { updateFullDescription() }
        }
        binding.etInterval.addTextChangedListener(textWatcher)
        binding.etEndCount.addTextChangedListener(textWatcher)


        val dayCheckBoxes = listOf(
            binding.cbSun, binding.cbMon, binding.cbTue,
            binding.cbWed, binding.cbThu, binding.cbFri, binding.cbSat
        )
        dayCheckBoxes.forEach { cb ->
            cb.setOnCheckedChangeListener { _, _ -> updateFullDescription() }
        }
    }

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

        // Fragment Result API 사용
        parentFragmentManager.setFragmentResult(
            "repeatKey",
            bundleOf("selectedRepeat" to selectedValue)
        )

        parentFragmentManager.popBackStack()
    }

    private fun handleLayoutVisibility(checkedId: Int) {
        val isRepeat = checkedId != R.id.rb_none
        val isWeekly = checkedId == R.id.rb_week

        binding.layoutRepeatInterval.visibility = if (isRepeat) View.VISIBLE else View.GONE
        binding.layoutRepeatEnd.visibility = if (isRepeat) View.VISIBLE else View.GONE
        binding.layoutDayOfWeek.visibility = if (isWeekly) View.VISIBLE else View.GONE

        if (checkedId == R.id.rb_none) hideKeyboard()

        binding.tvIntervalUnit.text = when(checkedId) {
            R.id.rb_daily -> "일마다"
            R.id.rb_week -> "주마다"
            R.id.rb_month -> "개월마다"
            R.id.rb_year -> "년마다"
            else -> ""
        }
    }

    private fun handleEndLayoutVisibility() {
        val endId = binding.rgEndOptions.checkedRadioButtonId
    }

    private fun updateFullDescription() {
        val interval = binding.etInterval.text.toString().ifEmpty { "1" }
        val typeId = binding.rgRepeatOptions.checkedRadioButtonId

        if (typeId == R.id.rb_none) {
            binding.tvRepeatDescription.text = "일정 반복을 진행하지 않습니다."
            return
        }


        val selectedDays = mutableListOf<String>()
        if (typeId == R.id.rb_week) {
            val days = listOf(
                binding.cbSun to "일", binding.cbMon to "월", binding.cbTue to "화",
                binding.cbWed to "수", binding.cbThu to "목", binding.cbFri to "금", binding.cbSat to "토"
            )
            days.forEach { (cb, name) -> if (cb.isChecked) selectedDays.add(name) }
        }
        val daysText = if (selectedDays.isNotEmpty()) "${selectedDays.joinToString(", ")}요일" else ""

        val unit = when(typeId) {
            R.id.rb_daily -> "일"
            R.id.rb_week -> "주"
            R.id.rb_month -> "개월"
            R.id.rb_year -> "년"
            else -> ""
        }
        val intervalText = if (interval == "1") "매$unit" else "${interval}${unit}마다"

        val countValue = binding.etEndCount.text.toString().ifEmpty { "1" }
        if (binding.rbEndCount.isChecked) {
            binding.rbEndCount.text = ""
            binding.layoutCountInput.visibility = View.VISIBLE
        } else {
            binding.rbEndCount.text = "횟수 지정"
            binding.layoutCountInput.visibility = View.GONE
        }

        val endText = when {
            binding.rbEndNever.isChecked -> "반복됩니다"
            binding.rbEndCount.isChecked -> "${countValue}회 반복됩니다"
            binding.rbEndDate.isChecked -> "종료 날짜까지 반복됩니다"
            else -> "반복됩니다"
        }


        val finalSentence = "$intervalText $daysText $endText".replace("  ", " ").trim()
        binding.tvRepeatDescription.text = finalSentence
    }

    private fun showIntervalInput(unitText: String) {
        binding.layoutRepeatInterval.visibility = View.VISIBLE
        binding.tvIntervalUnit.text = unitText

        binding.etInterval.requestFocus()

        binding.etInterval.postDelayed({
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.etInterval, InputMethodManager.SHOW_IMPLICIT)
        }, 100)
    }


    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view?.windowToken, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
package com.example.pace.ui.add_schedule

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import com.example.pace.R
import com.example.pace.databinding.FragmentAlarmScheduleBinding

class AlarmScheduleFragment : Fragment() {

    private var _binding: FragmentAlarmScheduleBinding? = null
    private val binding get() = _binding!!

    private val selectedOptions = mutableSetOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAlarmScheduleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.alarmScheduleToolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // 시스템 뒤로가기 버튼 처리
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                parentFragmentManager.popBackStack()
            }
        })

        val checkBoxes = listOf(
            binding.rbStarttime, binding.rbStart5mago, binding.rbStart10mago, binding.rbStart15mago,
            binding.rbStart30mago, binding.rbStart1hago, binding.rbStart2hago,
            binding.rbStart1dago, binding.rbStart2dago, binding.rbStart1wago
        )


        checkBoxes.forEach { checkBox ->
            checkBox.setOnCheckedChangeListener { buttonView, isChecked ->
                val text = buttonView.text.toString()

                if (isChecked) {

                    if (selectedOptions.size >= 5) {
                        buttonView.isChecked = false

                        return@setOnCheckedChangeListener
                    }
                    selectedOptions.add(text)
                } else {
                    selectedOptions.remove(text)
                }


                updateUIAndResult()
            }
        }

        // 안함(rb_none) 버튼 클릭 시 모든 체크 해제
        binding.rbNone.setOnClickListener {
            checkBoxes.forEach { it.isChecked = false }
            selectedOptions.clear()
            updateUIAndResult()
        }

    }

    private fun updateUIAndResult() {

        if (selectedOptions.size >= 5) {
            binding.tvAlarmDescription.setTextColor(Color.RED)
        } else {
            binding.tvAlarmDescription.setTextColor(Color.parseColor("#666666"))
        }

        val resultText = selectedOptions.joinToString(", ")
        val bundle = Bundle().apply { putString("selectedAlarm", resultText) }

        parentFragmentManager.setFragmentResult("scheduleAlarmKey", bundle)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
package com.example.pace.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import com.example.pace.databinding.FragmentSettingEarlyarrivedBinding

class SettingEarlyarrivedFragment : Fragment() {

    private var _binding: FragmentSettingEarlyarrivedBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentSettingEarlyarrivedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        setupNumberPicker()

        binding.repeatToolbar.setNavigationOnClickListener {
            sendResultAndBack()
        }
    }

    private fun setupNumberPicker() {
        binding.pickerYear.apply {
            minValue = 0
            maxValue = 60
            value = 10 // 기본값 10분 설정

            setFormatter { String.format("%02d", it) }
            wrapSelectorWheel = true


            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        }
    }

    private fun sendResultAndBack() {
        val selectedMinutes = binding.pickerYear.value

        val resultText = if (selectedMinutes == 0) "안함" else "${selectedMinutes}분"


        setFragmentResult(
            "earlyDepartureKey",
            bundleOf("selectedMinutes" to resultText)
        )

        parentFragmentManager.popBackStack()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
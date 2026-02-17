package com.example.pace.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import com.example.pace.R
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

        val initialValue = arguments?.getInt("currentMinutes") ?: 0

        binding.pickerYear.visibility = View.VISIBLE
        setupNumberPicker(initialValue)

    }

    private fun setupNumberPicker(initialValue: Int) {
        binding.pickerYear.apply {
            minValue = 0
            maxValue = 60
            value = initialValue // 💡 넘겨받은 숫자로 시작 위치 설정

            // 2. 초기 진입 시점에도 결과값을 한번 전송해두는 것이 안전합니다.
            sendResult(initialValue)

            setOnValueChangedListener { _, _, newVal ->
                sendResult(newVal)
            }

            setFormatter { String.format("%02d", it) }
            wrapSelectorWheel = true
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        }
    }

    private fun sendResult(minutes: Int) {
        setFragmentResult("earlyDepartureKey", bundleOf("selectedMinutes" to minutes))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
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

        binding.pickerYear.visibility = View.VISIBLE
        setupNumberPicker()

        activity?.findViewById<View>(R.id.settings_back_iv)?.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun setupNumberPicker() {
        binding.pickerYear.apply {
            minValue = 0
            maxValue = 60
            value = 10

            setOnValueChangedListener { _, _, _ ->
                sendCurrentValue()
            }

            setOnValueChangedListener { _, _, newVal ->
                val resultText = if (newVal == 0) "안함" else "${newVal}분"
                setFragmentResult("earlyDepartureKey", bundleOf("selectedMinutes" to resultText))
            }

            setFormatter { String.format("%02d", it) }
            wrapSelectorWheel = true
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        }
    }

    private fun sendCurrentValue() {
        val selectedMinutes = binding.pickerYear.value
        val resultText = if (selectedMinutes == 0) "안함" else "${selectedMinutes}분"

        setFragmentResult("earlyDepartureKey", bundleOf("selectedMinutes" to resultText))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
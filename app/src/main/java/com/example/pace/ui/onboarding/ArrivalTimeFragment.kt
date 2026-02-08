package com.example.pace.ui.onboarding

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.example.pace.R
import com.example.pace.databinding.FragmentArrivalTimeBinding

class ArrivalTimeFragment : Fragment() {

    private var _binding: FragmentArrivalTimeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentArrivalTimeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. NumberPicker 설정
        setupNumberPicker()

        // 2. 다음 버튼 클릭 시 저장 및 이동
        binding.btnNext.setOnClickListener {
            saveArrivalTime(binding.numberPicker.value)
            navigateToNextPage()
        }
    }

    private fun setupNumberPicker() {
        binding.numberPicker.apply {
            minValue = 0
            maxValue = 60
            value = 60 // 기본값 60분 설정

            // 텍스트가 순환되게 하고 싶다면 (0 다음 바로 60)
            wrapSelectorWheel = true
        }
    }

    private fun saveArrivalTime(minutes: Int) {
        // SharedPreferences에 저장
        val sharedPref = requireActivity().getSharedPreferences("PaceSettings", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putInt("arrival_buffer_time", minutes)
            apply() // 비동기로 저장
        }
    }

    private fun navigateToNextPage() {
        // 부모 Fragment(AppSettingPagerFragment)의 ViewPager2를 찾아서 다음 페이지로 이동
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
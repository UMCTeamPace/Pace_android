package com.example.pace.ui.main.calendar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.pace.databinding.FragmentCalendarBinding
import com.google.android.material.tabs.TabLayoutMediator

// 전체 프래그먼트를 포함하는 프래그먼트로 뷰페이저를 담고있음
class CalendarFragment: Fragment() {
    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val calendarFragmentAdapter = CalendarFragmentAdapter(this)
        binding.calendarVp.adapter = calendarFragmentAdapter
        binding.calendarVp.isUserInputEnabled = false // 가로 스크롤 오류로 인하여 뷰페이저 스크롤 막기

        val tabTitles = listOf("List", "Calendar")
        TabLayoutMediator(binding.calendarTabLayout, binding.calendarVp) { tab, position ->
            tab.text = tabTitles[position]
        }.attach()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
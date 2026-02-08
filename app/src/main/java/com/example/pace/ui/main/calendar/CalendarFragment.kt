package com.example.pace.ui.main.calendar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.example.pace.databinding.FragmentCalendarBinding
import com.example.pace.ui.main.MainActivity
import com.google.android.material.tabs.TabLayoutMediator
import com.example.pace.ui.main.calendar.SearchFragment
import com.example.pace.R

class CalendarFragment: Fragment() {
    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ScheduleViewModel by lazy {
        (requireActivity() as MainActivity).getSharedViewModel()
    }
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

        val mainActivity = requireActivity() as MainActivity

        // 1. 액티비티 툴바의 돋보기 버튼 리스너 달기
        mainActivity.binding.scheduleSearchIv.setOnClickListener {
            // 프래그먼트 전환 로직
            parentFragmentManager.beginTransaction()
                .replace(R.id.main_fcv, SearchFragment()) // 액티비티의 컨테이너 ID 사용
                .addToBackStack(null) // 뒤로가기 버튼 지원
                .commit()
        }

        val calendarFragmentAdapter = CalendarFragmentAdapter(this)
        binding.calendarVp.adapter = calendarFragmentAdapter
        binding.calendarVp.isUserInputEnabled = false

        val tabTitles = listOf("List", "Calendar")
        TabLayoutMediator(binding.calendarTabLayout, binding.calendarVp) { tab, position ->
            tab.text = tabTitles[position]
        }.attach()

        binding.calendarVp.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                val mainActivity = requireActivity() as MainActivity
                when (position) {
                    0 -> mainActivity.binding.scheduleEditIv.visibility = View.VISIBLE  // List tab
                    1 -> mainActivity.binding.scheduleEditIv.visibility = View.GONE     // Calendar tab
                }
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

package com.example.pace.ui.main.calendar

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

// 뷰페이저 어댑터로 넘어가는 화면 정하는 코드
class CalendarFragmentAdapter(fragment: Fragment): FragmentStateAdapter(fragment) {
    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when(position) {
            0 -> ScheduleListFragment()
            else -> CalendarPageFragment()
        }
    }
}
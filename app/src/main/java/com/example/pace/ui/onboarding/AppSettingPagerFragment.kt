package com.example.pace.ui.onboarding

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.pace.databinding.FragmentAppSettingPagerBinding


class AppSettingPagerFragment : Fragment() {
    private var _binding: FragmentAppSettingPagerBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAppSettingPagerBinding.inflate(inflater, container, false)

        val adapter = InnerSettingAdapter(this)

        binding.appSettingViewpager.adapter = adapter

        // 스와이프 차단(다음버튼 눌러야만 움직이는거 가능하게)
        binding.appSettingViewpager.isUserInputEnabled = false

        // 인디케이터 제어 로직
        binding.appSettingViewpager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val indicators = listOf(binding.step1, binding.step2, binding.step3, binding.step4)
                indicators.forEachIndexed { index, view ->
                    view.setBackgroundColor(if (index <= position) Color.parseColor("#9ACD32") else Color.parseColor("#E0E0E0"))
                }
            }
        })

        return binding.root
    }

    // 내부 프래그먼트 어댑터
    inner class InnerSettingAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        override fun getItemCount(): Int = 4
        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> ArrivalTimeFragment()    // 도착시간
                1 -> ScheduleAlarmFragment() // 일정알림
                2 -> DepartAlarmFragment()   // 출발알림
                3 -> CalendarSelectFragment() // 캘린더 선택
                else -> ArrivalTimeFragment()
            }
        }
    }
}
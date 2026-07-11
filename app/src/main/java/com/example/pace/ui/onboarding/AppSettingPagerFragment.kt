package com.example.pace.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.pace.R
import com.example.pace.databinding.FragmentAppSettingPagerBinding


class AppSettingPagerFragment : Fragment() {
    private var _binding: FragmentAppSettingPagerBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAppSettingPagerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. 어댑터 설정
        val adapter = InnerSettingAdapter(this)
        binding.appSettingViewpager.adapter = adapter
        binding.appSettingViewpager.isUserInputEnabled = false // 스와이프 차단

        // 2. 인디케이터 제어 로직
        binding.appSettingViewpager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                val indicators = listOf(binding.step1, binding.step2, binding.step3, binding.step4)
                indicators.forEachIndexed { index, view ->
                    if (index == position) {
                        view.setBackgroundResource(R.drawable.bg_onboarding_step_active)
                    } else {
                        view.setBackgroundResource(R.drawable.bg_onboarding_step_inactive)
                    }
                }
            }
        })

        // 3. 시스템 뒤로가기 버튼 제어 (핵심!)
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.appSettingViewpager.currentItem > 0) {
                    // 현재 페이지가 첫 번째(0)가 아니면 뷰페이저 안에서 이전 페이지로 이동
                    binding.appSettingViewpager.currentItem = binding.appSettingViewpager.currentItem - 1
                } else {
                    // 첫 번째 페이지(미리 도착)라면 이 콜백을 끄고 시스템 뒤로가기 실행 (권한 화면으로 이동)
                    isEnabled = false
                    requireActivity().onBackPressed()
                }
            }
        })

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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

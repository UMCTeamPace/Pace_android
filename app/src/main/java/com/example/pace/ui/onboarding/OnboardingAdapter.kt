package com.example.pace.ui.onboarding

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

// FragmentActivity 대신 Fragment를 인자로 받아도 됩니다 (OnboardingFragment 내에서 사용할 것이므로)
class OnboardingAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    // 보여줄 페이지는 총 4개
    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> OnboardingFragment1()
            1 -> OnboardingFragment2()
            2 -> OnboardingFragment3()
            3 -> OnboardingFragment4()
            else -> OnboardingFragment1() // 예외 처리용
        }
    }
}
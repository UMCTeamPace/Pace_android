package com.example.pace.ui.onboarding

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter


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
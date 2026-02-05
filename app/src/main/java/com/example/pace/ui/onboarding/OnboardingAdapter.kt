package com.example.pace.ui.onboarding

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.pace.data.model.OnboardingItem

class OnboardingAdapter(
    fragmentActivity: FragmentActivity,
    private val onboardingItems: List<OnboardingItem> // 데이터를 리스트로 받음
) : FragmentStateAdapter(fragmentActivity) {

    // 리스트의 크기에 따라 페이지 개수 결정
    override fun getItemCount(): Int = 4

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> Onboarding1Fragment()
            1 -> Onboarding2Fragment()
            2 -> Onboarding3Fragment()
            3 -> Onboarding4Fragment()
            else -> Onboarding1Fragment()
        }
    }
}
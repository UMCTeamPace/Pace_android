package com.example.pace.ui.onboarding

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.example.pace.R
import com.example.pace.databinding.FragmentOnboardingBinding

class OnboardingFragment(
    private val title: String,
    private val description: String,
    private val imageRes: Int
) : Fragment(R.layout.fragment_onboarding) {

    private var _binding: FragmentOnboardingBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentOnboardingBinding.bind(view)

        // 1. 이미지 세팅 (Lottie 대신 ImageView 사용)
        binding.ivOnboarding.setImageResource(imageRes)

        // 2. 텍스트 세팅
        binding.tvTitle.text = title
        binding.tvDescription.text = description
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
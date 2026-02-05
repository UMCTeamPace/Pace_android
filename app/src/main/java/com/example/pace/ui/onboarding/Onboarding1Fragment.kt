package com.example.pace.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.pace.databinding.FragmentOnboarding1Binding

class Onboarding1Fragment : Fragment() {
    private var _binding: FragmentOnboarding1Binding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboarding1Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 만약 코드에서 이미지를 변경하거나 애니메이션을 주고 싶다면 여기서 처리합니다.
        // 예: binding.ivOnboardingMain.alpha = 0f
        //     binding.ivOnboardingMain.animate().alpha(1f).duration = 1000
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
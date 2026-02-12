package com.example.pace.ui.onboarding

import android.graphics.Typeface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.pace.databinding.FragmentOnboarding2Binding

class OnboardingFragment2 : Fragment() {
    private var _binding: FragmentOnboarding2Binding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboarding2Binding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.tvDescription.setBoldText(
            "하루의 일정과 이동 사이에서\n당신의 페이스는 어땠나요?",
            listOf("당신의 페이스")
        )
    }

    fun TextView.setBoldText(fullText: String, boldKeywords: List<String>) {
        val spannable = SpannableStringBuilder(fullText)

        boldKeywords.forEach { keyword ->
            val start = fullText.indexOf(keyword)
            if (start != -1) {
                spannable.setSpan(
                    StyleSpan(Typeface.BOLD),
                    start,
                    start + keyword.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        this.text = spannable
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
package com.example.pace.ui.onboarding

import android.content.Context
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
import androidx.viewpager2.widget.ViewPager2
import com.example.pace.R
import com.example.pace.databinding.FragmentArrivalTimeBinding

class ArrivalTimeFragment : Fragment() {

    private var _binding: FragmentArrivalTimeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentArrivalTimeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupNumberPicker()

        binding.btnNext.setOnClickListener {
            saveArrivalTime(binding.numberPicker.value)
            navigateToNextPage()
        }

        binding.tvDescription.setBoldText(
            "일정 시작 몇 분 전에 도착하는 것을 선호하시나요?",
            listOf("몇 분 전에")
        )
    }

    private fun setupNumberPicker() {
        binding.numberPicker.apply {
            minValue = 0
            maxValue = 60
            value = 60

            //0~60 순환되게
            wrapSelectorWheel = true
        }
    }

    private fun saveArrivalTime(minutes: Int) {
        // SharedPreferences에 저장
        val sharedPref = requireActivity().getSharedPreferences("PaceSettings", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putInt("arrival_buffer_time", minutes)
            apply() // 비동기로 저장
        }
    }

    private fun navigateToNextPage() {
        // 부모 Fragment(AppSettingPagerFragment)의 ViewPager2를 찾아서 다음 페이지로 이동
        val viewPager = activity?.findViewById<ViewPager2>(R.id.app_setting_viewpager)
        viewPager?.let {
            it.currentItem = it.currentItem + 1
        }
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
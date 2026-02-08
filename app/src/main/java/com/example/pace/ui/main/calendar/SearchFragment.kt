package com.example.pace.ui.main.calendar

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment // 반드시 androidx 패키지여야 함
import com.example.pace.databinding.FragmentSearchBinding
import com.example.pace.ui.main.MainActivity

// 1. Fragment() 상속 추가
class SearchFragment : Fragment() {
    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    // 2. 레이아웃 인플레이트 필수
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mainActivity = requireActivity() as MainActivity
        // 검색 화면 진입 시 액티비티 메인 툴바 숨기기
        mainActivity.binding.mainToolbar.visibility = View.GONE

        // 뒤로가기 버튼 설정
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 검색 화면 나갈 때 액티비티 메인 툴바 다시 보이기
        (requireActivity() as MainActivity).binding.mainToolbar.visibility = View.VISIBLE
        _binding = null
    }
}
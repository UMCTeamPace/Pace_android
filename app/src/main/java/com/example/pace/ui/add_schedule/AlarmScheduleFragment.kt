package com.example.pace.ui.add_schedule // 패키지명 확인

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import com.example.pace.R
import com.example.pace.databinding.FragmentAlarmScheduleBinding // 바인딩 클래스 임포트

class AlarmScheduleFragment : Fragment() {

    // 1. binding 변수를 선언해야 에러가 사라집니다.
    private var _binding: FragmentAlarmScheduleBinding? = null
    private val binding get() = _binding!!

    // 2. onCreateView에서 레이아웃을 초기화해야 합니다.
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAlarmScheduleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // toolbar 대신 alarmScheduleToolbar를 사용합니다.
        binding.alarmScheduleToolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // 시스템 뒤로가기 버튼 처리
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                parentFragmentManager.popBackStack()
            }
        })

        // 라디오 버튼 선택 시 데이터 전달 (예시: 10분 전 선택 시)
        binding.rgRepeatOptions.setOnCheckedChangeListener { _, checkedId ->
            val selectedText = when(checkedId) {
                R.id.rb_none -> "안함"
                R.id.rb_starttime -> "일정시작시간"
                R.id.rb_5mago -> "5분 전"
                R.id.rb_10mago -> "10분 전"
                R.id.rb_15mago -> "15분 전"
                R.id.rb_30mago -> "30분 전"
                R.id.rb_1hago -> "1시간 전"
                R.id.rb_2hago -> "2시간 전"
                R.id.rb_1wago -> "1일 전"
                else -> "안함"
            }

            // 결과 전달용 키와 데이터를 담아 보냅니다.
            val bundle = Bundle().apply { putString("selectedAlarm", selectedText) }
            parentFragmentManager.setFragmentResult("alarmKey", bundle)

            // 선택 후 자동으로 이전 화면으로 복귀하고 싶다면:
            // parentFragmentManager.popBackStack()
        }

        binding.alarmScheduleToolbar.setNavigationOnClickListener {
            parentFragmentManager.popBackStack()
        }

    }

    // 5. 메모리 누수 방지
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
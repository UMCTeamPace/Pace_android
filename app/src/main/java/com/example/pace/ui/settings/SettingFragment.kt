package com.example.pace.ui.settings

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.replace
import androidx.fragment.app.setFragmentResultListener
import com.example.pace.R
import com.example.pace.databinding.FragmentSettingBinding

class SettingFragment: Fragment() {
    private var _binding: FragmentSettingBinding? = null
    private val binding get() = _binding!!
    private var selectedEarlyTime: String = "10분"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentSettingBinding.inflate(inflater, container, false)
        val title = activity?.findViewById<TextView>(R.id.settings_tv)
        title?.text = "설정"

        // Todo: 미리 출발 레이아웃 및 프래그먼트 구현해 연결
        // Todo: 클릭 시 상호 작용하는 코드 작성(현재는 단순 뷰)
        binding.settingsCalendarDefaultIv.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.settings_fcv, DefaultCalendarFragment())
                .addToBackStack(null)
                .commit()
            title?.text = "기본 캘린더"
        }
        binding.settingsCalendarListIv.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.settings_fcv, SyncWithCalendarFragment())
                .addToBackStack(null)
                .commit()
            title?.text = "캘린더 목록"
        }
        binding.settingsReminderAlarmIv.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.settings_fcv, SettingReminderFragment())
                .addToBackStack(null)
                .commit()
            title?.text = "일정 알림"
        }
        binding.settingsDepartureAlarmIv.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.settings_fcv, SettingDepartureFragment())
                .addToBackStack(null)
                .commit()
            title?.text = "출발 알림"
        }
        binding.settingsReviewLl.setOnClickListener {
            val reviewDialog = ReviewDialog(requireContext())
            reviewDialog.show()
        }
        binding.settingsSignoutLl.setOnClickListener {
            val signoutDialog = SignoutDialog(requireContext())
            signoutDialog.show()
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val title = activity?.findViewById<TextView>(R.id.settings_tv)
        title?.text = "설정"

        binding.settingsRouteMinuteTv.text = selectedEarlyTime

        setFragmentResultListener("earlyDepartureKey") { _, bundle ->
            val resultText = bundle.getString("selectedMinutes") ?: "10분"
            selectedEarlyTime = resultText

            _binding?.let {
                it.settingsRouteMinuteTv.text = resultText
            }
        }

        binding.settingsRouteLl.setOnClickListener {
            title?.text = "미리 도착"

            parentFragmentManager.beginTransaction()
                .replace(R.id.settings_fcv, SettingEarlyarrivedFragment())
                .addToBackStack(null)
                .commit()
        }

        parentFragmentManager.addOnBackStackChangedListener {
            if (parentFragmentManager.backStackEntryCount == 0) {
                val title = activity?.findViewById<TextView>(R.id.settings_tv)
                title?.text = "설정"
            }
        }



    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
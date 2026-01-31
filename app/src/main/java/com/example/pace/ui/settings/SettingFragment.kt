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
import com.example.pace.R
import com.example.pace.databinding.FragmentSettingBinding

class SettingFragment: Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val binding = FragmentSettingBinding.inflate(inflater, container, false)
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

        binding.settingsRouteLl.setOnClickListener {
            val earlyDepartureFragment = SettingEarlyarrivedFragment()
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.main_fcv, earlyDepartureFragment)
                .addToBackStack(null)
                .commit()
        }


        return binding.root
    }
}
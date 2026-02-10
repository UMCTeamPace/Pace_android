package com.example.pace.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.Fragment
import com.example.pace.R
import com.example.pace.databinding.FragmentDefaultCalendarBinding
import com.example.pace.databinding.FragmentSettingDepartureBinding
import com.example.pace.databinding.FragmentSettingReminderBinding
import com.example.pace.databinding.FragmentSyncWithCalendarBinding

class DefaultCalendarFragment: Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val binding = FragmentDefaultCalendarBinding.inflate(inflater, container,false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentDefaultCalendarBinding.bind(view)

        binding.rgRepeatOptions.setOnCheckedChangeListener { group, checkedId ->
            when (checkedId) {
                R.id.default_calendar_my_phone -> {
                    // '내 휴대전화' 선택 시 동작
                }
                R.id.default_calendar_samsung -> {
                    // '삼성계정' 선택 시 동작
                }
                R.id.default_calendar_google -> {
                    // '구글계정1' 선택 시 동작
                }
                R.id.default_calendar_google2 -> {
                    // '구글계정2' 선택 시 동작
                }
            }
        }
    }
}
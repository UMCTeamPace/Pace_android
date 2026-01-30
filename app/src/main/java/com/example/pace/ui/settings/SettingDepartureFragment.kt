package com.example.pace.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.Fragment
import com.example.pace.R
import com.example.pace.databinding.FragmentSettingDepartureBinding
import com.example.pace.databinding.FragmentSettingReminderBinding
import com.example.pace.databinding.FragmentSyncWithCalendarBinding

class SettingDepartureFragment: Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val binding = FragmentSettingDepartureBinding.inflate(inflater, container,false)
        return binding.root
    }
}
package com.example.pace.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pace.R
import com.example.pace.data.model.CalendarAccount
import com.example.pace.data.viewmodel.SettingsViewModel
import com.example.pace.databinding.FragmentDefaultCalendarBinding
import com.example.pace.databinding.FragmentSettingDepartureBinding
import com.example.pace.databinding.FragmentSettingReminderBinding
import com.example.pace.databinding.FragmentSyncWithCalendarBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DefaultCalendarFragment : Fragment() {
    private var _binding: FragmentDefaultCalendarBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by viewModels()
    private lateinit var calendarAdapter: CalendarAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDefaultCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        observeData()
    }

    private fun setupRecyclerView() {
        calendarAdapter = CalendarAdapter { selectedId ->
            viewModel.updateDefaultCalendar(selectedId)
        }
        binding.rvCalendarList.apply { // XML에서 RadioGroup 대신 RecyclerView(id: rv_calendar_list) 추가 필요
            adapter = calendarAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }
    }

    private fun observeData() {
        // 시스템 캘린더 가져오기
        val allCalendars = fetchCalendarAccounts()

        // DB 설정값 관찰하여 리스트 갱신
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.userSettings.collect { settings ->
                val currentId = settings?.calendarId ?: -1L
                val effectiveId = resolveDefaultCalendarId(currentId, allCalendars)
                calendarAdapter.submitList(allCalendars, effectiveId)
            }
        }
    }

    private fun resolveDefaultCalendarId(
        preferredId: Long,
        calendars: List<CalendarAccount>
    ): Long {
        val availableIds = calendars.mapNotNull { it.id.toLongOrNull() }
        return when {
            preferredId in availableIds -> preferredId
            availableIds.isNotEmpty() -> availableIds.first()
            else -> -1L
        }
    }

    private fun fetchCalendarAccounts(): List<CalendarAccount> {
        val list = mutableListOf<CalendarAccount>()
        val projection = arrayOf(
            android.provider.CalendarContract.Calendars._ID,
            android.provider.CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            android.provider.CalendarContract.Calendars.ACCOUNT_NAME
        )
        val cursor = requireContext().contentResolver.query(
            android.provider.CalendarContract.Calendars.CONTENT_URI, projection, null, null, null
        )
        cursor?.use {
            while (it.moveToNext()) {
                list.add(
                    CalendarAccount(
                        it.getLong(0).toString(),
                        it.getString(1),
                        it.getString(2),
                        0
                    )
                )
            }
        }
        return list
    }
}
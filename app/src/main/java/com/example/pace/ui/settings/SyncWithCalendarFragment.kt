package com.example.pace.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pace.data.model.CalendarAccount
import com.example.pace.data.viewmodel.SettingsViewModel
import com.example.pace.databinding.FragmentSyncWithCalendarBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint // 💡 Hilt 주입을 위해 필수!
class SyncWithCalendarFragment : Fragment() {

    private var _binding: FragmentSyncWithCalendarBinding? = null
    private val binding get() = _binding!!

    // 💡 ViewModel 주입
    private val viewModel: SettingsViewModel by viewModels()
    private lateinit var syncAdapter: SyncCalendarAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSyncWithCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. 어댑터 초기화
        syncAdapter = SyncCalendarAdapter { id, isChecked ->
            // ViewModel의 토글 로직 호출 (아래에서 ViewModel 코드도 알려드릴게요)
            viewModel.toggleCalendarSync(id, isChecked)
        }

        // 2. 리사이클러뷰 설정
        binding.rvSyncCalendar.apply {
            adapter = syncAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }

        // 3. 데이터 관찰 (DB의 동기화 리스트가 바뀔 때마다 UI 갱신)
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.userSettings.collect { settings ->
                val allCalendars = fetchAllCalendars()
                // Entity에 저장된 동기화된 ID 리스트를 어댑터에 전달
                syncAdapter.submitData(allCalendars, settings?.syncedCalendarIds ?: emptyList())
            }
        }
    }

    private fun fetchAllCalendars(): List<CalendarAccount> {
        val list = mutableListOf<CalendarAccount>()
        val projection = arrayOf(
            android.provider.CalendarContract.Calendars._ID,
            android.provider.CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            android.provider.CalendarContract.Calendars.ACCOUNT_NAME,
            android.provider.CalendarContract.Calendars.CALENDAR_COLOR
        )

        val cursor = requireContext().contentResolver.query(
            android.provider.CalendarContract.Calendars.CONTENT_URI, projection, null, null, null
        )

        cursor?.use {
            while (it.moveToNext()) {
                list.add(
                    CalendarAccount(
                        id = it.getLong(0).toString(),
                        displayName = it.getString(1),
                        accountName = it.getString(2),
                        color = it.getInt(3)
                    )
                )
            }
        }
        return list
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
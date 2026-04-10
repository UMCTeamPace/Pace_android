package com.example.pace.ui.add_schedule

import android.Manifest
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Bundle
import android.provider.CalendarContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pace.data.model.CalendarAccount
import com.example.pace.databinding.FragmentSelectCalendarBinding

class SelectCalendarFragment : Fragment() {

    private var _binding: FragmentSelectCalendarBinding? = null
    private val binding get() = _binding!!

    // 1. 어댑터 선언 (늦은 초기화 또는 널 허용)
    private var calendarAdapter: CalendarSelectAdapter? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSelectCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    parentFragmentManager.popBackStack()
                }
            }
        )

        // 초기 뒤로가기 리스너 (데이터 없이 나갈 경우 대비)
        binding.btnBack.setOnClickListener { parentFragmentManager.popBackStack() }

        checkPermissionAndLoad()
    }

    private fun checkPermissionAndLoad() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CALENDAR)
            == PackageManager.PERMISSION_GRANTED) {
            loadCalendarProviders()
        } else {
            requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), 1004)
        }
    }

    private fun loadCalendarProviders() {
        val calendarList = mutableListOf<CalendarAccount>()
        val currentId = arguments?.getLong("currentCalendarId", -1L)
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_COLOR
        )

        val cursor: Cursor? = requireContext().contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            null, null, null
        )

        cursor?.use {
            while (it.moveToNext()) {
                val id = it.getString(0)
                val name = it.getString(1)
                var account = it.getString(2)
                val type = it.getString(3)
                val color = it.getInt(4)

                if (type == CalendarContract.ACCOUNT_TYPE_LOCAL || account.isNullOrEmpty()) {
                    account = "내 휴대전화"
                }
                calendarList.add(CalendarAccount(id, name, account, color))
            }
        }

        // 호출한 곳에서 보낸 Key (없으면 기본값)
        val requestKey = arguments?.getString("requestKey") ?: "calendarSelectKey"
        android.util.Log.d("CALENDAR_SEND", "현재 설정된 RequestKey: $requestKey")

        calendarAdapter = CalendarSelectAdapter(calendarList, currentId) { selected ->
            // 💡 전송 직전 로그
            android.util.Log.d("CALENDAR_SEND", "아이템 클릭됨: ${selected.displayName} (ID: ${selected.id})")

            setFragmentResult(requestKey, bundleOf(
                "calendarId" to selected.id.toLong(),
                "calendarName" to selected.displayName,
                "selectedCalendarColor" to selected.color
            ))

            android.util.Log.d("CALENDAR_SEND", "setFragmentResult 완료 (Key: $requestKey)")
            parentFragmentManager.popBackStack()
        }

        binding.rvCalendarList.apply {
            this.adapter = calendarAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }

        // 💡 2. 상단 뒤로가기 버튼은 이제 '단순 닫기' (취소) 역할만 수행
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 메모리 누수 방지
        _binding = null
        calendarAdapter = null
    }
}

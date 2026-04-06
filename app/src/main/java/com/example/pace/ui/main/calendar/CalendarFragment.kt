package com.example.pace.ui.main.calendar

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.example.pace.databinding.FragmentCalendarBinding
import com.example.pace.ui.main.MainActivity
import com.google.android.material.tabs.TabLayoutMediator
import com.example.pace.ui.main.calendar.SearchFragment
import com.example.pace.R
import com.example.pace.ui.add_schedule.AddScheduleActivity
import androidx.fragment.app.activityViewModels // 추가
import com.example.pace.data.viewmodel.ScheduleViewModel
import dagger.hilt.android.AndroidEntryPoint // 추가

@AndroidEntryPoint
class CalendarFragment: Fragment() {
    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!
    private var pendingResetToTodayState = false
    private var currentTabIndex = 1

    private val viewModel: ScheduleViewModel by activityViewModels()
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mainActivity = requireActivity() as MainActivity


        mainActivity.binding.scheduleEditIv.setOnClickListener {
            // ViewModel을 통해 편집 모드 활성화
            // 이 값을 관찰 중인 ScheduleListFragment가 UI를 자동으로 바꿉니다.
            viewModel.setEditMode(true)
        }

        // 1. 액티비티 툴바의 돋보기 버튼 리스너 달기
        mainActivity.binding.scheduleSearchIv.setOnClickListener {
            // 프래그먼트 전환 로직
            parentFragmentManager.beginTransaction()
                .replace(R.id.main_fcv, SearchFragment()) // 액티비티의 컨테이너 ID 사용
                .addToBackStack(null) // 뒤로가기 버튼 지원
                .commit()
        }

        mainActivity.binding.scheduleAddIv.setOnClickListener {
            val currentTab = binding.calendarVp.currentItem // 0: List, 1: Calendar
            val intent = Intent(requireContext(), AddScheduleActivity::class.java)

            when (currentTab) {
                0 -> {
                    // List 탭: 무조건 오늘 날짜 전달
                    val today = java.time.LocalDate.now().toString()
                    intent.putExtra("selected_date", today)
                    intent.putExtra("mode", "LIST_ADD")
                }
                1 -> {
                    // Calendar 탭: 선택된 날짜 전달 (없으면 오늘 날짜)
                    val selectedDate = viewModel.selectedDate.value?.toString()
                        ?: java.time.LocalDate.now().toString()

                    intent.putExtra("selected_date", selectedDate)
                    intent.putExtra("mode", "CALENDAR_ADD")
                }
            }

            startActivity(intent)
        }


        val calendarFragmentAdapter = CalendarFragmentAdapter(this)
        binding.calendarVp.visibility = View.INVISIBLE
        binding.calendarVp.adapter = calendarFragmentAdapter
        binding.calendarVp.offscreenPageLimit = 1
        binding.calendarVp.isUserInputEnabled = false
        binding.calendarVp.setCurrentItem(currentTabIndex, false)
        updateHeaderForTab(currentTabIndex)

        val tabTitles = listOf("리스트", "캘린더")
        TabLayoutMediator(binding.calendarTabLayout, binding.calendarVp) { tab, position ->
            tab.text = tabTitles[position]
        }.attach()

        binding.calendarVp.isUserInputEnabled = false

        binding.calendarVp.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                currentTabIndex = position
                updateHeaderForTab(position)
            }
        })

        binding.calendarVp.post {
            if (_binding == null) return@post
            binding.calendarVp.visibility = View.VISIBLE
        }

        applyPendingResetIfNeeded()
    }

    fun setTabVisibility(isVisible: Boolean) {
        val visibility = if (isVisible) View.VISIBLE else View.GONE
        binding.calendarTabLayout.visibility = visibility

        // [수정] 원래 스와이프를 막기로 했다면, 여기서 다시 true로 만들면 안 됩니다.
        // 편집 모드든 아니든 뷰페이저는 터치로 넘기지 못하게 false로 박아버립니다.
        binding.calendarVp.isUserInputEnabled = false
    }

    fun showCalendarTab() {
        if (_binding == null) return
        currentTabIndex = 1
        binding.root.visibility = View.INVISIBLE
        binding.calendarVp.setCurrentItem(currentTabIndex, false)
        updateHeaderForTab(currentTabIndex)
        binding.calendarTabLayout.post {
            if (_binding == null) return@post
            binding.calendarTabLayout.selectTab(binding.calendarTabLayout.getTabAt(currentTabIndex), false)
            binding.calendarTabLayout.setScrollPosition(currentTabIndex, 0f, true)
            binding.root.visibility = View.VISIBLE
        }
    }

    fun resetToTodayState() {
        if (_binding == null) {
            pendingResetToTodayState = true
            return
        }
        pendingResetToTodayState = false

        val today = java.time.LocalDate.now()
        viewModel.setSelectedDate(today)
        showCalendarTab()

        childFragmentManager.fragments.forEach { fragment ->
            when (fragment) {
                is ScheduleListFragment -> fragment.resetToToday()
                is CalendarPageFragment -> fragment.resetToTodayState()
            }
        }
    }

    private fun applyPendingResetIfNeeded() {
        if (!pendingResetToTodayState || _binding == null) return
        resetToTodayState()
    }

    private fun updateHeaderForTab(position: Int) {
        val activity = activity as? MainActivity ?: return
        when (position) {
            0 -> {
                activity.binding.scheduleEditIv.visibility = View.VISIBLE
                activity.binding.scheduleSearchIv.visibility = View.VISIBLE
                activity.binding.scheduleAddIv.visibility = View.VISIBLE
            }
            else -> {
                activity.binding.scheduleEditIv.visibility = View.GONE
                activity.binding.scheduleSearchIv.visibility = View.VISIBLE
                activity.binding.scheduleAddIv.visibility = View.VISIBLE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

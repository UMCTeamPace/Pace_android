package com.example.pace.ui.main.calendar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.example.pace.databinding.FragmentCalendarBinding
import com.example.pace.ui.main.MainActivity
import com.example.pace.ui.main.calendar.CalendarFragmentAdapter
import com.example.pace.data.db.ScheduleDatabase
import com.example.pace.data.repository.ScheduleRepository
import com.google.android.material.tabs.TabLayoutMediator
import androidx.fragment.app.viewModels
import com.example.pace.ui.main.calendar.ScheduleViewModel
import com.example.pace.ui.main.calendar.ScheduleViewModelFactory

class CalendarFragment: Fragment() {
    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!
    val viewModel: ScheduleViewModel by viewModels{
        ScheduleViewModelFactory(
            ScheduleRepository(
                ScheduleDatabase.getDatabase(requireContext()).scheduleDao(),
                requireContext().applicationContext
            )
        )
    }
    fun getSharedViewModel(): ScheduleViewModel = viewModel

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

        val calendarFragmentAdapter = CalendarFragmentAdapter(this)
        binding.calendarVp.adapter = calendarFragmentAdapter
        binding.calendarVp.isUserInputEnabled = false

        val tabTitles = listOf("List", "Calendar")
        TabLayoutMediator(binding.calendarTabLayout, binding.calendarVp) { tab, position ->
            tab.text = tabTitles[position]
        }.attach()

        binding.calendarVp.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                val mainActivity = requireActivity() as MainActivity
                when (position) {
                    0 -> mainActivity.binding.scheduleEditIv.visibility = View.VISIBLE  // List tab
                    1 -> mainActivity.binding.scheduleEditIv.visibility = View.GONE     // Calendar tab
                }
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

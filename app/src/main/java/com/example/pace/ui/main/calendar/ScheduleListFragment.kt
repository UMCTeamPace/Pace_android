package com.example.pace.ui.main.calendar

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pace.data.db.ScheduleDatabase
import com.example.pace.data.datasource.NormalScheduleRemoteDataSource
import com.example.pace.data.model.Schedule
import com.example.pace.data.repository.ScheduleRepository
import com.example.pace.databinding.FragmentScheduleListBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import androidx.fragment.app.activityViewModels
import com.example.pace.ui.main.MainActivity
import com.example.pace.ui.main.calendar.ScheduleViewModel
import com.example.pace.ui.main.calendar.ScheduleViewModelFactory


class ScheduleListFragment : Fragment() {
    private var _binding: FragmentScheduleListBinding? = null
    private val binding get() = _binding!!

    private lateinit var scheduleAdapter: ScheduleAdapter

    private val viewModel: ScheduleViewModel by lazy {
        (requireActivity() as MainActivity).getSharedViewModel()
    }


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScheduleListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        observeSchedules()
    }

    private fun setupRecyclerView() {
        scheduleAdapter = ScheduleAdapter(emptyList()) { schedule ->
            val updatedSchedule = schedule.copy(isPinned = !schedule.isPinned)
            viewModel.updateSchedule(updatedSchedule)
        }
        binding.scheduleListRv.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = scheduleAdapter
        }
    }

    private fun observeSchedules() {
        viewLifecycleOwner.lifecycleScope.launch {
            // [수정] 뷰모델에서 이미 가공된 scheduleMap을 관찰합니다.
            viewModel.scheduleMap.collectLatest { groupedMap ->
                processAndDisplaySchedules(groupedMap)
            }
        }
    }

    private suspend fun processAndDisplaySchedules(groupedMap: Map<java.time.LocalDate, List<Schedule>>) {
        val items = mutableListOf<ScheduleListItem>()
        val today = java.time.LocalDate.now()

        if (groupedMap.isNotEmpty()) {
            withContext(Dispatchers.Default) {
                // 1. 오늘 이후의 날짜만 필터링하고 정렬된 리스트 생성
                val sortedDates = groupedMap.keys
                    .filter { !it.isBefore(today) } // 오늘 포함 미래 일정만
                    .sorted()

                // 2. 어댑터용 아이템 리스트 생성
                for (date in sortedDates) {
                    val scheduleList = groupedMap[date] ?: continue

                    // 날짜 헤더 추가
                    items.add(ScheduleListItem.DateHeader(formatDateToHeader(date)))

                    // 해당 날짜 내 일정 정렬 (고정 -> 종일 -> 시간순)
                    val sortedList = scheduleList.sortedWith(
                        compareBy(
                            { !it.isPinned },
                            { !it.isAllDay },
                            { it.startTime }
                        )
                    )

                    sortedList.forEach { schedule ->
                        items.add(ScheduleListItem.ScheduleItem(schedule))
                    }
                }
            }
        }

        withContext(Dispatchers.Main) {
            scheduleAdapter.updateData(items)
        }
    }

    // 파라미터를 String이 아닌 LocalDate로 받아 더 안전하게 처리
    private fun formatDateToHeader(date: java.time.LocalDate): String {
        return try {
            val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy년 MM월 dd일 (E)", java.util.Locale.KOREAN)
            date.format(formatter)
        } catch (e: Exception) {
            date.toString()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
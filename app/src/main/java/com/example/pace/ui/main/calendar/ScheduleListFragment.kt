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
            viewModel.allSchedules.collectLatest { schedules ->
                processAndDisplaySchedules(schedules)
            }
        }
    }

    private suspend fun processAndDisplaySchedules(schedules: List<Schedule>) {
        val items = mutableListOf<ScheduleListItem>()
        if (schedules.isNotEmpty()) {
            val today = java.time.LocalDate.now() // [추가] 오늘 날짜 기준

            val expandedSchedules = withContext(Dispatchers.Default) {
                val dateStyleFormatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
                val flattenedList = mutableListOf<Pair<String, Schedule>>()

                schedules.forEach { schedule ->
                    try {
                        val start = java.time.LocalDate.parse(schedule.startDate.substring(0, 10))
                        var end = java.time.LocalDate.parse(schedule.endDate.substring(0, 10))

                        // All-day 종료일 보정 (원본 데이터 훼손 없이 UI 판단용으로만)
                        if (schedule.isAllDay && end.isAfter(start)) {
                            end = end.minusDays(1)
                        }

                        // [수정] 일정의 '종료일'이 오늘보다 전이면 아예 계산에서 제외
                        if (!end.isBefore(today)) {
                            var current = start
                            while (!current.isAfter(end)) {
                                // [추가] 날짜를 펼칠 때도 오늘 이후인 날짜만 리스트에 담음
                                if (!current.isBefore(today)) {
                                    flattenedList.add(current.format(dateStyleFormatter) to schedule)
                                }
                                current = current.plusDays(1)
                            }
                        }
                    } catch (e: Exception) {
                        // 예외 발생 시 안전을 위해 추가하되, 날짜가 오늘 이후인지 체크
                        val dateStr = schedule.startDate.substring(0, 10)
                        if (dateStr >= today.format(dateStyleFormatter)) {
                            flattenedList.add(dateStr to schedule)
                        }
                    }
                }
                flattenedList
            }

            // 2. 날짜별로 그룹화 및 정렬
            val groupedByDate = expandedSchedules
                .groupBy({ it.first }, { it.second })
                .toSortedMap()

            // 3. 어댑터용 아이템 리스트 생성
            for ((date, scheduleList) in groupedByDate) {
                items.add(ScheduleListItem.DateHeader(formatDateToHeader(date)))

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

        withContext(Dispatchers.Main) {
            scheduleAdapter.updateData(items)
        }
    }

    private fun formatDateToHeader(dateStr: String): String {
        return try {
            val date = java.time.LocalDate.parse(dateStr.substring(0, 10))
            val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy년 MM월 dd일 (E)", java.util.Locale.KOREAN)
            date.format(formatter)
        } catch (e: Exception) {
            dateStr
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
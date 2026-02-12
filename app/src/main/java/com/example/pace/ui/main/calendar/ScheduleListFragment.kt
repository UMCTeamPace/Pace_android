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
import androidx.recyclerview.widget.ItemTouchHelper
import com.example.pace.ui.main.MainActivity
import com.example.pace.ui.main.calendar.ScheduleViewModel
import com.example.pace.ui.main.calendar.ScheduleViewModelFactory
import androidx.fragment.app.activityViewModels // 추가
import dagger.hilt.android.AndroidEntryPoint // 추가
import com.example.pace.ui.main.home.ScheduleTouchHelper
@AndroidEntryPoint
class ScheduleListFragment : Fragment() {
    private var _binding: FragmentScheduleListBinding? = null
    private val binding get() = _binding!!

    private lateinit var scheduleAdapter: ScheduleAdapter
    private lateinit var scheduleTouchHelper: ScheduleTouchHelper

    private val viewModel: ScheduleViewModel by activityViewModels()


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
        observeEditMode() // 추가
        setupEditBarButtons() // 추가

    }

    private fun observeEditMode() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isEditMode.collectLatest { isEditMode ->
                val mainActivity = requireActivity() as MainActivity
                val parent = parentFragment as? CalendarFragment

                // 1. 편집 헤더 텍스트 보이기/숨기기
                binding.layoutEditHeader.visibility = if (isEditMode) View.VISIBLE else View.GONE

                if (isEditMode) {
                    // --- 편집 모드 진입 ---
                    binding.layoutEditBar.visibility = View.VISIBLE
                    mainActivity.binding.mainBnv.visibility = View.GONE
                    mainActivity.binding.mainToolbar.visibility = View.GONE
                    parent?.setTabVisibility(false)
                } else {
                    // --- 편집 모드 해제 ---
                    binding.layoutEditBar.visibility = View.GONE
                    mainActivity.binding.mainBnv.visibility = View.VISIBLE
                    mainActivity.binding.mainToolbar.visibility = View.VISIBLE
                    parent?.setTabVisibility(true)
                }

                scheduleAdapter.setEditMode(isEditMode)
            }
        }
    }

    private fun setupEditBarButtons() {
        binding.btnEditCancel.setOnClickListener {
            viewModel.setEditMode(false)
        }
        binding.btnEditDelete.setOnClickListener {
            viewModel.deleteSelected()
        }
    }

    private fun setupRecyclerView() {
        scheduleAdapter = ScheduleAdapter(
            context = requireContext(),
            items = emptyList(),
            onPinClick = { schedule ->
                val updatedSchedule = schedule.copy(isPinned = !schedule.isPinned)
                viewModel.updateSchedule(updatedSchedule)
            },
            onEditSelect = { id ->
                // 아이템 클릭 시 뷰모델의 선택 리스트에 추가/삭제
                viewModel.toggleSelection(id)
            }
        )
        // 스와이프 로직 연결
        scheduleTouchHelper = ScheduleTouchHelper(scheduleAdapter)
        val itemTouchHelper = ItemTouchHelper(scheduleTouchHelper)
        scheduleAdapter.scheduleTouchHelper = scheduleTouchHelper
        itemTouchHelper.attachToRecyclerView(binding.scheduleListRv)

        binding.scheduleListRv.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = scheduleAdapter
        }
    }

    private fun observeSchedules() {
        // 1. 일정 데이터 관찰 (기존 로직)
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.scheduleMap.collectLatest { groupedMap ->
                processAndDisplaySchedules(groupedMap)
            }
        }

        // 3. 선택된 아이템 ID 세트 관찰 (추가)
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selectedIds.collectLatest { ids ->
                scheduleAdapter.updateSelectedIds(ids)
                // 선택된 개수에 따라 삭제 버튼 텍스트 변경 가능 (예: 삭제(3))
                binding.btnEditDelete.text = if (ids.isEmpty()) "삭제" else "삭제(${ids.size})"
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
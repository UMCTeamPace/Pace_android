package com.example.pace.ui.main.calendar

import android.Manifest
import android.content.Intent
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
import com.example.pace.data.viewmodel.ScheduleViewModel
import androidx.fragment.app.activityViewModels // 추가
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.pace.ui.add_schedule.AddScheduleActivity
import com.example.pace.ui.main.home.DeleteRepeatScheduleDialog
import com.example.pace.ui.main.home.DeleteScheduleDialog
import dagger.hilt.android.AndroidEntryPoint // 추가
import com.example.pace.ui.main.home.ScheduleTouchHelper
@AndroidEntryPoint
class ScheduleListFragment : Fragment() {
    private var _binding: FragmentScheduleListBinding? = null
    private val binding get() = _binding!!

    private lateinit var scheduleListAdapter: ScheduleListRVAdapter

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
        setupObservers()
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

                scheduleListAdapter.setEditMode(isEditMode)
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
        scheduleListAdapter = ScheduleListRVAdapter(
            context = requireContext(),
            onPinClick = { schedule ->
                // 💡 생략되었던 고정 로직을 다시 채워넣습니다.
                try {
                    // schedule.startDate 문자열을 LocalDate로 변환 (yyyy-MM-dd 형식)
                    // 만약 문자열 뒤에 시간이 붙어있다면 substring(0, 10)을 사용하세요.
                    val dateStr = schedule.startDate.substring(0, 10)
                    val date = java.time.LocalDate.parse(dateStr)

                    // 뷰모델에 고정 업데이트 요청
                    viewModel.togglePinLocally(date, schedule.id)

                    android.util.Log.d("PinSuccess", "고정 요청 보냄: ${schedule.title} / $date")
                } catch (e: Exception) {
                    android.util.Log.e("PinError", "고정 중 날짜 파싱 에러: ${e.message}")
                }
            },
            onDeleteClick = { schedule -> showDeleteDialog(schedule) },
            onEditClick = { schedule ->
                val intent = Intent(requireContext(), AddScheduleActivity::class.java).apply {
                    putExtra("isEdit", true)
                    putExtra("SCHEDULE_ID", schedule.id)
                    putExtra("SCHEDULE_TYPE", schedule.type)
                    if (schedule.type == "ROUTE") putExtra("OPEN_ROUTE_TAB", true)
                }
                startActivity(intent)
            }
        )

        binding.scheduleListRv.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = scheduleListAdapter
        }
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 1. 일정 데이터 관찰 (HomeFragment의 Observer 로직 참고)
                launch {
                    viewModel.scheduleMap.collect { groupedMap ->
                        // 모든 미래 날짜의 경로 일정에 대해 API 호출 트리거
                        groupedMap.values.flatten()
                            .filter { it.type == "ROUTE" }
                            .forEach { viewModel.fetchRouteDetail(it.id) }

                        processAndDisplaySchedules(groupedMap)
                    }
                }

                // 2. 경로 상세 데이터 관찰 (데이터가 들어오면 리스트 갱신)
                launch {
                    viewModel.routeDetails.collect { _ ->
                        processAndDisplaySchedules(viewModel.scheduleMap.value)
                    }
                }

                // 3. 선택된 아이템 ID 세트 관찰 (편집 모드용)
                launch {
                    viewModel.selectedIds.collect { ids ->
                        scheduleListAdapter.updateSelectedIds(ids)
                        binding.btnEditDelete.text = if (ids.isEmpty()) "삭제" else "삭제(${ids.size})"
                    }
                }
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
            // ViewModel에 저장된 최신 경로 상세 정보(routeDetails)를 함께 전달해야 합니다.
            scheduleListAdapter.updateData(items, viewModel.routeDetails.value)
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


    private fun showDeleteDialog(schedule: Schedule) {
        // 1. 경로 일정 (ROUTE)
        if (schedule.type == "ROUTE") {
            DeleteScheduleDialog(requireContext()).apply {
                setOnConfirmListener {
                    viewModel.deleteSchedule(schedule.id, withRoute = true)
                }
            }.show()
        }
        // 2. 반복 일정 여부 체크
        else if (!schedule.repeatRule.isNullOrEmpty()) {
            DeleteRepeatScheduleDialog(requireContext()).apply {
                setOnOptionSelectedListener { option ->
                    when (option) {
                        "ONLY_THIS" -> {
                            // schedule.startDate는 expandSchedules에 의해 해당 회차 날짜로 이미 채워져 있음
                            val occurrenceDate = java.time.LocalDate.parse(schedule.startDate)
                            viewModel.deleteOnlyThisOccurrence(schedule, occurrenceDate)
                        }
                        "ALL" -> viewModel.deleteSchedule(schedule.id, withRoute = false)
                    }
                }
            }.show()
        }
        // 3. 일반 단일 일정
        else {
            DeleteScheduleDialog(requireContext()).apply {
                setOnConfirmListener {
                    viewModel.deleteSchedule(schedule.id, withRoute = false)
                }
            }.show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
package com.example.pace.ui.main.calendar

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.pace.data.model.Schedule
import com.example.pace.databinding.FragmentSearchBinding
import com.example.pace.databinding.LayoutSearchEmptyBinding
import com.example.pace.ui.main.MainActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.recyclerview.widget.ItemTouchHelper
import com.example.pace.ui.add_schedule.AddScheduleActivity
import com.example.pace.ui.main.home.DeleteRepeatScheduleDialog
import com.example.pace.ui.main.home.DeleteScheduleDialog
import com.example.pace.ui.main.home.ScheduleTouchHelper
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SearchFragment : Fragment() {
    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private lateinit var searchAdapter: SearchAdapter
    private lateinit var scheduleTouchHelper: ScheduleTouchHelper
    private var recyclerView: RecyclerView? = null

    private val viewModel: ScheduleViewModel by lazy {
        (requireActivity() as MainActivity).getSharedViewModel()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupSearchInput()
        setupButtons()
        observeSearchResults()

        showInitialState()

        binding.etSearch.requestFocus()
        showKeyboard()

        // 툴바 숨기기
        (requireActivity() as MainActivity).binding.mainToolbar.visibility = View.GONE
    }

    private fun setupRecyclerView() {
        searchAdapter = SearchAdapter(
            context = requireContext(),
            onPinClick = { schedule ->
                try {
                    // startDate에서 날짜 정보(yyyy-MM-dd) 추출
                    val dateStr = schedule.startDate.substring(0, 10)
                    val date = java.time.LocalDate.parse(dateStr)

                    // 뷰모델의 로컬 핀 토글 함수 호출 (UI 즉시 반영용)
                    viewModel.togglePinLocally(date, schedule.id)
                    searchAdapter.updateItemPinStatus(schedule.id, !schedule.isPinned)
                } catch (e: Exception) {
                    android.util.Log.e("SearchPinError", "날짜 파싱 에러: ${e.message}")
                }
            },
            // 2. 삭제: 프래그먼트에 정의한 showDeleteDialog 호출
            onDeleteClick = { schedule ->
                showDeleteDialog(schedule)
            },
            // 3. 수정: AddScheduleActivity로 이동
            onEditClick = { schedule ->
                val intent = Intent(requireContext(), AddScheduleActivity::class.java).apply {
                    putExtra("isEdit", true)
                    putExtra("SCHEDULE_ID", schedule.id)
                    putExtra("SCHEDULE_TYPE", schedule.type)
                    if (schedule.type == "ROUTE") putExtra("OPEN_ROUTE_TAB", true)
                }
                startActivity(intent)
            },
            // 4. 편집 모드 선택
            onEditSelect = { id ->
                viewModel.toggleSelection(id)
            }
        )

        recyclerView = RecyclerView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            layoutManager = LinearLayoutManager(context)
            adapter = searchAdapter


        }

        // 스와이프 로직 연결
        scheduleTouchHelper = ScheduleTouchHelper(searchAdapter)
        ItemTouchHelper(scheduleTouchHelper).attachToRecyclerView(recyclerView)
        searchAdapter.scheduleTouchHelper = scheduleTouchHelper
    }

    private fun observeSearchResults() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // 1. 검색 결과 및 편집 모드/선택 상태 통합 관찰
                launch {
                    viewModel.searchResults.collectLatest { results ->
                        val query = binding.etSearch.text.toString().trim()

                        // 어댑터에 검색어를 먼저 전달 (하이라이트용)
                        searchAdapter.updateQuery(query)

                        if (results.isEmpty()) {
                            if (query.isEmpty()) showInitialState() else showEmptyState()
                        } else {
                            val uiItems = transformToSearchItems(results)

                            // [수정] removeAllViews 대신 어댑터 업데이트만 수행
                            binding.searchResultContainer.visibility = View.VISIBLE
                            if (recyclerView?.parent == null) {
                                binding.searchResultContainer.addView(recyclerView)
                            }

                            searchAdapter.submitList(uiItems)

                            results.filter { it.type == "ROUTE" }.forEach {
                                viewModel.fetchRouteDetail(it.id)
                            }
                        }
                    }
                }

                // 2. 선택된 ID 세트 관찰 (편집 모드 체크박스 즉시 반영)
                launch {
                    viewModel.selectedIds.collect { ids ->
                        searchAdapter.updateSelectedIds(ids)
                    }
                }

                // 3. 경로 상세 정보 관찰
                launch {
                    viewModel.routeDetails.collectLatest { routeMap ->
                        searchAdapter.updateRouteInfo(routeMap)
                    }
                }
            }
        }
    }


    private fun showResultList(results: List<ScheduleListItem>) {
        binding.searchResultContainer.removeAllViews()
        recyclerView?.let { rv ->
            if (rv.parent == null) {
                binding.searchResultContainer.addView(rv)
            } else {
                binding.searchResultContainer.addView(rv)
            }
            searchAdapter.submitList(results)
            recyclerView?.scrollToPosition(0)
        }
    }

    private fun setupSearchInput() {
        binding.etSearch.addTextChangedListener { text ->
            val query = text?.toString()?.trim() ?: ""
            // 어댑터 쿼리 업데이트는 위 observe 로직에서 처리하므로 삭제 가능

            if (query.isEmpty()) {
                viewModel.clearSearch()
                showInitialState()
            } else {
                // 뷰모델에서 검색 수행 (검색 결과 flow가 방출됨)
                viewModel.searchSchedules(query)
            }
        }
    }

    private fun setupButtons() {
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.ivList.setOnClickListener {
            SearchFilterBottomSheet().show(childFragmentManager, "filter")
        }
    }

    private fun showInitialState() {
        binding.searchResultContainer.removeAllViews()
        searchAdapter.submitList(emptyList())
        searchAdapter.updateQuery("")
    }

    private fun showEmptyState() {
        binding.searchResultContainer.removeAllViews()
        val emptyBinding = LayoutSearchEmptyBinding.inflate(layoutInflater, binding.searchResultContainer, true)

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.searchRangeText.collect { range ->
                emptyBinding.tvEmptyRange.text = range
            }
        }

        emptyBinding.btnExpandSearch.setOnClickListener {
            viewModel.expandSearchRange()
        }
    }

    private fun showKeyboard() {
        binding.etSearch.postDelayed({
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT)
        }, 100)
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

    private fun transformToSearchItems(schedules: List<Schedule>): List<ScheduleListItem> {
        val resultList = mutableListOf<ScheduleListItem>()

        // 1. 날짜별 그룹화 (문자열 처리를 통해 yyyy-MM-dd 형태 추출)
        val grouped = schedules.groupBy { it.startDate.substring(0, 10) }

        // 2. 날짜순 정렬
        val sortedDates = grouped.keys.sorted()

        for (dateStr in sortedDates) {
            val date = java.time.LocalDate.parse(dateStr)

            // 리스트 프래그먼트와 동일한 날짜 헤더 추가 (yyyy년 MM월 dd일 (E))
            resultList.add(ScheduleListItem.DateHeader(formatDateToHeader(date)))

            grouped[dateStr]?.let { daySchedules ->
                // 3. 해당 날짜 내 정렬 (리스트 화면과 동일: 고정 -> 종일 -> 시간순)
                val sortedList = daySchedules.sortedWith(
                    compareBy(
                        { !it.isPinned },
                        { !it.isAllDay },
                        { it.startTime }
                    )
                )
                resultList.addAll(sortedList.map { ScheduleListItem.ScheduleItem(it) })
            }
        }
        return resultList
    }

    // 리스트 프래그먼트와 동일한 포맷 함수 추가
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
        (requireActivity() as MainActivity).binding.mainToolbar.visibility = View.VISIBLE
        _binding = null
    }
}
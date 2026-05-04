package com.example.pace.ui.main.calendar

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.pace.data.model.Schedule
import com.example.pace.data.viewmodel.ScheduleViewModel
import com.example.pace.databinding.FragmentSearchBinding
import com.example.pace.databinding.LayoutSearchEmptyBinding
import com.example.pace.ui.add_schedule.AddScheduleActivity
import com.example.pace.ui.main.MainActivity
import com.example.pace.ui.main.home.DeleteRepeatScheduleDialog
import com.example.pace.ui.main.home.DeleteScheduleDialog
import com.example.pace.util.ScheduleSortUtils
import com.example.pace.util.SearchTextMatcher
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class SearchFragment : Fragment() {
    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private lateinit var searchAdapter: SearchAdapter
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
        setupOnBackPressed()
        setupKeyboardDismissOnTouch()
        observeSearchResults()

        showInitialState()

        binding.etSearch.requestFocus()
        showKeyboard()
    }

    private fun setupOnBackPressed() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    closeSearchScreen()
                }
            }
        )
    }

    private fun setupRecyclerView() {
        searchAdapter = SearchAdapter(
            context = requireContext(),
            onPinClick = { schedule ->
                try {
                    val dateStr = schedule.startDate.substring(0, 10)
                    val date = java.time.LocalDate.parse(dateStr)
                    viewModel.togglePinLocally(date, schedule.id, schedule.startDate)
                    searchAdapter.updateItemPinStatus(schedule.id, schedule.startDate, !schedule.isPinned)
                } catch (e: Exception) {
                    android.util.Log.e("SearchPinError", "날짜 파싱 에러: ${e.message}")
                }
            },
            onDeleteClick = { schedule ->
                showDeleteDialog(schedule)
            },
            onEditClick = { schedule ->
                val intent = Intent(requireContext(), AddScheduleActivity::class.java).apply {
                    putExtra("isEdit", true)
                    putExtra("SCHEDULE_ID", schedule.id)
                    putExtra("OCCURRENCE_DATE", schedule.startDate)
                    putExtra("SCHEDULE_TYPE", schedule.type)
                    if (schedule.type == "ROUTE") putExtra("OPEN_ROUTE_TAB", true)
                }
                startActivity(intent)
            },
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
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    binding.etSearch.clearFocus()
                    hideKeyboard()
                }
                false
            }
        }
    }

    private fun observeSearchResults() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.searchResults.collectLatest { results ->
                        val query = binding.etSearch.text.toString().trim()
                        searchAdapter.updateQuery(query)

                        if (query.isEmpty()) {
                            showInitialState()
                        } else if (results.isEmpty()) {
                            showEmptyState()
                        } else {
                            val uiItems = transformToSearchItems(results)
                            showSearchResults(uiItems)

                            results.filter { it.type == "ROUTE" }.forEach {
                                viewModel.fetchRouteDetail(it.id)
                            }
                        }
                    }
                }

                launch {
                    viewModel.selectedIds.collect { ids ->
                        searchAdapter.updateSelectedIds(ids)
                    }
                }

                launch {
                    viewModel.routeDetails.collectLatest { routeMap ->
                        searchAdapter.updateRouteInfo(routeMap)
                    }
                }
            }
        }
    }

    private fun setupSearchInput() {
        binding.etSearch.addTextChangedListener { text ->
            val query = text?.toString()?.trim() ?: ""
            if (query.isEmpty()) {
                viewModel.clearSearch()
                showInitialState()
            } else {
                viewModel.searchSchedules(query)
            }
        }
    }

    private fun setupButtons() {
        binding.btnBack.setOnClickListener {
            closeSearchScreen()
        }

        binding.ivList.setOnClickListener {
            SearchFilterBottomSheet().show(childFragmentManager, "filter")
        }
    }

    private fun setupKeyboardDismissOnTouch() {
        binding.root.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                binding.etSearch.clearFocus()
                hideKeyboard()
            }
            false
        }

        binding.searchResultContainer.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                binding.etSearch.clearFocus()
                hideKeyboard()
            }
            false
        }
    }

    private fun showInitialState() {
        binding.searchResultContainer.removeAllViews()
        binding.searchResultContainer.visibility = View.GONE
        searchAdapter.submitList(emptyList())
        searchAdapter.updateQuery("")
    }

    private fun showEmptyState() {
        binding.searchResultContainer.removeAllViews()
        binding.searchResultContainer.visibility = View.VISIBLE
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

    private fun showSearchResults(items: List<ScheduleListItem>) {
        binding.searchResultContainer.removeAllViews()
        binding.searchResultContainer.visibility = View.VISIBLE
        recyclerView?.let { binding.searchResultContainer.addView(it) }
        searchAdapter.submitList(items)
    }

    private fun showKeyboard() {
        binding.etSearch.postDelayed({
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT)
        }, 100)
    }

    private fun hideKeyboard() {
        if (_binding == null) return
        binding.etSearch.clearFocus()
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
    }

    private fun closeSearchScreen() {
        hideKeyboard()
        parentFragmentManager.popBackStack()
        (requireActivity() as MainActivity).hideOverlayContainerIfEmpty()
    }

    private fun showDeleteDialog(schedule: Schedule) {
        if (schedule.type == "ROUTE") {
            DeleteScheduleDialog(requireContext()).apply {
                setOnConfirmListener {
                    viewModel.deleteSchedule(schedule.id, withRoute = true)
                }
            }.show()
        } else if (!schedule.repeatRule.isNullOrEmpty()) {
            DeleteRepeatScheduleDialog(requireContext()).apply {
                setOnOptionSelectedListener { option ->
                    when (option) {
                        "ONLY_THIS" -> {
                            val occurrenceDate = java.time.LocalDate.parse(schedule.startDate)
                            viewModel.deleteOnlyThisOccurrence(schedule, occurrenceDate)
                        }

                        "ALL" -> viewModel.deleteSchedule(schedule.id, withRoute = false)
                    }
                }
            }.show()
        } else {
            DeleteScheduleDialog(requireContext()).apply {
                setOnConfirmListener {
                    viewModel.deleteSchedule(schedule.id, withRoute = false)
                }
            }.show()
        }
    }

    private fun transformToSearchItems(schedules: List<Schedule>): List<ScheduleListItem> {
        val resultList = mutableListOf<ScheduleListItem>()
        val grouped = schedules.groupBy { it.startDate.substring(0, 10) }
        val sortedDates = grouped.keys.sorted()

        for (dateStr in sortedDates) {
            val date = java.time.LocalDate.parse(dateStr)
            resultList.add(ScheduleListItem.DateHeader(formatDateToHeader(date)))

            grouped[dateStr]?.let { daySchedules ->
                val sortedList = daySchedules.sortedWith(ScheduleSortUtils.displayComparator())
                resultList.addAll(sortedList.map { ScheduleListItem.ScheduleItem(it) })
            }
        }
        return resultList
    }

    private fun formatDateToHeader(date: java.time.LocalDate): String {
        return try {
            val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy년 MM월 dd일(E)", java.util.Locale.KOREAN)
            date.format(formatter)
        } catch (e: Exception) {
            date.toString()
        }
    }

    override fun onDestroyView() {
        hideKeyboard()
        super.onDestroyView()
        (requireActivity() as MainActivity).hideOverlayContainerIfEmpty()
        _binding = null
    }
}

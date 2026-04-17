package com.example.pace.ui.main.calendar

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.pace.data.model.Schedule
import com.example.pace.data.viewmodel.ScheduleViewModel
import com.example.pace.databinding.FragmentScheduleListBinding
import com.example.pace.ui.add_schedule.AddScheduleActivity
import com.example.pace.ui.main.MainActivity
import com.example.pace.ui.main.calendar.SearchFragment
import com.example.pace.ui.main.home.DeleteRepeatScheduleDialog
import com.example.pace.ui.main.home.DeleteScheduleDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@AndroidEntryPoint
class ScheduleListFragment : Fragment() {
    private var _binding: FragmentScheduleListBinding? = null
    private val binding get() = _binding!!

    private lateinit var scheduleListAdapter: ScheduleListRVAdapter
    private val viewModel: ScheduleViewModel by activityViewModels()
    private var hasScrolledToToday = false
    private var pendingResetToToday = false
    private var pendingFocusDate: LocalDate? = null

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
        setupOnBackPressed()
        setupRecyclerView()
        setupObservers()
        observeEditMode()
        setupEditBarButtons()
        applyPendingResetIfNeeded()
    }

    private fun setupOnBackPressed() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (viewModel.isEditMode.value) {
                        viewModel.setEditMode(false)
                        return
                    }

                    isEnabled = false
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        )
    }

    private fun observeEditMode() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isEditMode.collectLatest { isEditMode ->
                val mainActivity = requireActivity() as MainActivity
                val parent = parentFragment as? CalendarFragment
                val currentMainFragment = requireActivity()
                    .supportFragmentManager
                    .findFragmentById(com.example.pace.R.id.main_fcv)

                binding.layoutEditHeader.visibility = if (isEditMode) View.VISIBLE else View.GONE

                if (isEditMode) {
                    binding.layoutEditBar.visibility = View.VISIBLE
                    mainActivity.binding.mainBnv.visibility = View.GONE
                    mainActivity.binding.mainToolbar.visibility = View.GONE
                    parent?.setTabVisibility(false)
                } else {
                    binding.layoutEditBar.visibility = View.GONE
                    mainActivity.binding.mainBnv.visibility = View.VISIBLE
                    if (currentMainFragment !is SearchFragment) {
                        mainActivity.binding.mainToolbar.visibility = View.VISIBLE
                    }
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
            showBulkDeleteDialog()
        }
    }

    private fun showBulkDeleteDialog() {
        val selectedSchedules =
            scheduleListAdapter.getSelectedSchedules(viewModel.selectedOccurrenceKeys.value)
        if (selectedSchedules.isEmpty()) return

        val repeatingSchedules = selectedSchedules.filter {
            it.type != "ROUTE" && !it.repeatRule.isNullOrEmpty()
        }
        val singleSchedules = selectedSchedules.filterNot {
            it.type != "ROUTE" && !it.repeatRule.isNullOrEmpty()
        }

        if (repeatingSchedules.isNotEmpty()) {
            DeleteRepeatScheduleDialog(requireContext()).apply {
                setOnOptionSelectedListener { option ->
                    deleteSelectedSchedules(repeatingSchedules, option)

                    if (singleSchedules.isNotEmpty()) {
                        showSingleDeleteDialog(singleSchedules)
                    } else {
                        viewModel.setEditMode(false)
                    }
                }
            }.show()
            return
        }

        showSingleDeleteDialog(singleSchedules)
    }

    private fun showSingleDeleteDialog(singleSchedules: List<Schedule>) {
        if (singleSchedules.isEmpty()) return

        DeleteScheduleDialog(requireContext()).apply {
            setOnConfirmListener {
                deleteSelectedSchedules(singleSchedules, null)
                viewModel.setEditMode(false)
            }
        }.show()
    }

    private fun deleteSelectedSchedules(selectedSchedules: List<Schedule>, repeatOption: String?) {
        selectedSchedules.forEach { schedule ->
            when {
                schedule.type == "ROUTE" -> {
                    viewModel.deleteSchedule(schedule.id, withRoute = true)
                }

                !schedule.repeatRule.isNullOrEmpty() && repeatOption == "ONLY_THIS" -> {
                    val occurrenceDate = LocalDate.parse(schedule.startDate)
                    viewModel.deleteOnlyThisOccurrence(schedule, occurrenceDate)
                }

                else -> {
                    viewModel.deleteSchedule(schedule.id, withRoute = false)
                }
            }
        }
    }

    private fun setupRecyclerView() {
        scheduleListAdapter = ScheduleListRVAdapter(
            context = requireContext(),
            onPinClick = { schedule ->
                try {
                    val date = LocalDate.parse(schedule.startDate.substring(0, 10))
                    viewModel.togglePinLocally(date, schedule.id, schedule.startDate)
                } catch (_: Exception) {
                }
            },
            onDeleteClick = { schedule -> showDeleteDialog(schedule) },
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
            onSelectionToggle = { schedule ->
                viewModel.toggleOccurrenceSelection(schedule.id, schedule.startDate)
            },
            onItemClick = { schedule ->
                openScheduleDetail(schedule)
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
                launch {
                    viewModel.scheduleMap.collectLatest { groupedMap ->
                        groupedMap.values.flatten()
                            .filter { it.type == "ROUTE" }
                            .forEach { viewModel.fetchRouteDetail(it.id) }

                        processAndDisplaySchedules(groupedMap)
                    }
                }

                launch {
                    viewModel.routeDetails.collectLatest {
                        scheduleListAdapter.updateRouteMap(it)
                    }
                }

                launch {
                    viewModel.selectedOccurrenceKeys.collect { keys ->
                        scheduleListAdapter.updateSelectedKeys(keys)
                        binding.btnEditDelete.text =
                            if (keys.isEmpty()) "삭제" else "${keys.size}개 삭제"
                        binding.btnEditDelete.isEnabled = keys.isNotEmpty()
                        binding.btnEditDelete.alpha = if (keys.isEmpty()) 0.45f else 1f
                    }
                }
            }
        }
    }

    private suspend fun processAndDisplaySchedules(
        groupedMap: Map<LocalDate, List<Schedule>>,
        targetDate: LocalDate = pendingFocusDate ?: viewModel.selectedDate.value
    ) {
        val items = mutableListOf<ScheduleListItem>()
        var targetPosition: Int? = null

        if (groupedMap.isNotEmpty()) {
            withContext(Dispatchers.Default) {
                val sortedDates = groupedMap.keys
                    .sorted()

                for (date in sortedDates) {
                    val scheduleList = groupedMap[date] ?: continue
                    if (targetPosition == null && !date.isBefore(targetDate)) {
                        targetPosition = items.size
                    }
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
        }

        scheduleListAdapter.updateData(items, viewModel.routeDetails.value)
        scrollToTodayPositionIfNeeded(targetPosition)
        pendingFocusDate = null
    }

    private fun scrollToTodayPositionIfNeeded(todayPosition: Int?) {
        if (hasScrolledToToday) return

        val layoutManager = binding.scheduleListRv.layoutManager as? LinearLayoutManager ?: return
        val targetPosition = todayPosition ?: return

        binding.scheduleListRv.post {
            if (!isAdded || _binding == null || hasScrolledToToday) return@post
            layoutManager.scrollToPositionWithOffset(targetPosition, 0)
            hasScrolledToToday = true
        }
    }

    fun resetToToday() {
        focusOnDate(LocalDate.now())
    }

    fun focusOnDate(date: LocalDate) {
        if (_binding == null) {
            pendingFocusDate = date
            pendingResetToToday = true
            return
        }
        pendingResetToToday = false
        pendingFocusDate = date
        hasScrolledToToday = false
        viewModel.setSelectedDate(date)
        viewLifecycleOwner.lifecycleScope.launch {
            processAndDisplaySchedules(viewModel.scheduleMap.value, date)
        }
    }

    private fun openScheduleDetail(schedule: Schedule) {
        (parentFragment as? CalendarFragment)?.openScheduleDetail(
            scheduleId = schedule.id,
            occurrenceDate = schedule.startDate,
            scheduleType = schedule.type
        )
    }

    private fun applyPendingResetIfNeeded() {
        if (!pendingResetToToday || _binding == null) return
        focusOnDate(pendingFocusDate ?: LocalDate.now())
    }

    private fun formatDateToHeader(date: LocalDate): String {
        return try {
            val formatter = DateTimeFormatter.ofPattern("yyyy년 MM월 dd일 (E)", Locale.KOREAN)
            date.format(formatter)
        } catch (e: Exception) {
            date.toString()
        }
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
                            val occurrenceDate = LocalDate.parse(schedule.startDate)
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

    override fun onDestroyView() {
        super.onDestroyView()
        hasScrolledToToday = false
        _binding = null
    }
}

package com.example.pace.ui.add_schedule

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
import com.example.pace.data.model.Schedule
import com.example.pace.data.viewmodel.RouteViewModel
import com.example.pace.data.viewmodel.ScheduleViewModel
import com.example.pace.databinding.FragmentRouteScheduleListBinding
import com.example.pace.ui.main.calendar.ScheduleListItem
import com.example.pace.ui.main.calendar.ScheduleListRVAdapter
import com.example.pace.ui.main.home.DeleteScheduleDialog
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class RouteScheduleListFragment : Fragment() {

    private var _binding: FragmentRouteScheduleListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ScheduleViewModel by activityViewModels()
    private val routeViewModel: RouteViewModel by activityViewModels()
    private lateinit var adapter: ScheduleListRVAdapter
    private var isEditMode = false
    private var selectedKeys = linkedSetOf<String>()
    private var currentRouteSchedules: List<Schedule> = emptyList()
    private var latestGroupedMap: Map<LocalDate, List<Schedule>> = emptyMap()
    private val pendingDeletedRouteIds = mutableSetOf<Long>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRouteScheduleListBinding.inflate(inflater, container, false)
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
        binding.ivBack.setOnClickListener {
            if (isEditMode) {
                exitEditMode()
            } else {
                parentFragmentManager.popBackStack()
            }
        }
        binding.ivEdit.setOnClickListener {
            if (isEditMode) return@setOnClickListener
            enterEditMode()
        }
        binding.headerSelectCheckbox.setOnClickListener {
            if (!isEditMode) return@setOnClickListener
            toggleSelectAll()
        }
        binding.btnEditCancel.setOnClickListener {
            exitEditMode()
        }
        binding.btnEditDelete.setOnClickListener {
            RouteScheduleBulkDeleteDialog(requireContext(), selectedKeys.size) {
                deleteSelectedSchedules()
            }.show()
        }
        setupRecyclerView()
        observeRouteSchedules()
    }

    private fun setupRecyclerView() {
        adapter = ScheduleListRVAdapter(
            context = requireContext(),
            onPinClick = { schedule ->
                runCatching {
                    val date = LocalDate.parse(schedule.startDate.substring(0, 10))
                    viewModel.togglePinLocally(date, schedule.id)
                }
            },
            onDeleteClick = { schedule ->
                DeleteScheduleDialog(requireContext()).apply {
                    setOnConfirmListener {
                        pendingDeletedRouteIds.add(schedule.id)
                        routeViewModel.removeRouteScheduleLocally(schedule.id)
                        updateRouteScheduleList(latestGroupedMap)
                        viewModel.deleteSchedule(schedule.id, withRoute = true)
                    }
                }.show()
            },
            onEditClick = { schedule ->
                val intent = Intent(requireContext(), AddScheduleActivity::class.java).apply {
                    putExtra("isEdit", true)
                    putExtra("SCHEDULE_ID", schedule.id)
                    putExtra("OCCURRENCE_DATE", schedule.startDate)
                    putExtra("SCHEDULE_TYPE", schedule.type)
                    putExtra("OPEN_ROUTE_TAB", true)
                }
                startActivity(intent)
            },
            onSelectionToggle = { schedule ->
                toggleSelection(schedule)
            },
            onItemClick = {}
        )

        binding.routeScheduleListRv.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@RouteScheduleListFragment.adapter
        }
    }

    private fun observeRouteSchedules() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.scheduleMap.collectLatest { groupedMap ->
                        latestGroupedMap = groupedMap
                        updateRouteScheduleList(groupedMap)
                    }
                }

                launch {
                    viewModel.routeDetails.collectLatest { routeMap ->
                        adapter.updateRouteMap(routeMap)
                    }
                }
            }
        }
    }

    private fun updateRouteScheduleList(groupedMap: Map<LocalDate, List<Schedule>>) {
        currentRouteSchedules = groupedMap.values
            .flatten()
            .filter { it.type == "ROUTE" && it.id !in pendingDeletedRouteIds }
        val routeItems = buildRouteItems(groupedMap)
        adapter.updateData(routeItems, viewModel.routeDetails.value)
        syncSelectionState()
    }

    private fun buildRouteItems(groupedMap: Map<LocalDate, List<com.example.pace.data.model.Schedule>>): List<ScheduleListItem> {
        val items = mutableListOf<ScheduleListItem>()
        val sortedDates = groupedMap.keys.sorted()

        sortedDates.forEach { date ->
            val routeSchedules = groupedMap[date]
                ?.filter { it.type == "ROUTE" && it.id !in pendingDeletedRouteIds }
                ?.sortedWith(
                    compareBy(
                        { !it.isPinned },
                        { it.startTime }
                    )
                )
                .orEmpty()

            if (routeSchedules.isEmpty()) return@forEach

            items.add(ScheduleListItem.DateHeader(formatDateToHeader(date)))
            routeSchedules.forEach { schedule ->
                items.add(ScheduleListItem.ScheduleItem(schedule))
            }
        }

        return items
    }

    private fun formatDateToHeader(date: LocalDate): String {
        return runCatching {
            date.format(DateTimeFormatter.ofPattern("yyyy년 MM월 dd일 (E)", Locale.KOREAN))
        }.getOrElse { date.toString() }
    }

    private fun enterEditMode() {
        isEditMode = true
        selectedKeys.clear()
        binding.layoutEditBar.visibility = View.VISIBLE
        binding.ivBack.visibility = View.GONE
        binding.tvHeaderTitle.visibility = View.GONE
        binding.ivEdit.visibility = View.GONE
        binding.layoutEditHeaderContent.visibility = View.VISIBLE
        adapter.setEditMode(true)
        adapter.updateSelectedKeys(selectedKeys)
        updateHeaderTitle()
    }

    private fun exitEditMode() {
        isEditMode = false
        selectedKeys.clear()
        binding.layoutEditBar.visibility = View.GONE
        binding.ivBack.visibility = View.VISIBLE
        binding.tvHeaderTitle.visibility = View.VISIBLE
        binding.ivEdit.visibility = View.VISIBLE
        binding.layoutEditHeaderContent.visibility = View.GONE
        adapter.setEditMode(false)
        adapter.updateSelectedKeys(selectedKeys)
        updateHeaderTitle()
    }

    private fun toggleSelection(schedule: Schedule) {
        if (!isEditMode) return

        val key = selectionKey(schedule)
        if (!selectedKeys.add(key)) {
            selectedKeys.remove(key)
        }
        adapter.updateSelectedKeys(selectedKeys)
        updateHeaderTitle()
    }

    private fun syncSelectionState() {
        if (!isEditMode) return

        val validKeys = currentRouteSchedules.mapTo(mutableSetOf()) { selectionKey(it) }
        selectedKeys = selectedKeys.filterTo(linkedSetOf()) { it in validKeys }
        adapter.updateSelectedKeys(selectedKeys)
        updateHeaderTitle()
    }

    private fun updateHeaderTitle() {
        binding.tvHeaderTitle.text = "일정 목록"
        binding.tvEditCount.text = if (
            currentRouteSchedules.isNotEmpty() &&
            selectedKeys.size == currentRouteSchedules.size
        ) {
            "전체 선택"
        } else {
            "${selectedKeys.size}개 선택됨"
        }
        binding.headerSelectCheckbox.isChecked =
            currentRouteSchedules.isNotEmpty() && selectedKeys.size == currentRouteSchedules.size
        binding.btnEditDelete.text = "삭제"
        binding.btnEditDelete.isEnabled = selectedKeys.isNotEmpty()
        binding.btnEditDelete.alpha = if (selectedKeys.isEmpty()) 0.45f else 1f
    }

    private fun selectionKey(schedule: Schedule): String = "${schedule.id}|${schedule.startDate}"

    private fun toggleSelectAll() {
        val shouldSelectAll = selectedKeys.size != currentRouteSchedules.size
        selectedKeys = if (shouldSelectAll) {
            currentRouteSchedules.mapTo(linkedSetOf()) { selectionKey(it) }
        } else {
            linkedSetOf()
        }
        adapter.updateSelectedKeys(selectedKeys)
        updateHeaderTitle()
    }

    private fun deleteSelectedSchedules() {
        if (selectedKeys.isEmpty()) return

        currentRouteSchedules
            .filter { selectionKey(it) in selectedKeys }
            .forEach { schedule ->
                pendingDeletedRouteIds.add(schedule.id)
                routeViewModel.removeRouteScheduleLocally(schedule.id)
                viewModel.deleteSchedule(schedule.id, withRoute = true)
            }

        updateRouteScheduleList(latestGroupedMap)
        exitEditMode()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

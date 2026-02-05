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
            val sortedSchedules = schedules.sortedWith(
                compareBy({ it.startDate }, { !it.isPinned }, { it.startTime })
            )

            val groupedByDate = sortedSchedules.groupBy { it.startDate }

            for ((date, scheduleList) in groupedByDate) {
                items.add(ScheduleListItem.DateHeader(formatDateToHeader(date)))
                scheduleList.forEach { schedule ->
                    items.add(ScheduleListItem.ScheduleItem(schedule))
                }
            }
        }

        withContext(Dispatchers.Main) {
            scheduleAdapter.updateData(items)
        }
    }
    
    private fun formatDateToHeader(dateStr: String): String {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val formatter = SimpleDateFormat("yyyy년 MM월 dd일 (E)", Locale.KOREAN)
        val date = parser.parse(dateStr)
        return formatter.format(date)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
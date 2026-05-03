package com.example.pace.ui.main.calendar

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.pace.R
import com.example.pace.data.model.Schedule
import com.example.pace.data.model.response.ReminderInfo
import com.example.pace.data.model.response.RouteInfo
import com.example.pace.data.viewmodel.ScheduleViewModel
import com.example.pace.databinding.FragmentScheduleDetailBinding
import com.example.pace.databinding.ItemRouteDetailBriefBinding
import com.example.pace.databinding.ItemRouteVehicleBinding
import com.example.pace.ui.RouteCalculator
import com.example.pace.ui.add_schedule.AddScheduleActivity
import com.example.pace.ui.main.MainActivity
import com.example.pace.ui.main.home.DeleteRepeatScheduleDialog
import com.example.pace.ui.main.home.DeleteScheduleDialog
import com.google.gson.Gson
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@AndroidEntryPoint
class ScheduleDetailFragment : Fragment() {
    private var _binding: FragmentScheduleDetailBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ScheduleViewModel by activityViewModels()

    private var currentScheduleId: Long = -1L
    private var currentOccurrenceDate: String = ""
    private var currentScheduleType: String = ""

    private var currentSchedule: Schedule? = null
    private val gson = Gson()
    private val editScheduleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data ?: return@registerForActivityResult
        val updatedScheduleId = data.getLongExtra("UPDATED_SCHEDULE_ID", -1L)
        if (updatedScheduleId == -1L) return@registerForActivityResult

        currentScheduleId = updatedScheduleId
        currentOccurrenceDate = data.getStringExtra("UPDATED_OCCURRENCE_DATE").orEmpty()
        currentScheduleType = data.getStringExtra("UPDATED_SCHEDULE_TYPE").orEmpty()
        loadSchedule()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentScheduleId = requireArguments().getLong(ARG_SCHEDULE_ID)
        currentOccurrenceDate = requireArguments().getString(ARG_OCCURRENCE_DATE).orEmpty()
        currentScheduleType = requireArguments().getString(ARG_SCHEDULE_TYPE).orEmpty()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentScheduleDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupClicks()
        observeScheduleUpdates()
        observeRouteDetail()
        loadSchedule()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) loadSchedule()
    }

    private fun setupClicks() {
        binding.btnBack.setOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }

        binding.btnEdit.setOnClickListener {
            val schedule = currentSchedule ?: return@setOnClickListener
            val intent = Intent(requireContext(), AddScheduleActivity::class.java).apply {
                putExtra("isEdit", true)
                putExtra("SCHEDULE_ID", schedule.id)
                putExtra("OCCURRENCE_DATE", currentOccurrenceDate)
                putExtra("SCHEDULE_TYPE", schedule.type)
                if (schedule.type == "ROUTE") putExtra("OPEN_ROUTE_TAB", true)
            }
            editScheduleLauncher.launch(intent)
        }

        binding.btnDelete.setOnClickListener {
            currentSchedule?.let(::showDeleteDialog)
        }
    }

    private fun observeRouteDetail() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.scheduleDetailInfoMap.collect { detailMap ->
                    val schedule = currentSchedule ?: return@collect
                    if (schedule.type != "ROUTE") return@collect
                    detailMap[schedule.id]?.let { detail ->
                        bindRouteSpecificInfo(schedule, detail.route, detail.reminders)
                    }
                }
            }
        }
    }

    private fun observeScheduleUpdates() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.allSchedules.collect { schedules ->
                    val updatedSchedule = schedules.firstOrNull { it.id == currentScheduleId } ?: return@collect
                    currentSchedule = updatedSchedule
                    val cachedDetail = if (updatedSchedule.type == "ROUTE" || currentScheduleType == "ROUTE") {
                        viewModel.scheduleDetailInfoMap.value[updatedSchedule.id]
                    } else {
                        null
                    }
                    bindSchedule(updatedSchedule, cachedDetail)
                }
            }
        }
    }

    private fun loadSchedule() {
        viewLifecycleOwner.lifecycleScope.launch {
            val schedule = viewModel.getScheduleById(currentScheduleId) ?: return@launch
            currentSchedule = schedule
            val cachedDetail = if (schedule.type == "ROUTE" || currentScheduleType == "ROUTE") {
                viewModel.scheduleDetailInfoMap.value[schedule.id]
            } else {
                null
            }
            bindSchedule(schedule, cachedDetail)
            if (schedule.type == "ROUTE" || currentScheduleType == "ROUTE") {
                viewModel.getScheduleDetail(schedule.id)
            }
        }
    }

    private fun bindSchedule(
        schedule: Schedule,
        routeDetail: com.example.pace.data.model.response.ScheduleDetailResponse? = null
    ) {
        val isRouteSchedule = schedule.type == "ROUTE"
        val color = schedule.eventColor
            ?: schedule.calendarColor
            ?: requireContext().getColor(R.color.schedule_18)
        binding.viewScheduleColor.backgroundTintList = ColorStateList.valueOf(color)
        binding.tvScheduleTitle.text = schedule.title ?: "제목 없음"

        val displayStartDate = resolveDisplayStartDate(schedule)
        val displayEndDate = resolveDisplayEndDate(schedule, displayStartDate)
        binding.tvStartDate.text = formatDate(displayStartDate)
        binding.tvEndDate.text = formatDate(displayEndDate)

        if (isRouteSchedule) {
            binding.tvAllDayLabel.text = "일정 시간"
            binding.addscheMyPhoneIv.visibility = View.GONE
            binding.tvStartTime.visibility = View.VISIBLE
            binding.tvEndTime.visibility = View.VISIBLE
            binding.layoutRepeatInfo.visibility = View.GONE
            bindBestEffortRouteReminderTexts(schedule, routeDetail)
        } else {
            binding.tvAllDayLabel.text = "하루 종일"
            binding.addscheMyPhoneIv.visibility = View.VISIBLE
            binding.addscheMyPhoneIv.setImageResource(
                if (schedule.isAllDay) R.drawable.ic_toggle_selected
                else R.drawable.ic_toggle_unselected
            )

            if (schedule.isAllDay) {
                binding.tvStartTime.visibility = View.GONE
                binding.tvEndTime.visibility = View.GONE
            } else {
                binding.tvStartTime.visibility = View.VISIBLE
                binding.tvEndTime.visibility = View.VISIBLE
            }

            binding.layoutRepeatInfo.visibility = View.VISIBLE
            binding.tvRepeatRule.text = if (schedule.repeatRule.isNullOrEmpty()) {
                "반복 없음"
            } else {
                buildRepeatText(schedule)
            }
            bindNormalReminderText(formatReminderText(schedule.reminders))
        }

        binding.tvStartTime.text = schedule.startTime
        binding.tvEndTime.text = schedule.endTime

        val storedCalendarName = schedule.calendarDisplayName?.takeIf { it.isNotBlank() }
        val resolvedCalendarName = runCatching { viewModel.getCalendarNameById(schedule.calendarId) }.getOrNull()
        binding.tvRemindStatus.text = when {
            !resolvedCalendarName.isNullOrBlank() && resolvedCalendarName != "기본 일정" -> resolvedCalendarName
            !storedCalendarName.isNullOrBlank() -> storedCalendarName
            !resolvedCalendarName.isNullOrBlank() -> resolvedCalendarName
            else -> "기본 일정"
        }

        bindNormalLocation(schedule)
        if (isRouteSchedule) {
            bindBestEffortRouteLocation(schedule, routeDetail)
        }
        bindMemo(schedule.memo)
    }

    private fun bindBestEffortRouteReminderTexts(
        schedule: Schedule,
        routeDetail: com.example.pace.data.model.response.ScheduleDetailResponse?
    ) {
        if (routeDetail != null) {
            bindRouteReminderTexts(routeDetail.reminders)
            return
        }

        binding.tvReminder.text = formatReminderText(schedule.reminders)
        binding.viewReminderDivider.visibility = View.VISIBLE
        binding.layoutDepartureReminder.visibility = View.VISIBLE
        binding.tvDepartureReminder.text = formatDepartureReminderText(schedule.departureReminders)
    }

    private fun bindBestEffortRouteLocation(
        schedule: Schedule,
        routeDetail: com.example.pace.data.model.response.ScheduleDetailResponse?
    ) {
        if (routeDetail != null) {
            bindRouteSpecificInfo(schedule, routeDetail.route, routeDetail.reminders)
            return
        }

        val localRoute = schedule.routeJson?.let {
            runCatching { gson.fromJson(it, RouteInfo::class.java) }.getOrNull()
        }
        val routeLabel = localRoute?.let { "${it.originName} -> ${it.destName}" }
            ?: schedule.location
            ?: "경로 정보 없음"
        binding.layoutNormalLocation.visibility = View.VISIBLE
        binding.tvLocationStatus.text = routeLabel
        bindRouteInfo(localRoute, schedule.location ?: "도착지")
    }

    private fun bindRouteSpecificInfo(
        schedule: Schedule,
        routeInfo: RouteInfo?,
        reminders: List<ReminderInfo>
    ) {
        val localRoute = schedule.routeJson?.let {
            runCatching { gson.fromJson(it, RouteInfo::class.java) }.getOrNull()
        }
        val route = routeInfo ?: localRoute
        val routeLabel = route?.let { "${it.originName} -> ${it.destName}" }
            ?: schedule.location
            ?: "경로 정보 없음"
        binding.layoutNormalLocation.visibility = View.VISIBLE
        binding.tvLocationStatus.text = routeLabel
        bindRouteInfo(route, schedule.location ?: "도착지")
        bindRouteReminderTexts(reminders)
    }

    private fun bindNormalLocation(schedule: Schedule) {
        if (schedule.type == "ROUTE") {
            val route = schedule.routeJson?.let {
                runCatching { gson.fromJson(it, RouteInfo::class.java) }.getOrNull()
            }
            binding.layoutNormalLocation.visibility = View.VISIBLE
            binding.tvLocationStatus.text = route?.let { "${it.originName} -> ${it.destName}" }
                ?: (schedule.location ?: "경로 정보 없음")
            bindRouteInfo(route, schedule.location ?: "도착지")
        } else {
            binding.layoutNormalLocation.visibility = View.VISIBLE
            binding.layoutRouteInfo.visibility = View.GONE
            binding.tvLocationStatus.text = schedule.location?.takeIf { it.isNotBlank() } ?: "장소 없음"
        }
    }

    private fun bindRouteInfo(route: RouteInfo?, fallbackEndName: String) {
        if (route == null || route.routeDetails.isNullOrEmpty()) {
            binding.layoutNormalLocation.visibility = View.VISIBLE
            binding.layoutRouteInfo.visibility = View.GONE
            return
        }

        binding.layoutRouteInfo.visibility = View.VISIBLE
        binding.tvRouteRange.text =
            "${RouteCalculator.convertUtcToKst(route.departureTime)} - ${RouteCalculator.convertUtcToKst(route.arrivalTime)}"
        binding.tvRouteTotalTime.text = formatRouteDuration(route.totalTime)

        binding.routeBriefContainer.removeAllViews()
        binding.routeVehicleContainer.removeAllViews()

        route.routeDetails.forEachIndexed { index, detail ->
            val briefBinding = ItemRouteDetailBriefBinding.inflate(layoutInflater)

            if (detail.transitDetail == null) {
                if (detail.sequence == 1) {
                    briefBinding.itemRouteDetailBriefIv.setImageResource(R.drawable.ic_people)
                } else {
                    briefBinding.itemRouteDetailBriefIv.visibility = View.GONE
                    briefBinding.itemRouteDetailBriefTv.setPadding(0, 0, 0, 0)
                }
                briefBinding.itemRouteDetailBriefTv.text = "${detail.duration / 60}분"
                briefBinding.itemRouteDetailBriefTv.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.gray_600)
                )

                if (index == route.routeDetails.lastIndex) {
                    val arrivalBinding = ItemRouteVehicleBinding.inflate(layoutInflater)
                    arrivalBinding.itemRouteVehicleIv.setImageResource(R.drawable.ic_route_item_arrival_icon)
                    arrivalBinding.itemRouteVehicleLineTv.text = "도착"
                    arrivalBinding.itemRouteVehicleLineTv.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.black)
                    )
                    arrivalBinding.itemRouteVehicleView.visibility = View.GONE
                    arrivalBinding.itemRouteVehicleTv.text = route.destName.ifBlank { fallbackEndName }
                    binding.routeVehicleContainer.addView(arrivalBinding.root)
                }
            } else {
                val vehicleBinding = ItemRouteVehicleBinding.inflate(layoutInflater)
                val layoutDrawable = ContextCompat.getDrawable(
                    requireContext(),
                    R.drawable.ic_route_detail
                )?.mutate() as LayerDrawable
                val iconShape = layoutDrawable.findDrawableByLayerId(R.id.ic_route_detail_color)
                    .mutate() as GradientDrawable
                val briefBg = briefBinding.itemRouteDetailBriefTv.background.mutate() as GradientDrawable

                val lineColorCode = try {
                    Color.parseColor(detail.transitDetail.lineColor ?: "#000000")
                } catch (_: Exception) {
                    Color.BLACK
                }

                when (detail.transitDetail.transitType) {
                    "BUS" -> {
                        val busDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.ic_bus)
                        layoutDrawable.setDrawableByLayerId(R.id.ic_route_detail_vehicle, busDrawable)
                    }

                    "SUBWAY" -> {
                        val subwayDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.ic_subway)
                        layoutDrawable.setDrawableByLayerId(R.id.ic_route_detail_vehicle, subwayDrawable)
                    }
                }

                iconShape.setColor(lineColorCode)
                briefBg.setColor(lineColorCode)

                briefBinding.itemRouteDetailBriefIv.setImageDrawable(layoutDrawable)
                briefBinding.itemRouteDetailBriefTv.text = "${detail.duration / 60}분"

                vehicleBinding.itemRouteVehicleIv.setImageDrawable(layoutDrawable)
                vehicleBinding.itemRouteVehicleLineTv.text = detail.transitDetail.lineName
                vehicleBinding.itemRouteVehicleLineTv.setTextColor(lineColorCode)
                vehicleBinding.itemRouteVehicleTv.text = "${detail.transitDetail.departureStop} 승차"
                binding.routeVehicleContainer.addView(vehicleBinding.root)
            }

            val weight = RouteCalculator.calculateWeight(detail.duration)
            val params = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight)
            binding.routeBriefContainer.addView(briefBinding.root, params)
        }
    }

    private fun formatRouteDuration(totalSeconds: Int): String {
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        return if (hours > 0) {
            if (minutes > 0) "${hours}시간 ${minutes}분" else "${hours}시간"
        } else {
            "${minutes}분"
        }
    }

    private fun bindNormalReminderText(text: String) {
        binding.viewReminderDivider.visibility = View.GONE
        binding.layoutDepartureReminder.visibility = View.GONE
        binding.tvReminder.text = text
    }

    private fun bindRouteReminderTexts(reminders: List<ReminderInfo>) {
        val eventReminders = reminders.filter { it.reminderType == "EVENT" }
        val departureReminders = reminders.filter { it.reminderType == "DEPARTURE" }

        binding.tvReminder.text = formatRouteReminderText(eventReminders, "일정 알림 안함")
        binding.viewReminderDivider.visibility = View.VISIBLE
        binding.layoutDepartureReminder.visibility = View.VISIBLE
        binding.tvDepartureReminder.text = formatRouteDepartureReminderText(departureReminders)
    }

    private fun bindMemo(memo: String?) {
        binding.etMemo.text = memo?.takeIf { it.isNotBlank() } ?: "메모 없음"
    }

    private fun buildRepeatText(schedule: Schedule): String {
        val rRule = schedule.repeatRule ?: return "반복 일정"
        val repeatInfo = viewModel.parseRepeatRule(rRule, schedule.endDate)
        return repeatInfo?.let(viewModel::getRepeatDescription) ?: "반복 일정"
    }

    private fun formatReminderText(reminders: List<Int>): String {
        if (reminders.isEmpty()) return "일정 알림 안함"
        return reminders.sorted().joinToString(", ") { minutes ->
            when {
                minutes == 0 -> "일정 시작 시간"
                minutes < 60 -> "${minutes}분 전"
                minutes < 1440 -> "${minutes / 60}시간 전"
                else -> "${minutes / 1440}일 전"
            }
        }
    }

    private fun formatDepartureReminderText(reminders: List<Int>): String {
        if (reminders.isEmpty()) return "출발 알림 안함"
        val formatted = reminders.sorted().map { minutes ->
            when {
                minutes == 0 -> "출발 시각"
                minutes < 60 -> "${minutes}분 전"
                minutes < 1440 -> "${minutes / 60}시간 전"
                else -> "${minutes / 1440}일 전"
            }
        }
        return if (formatted.any { it == "출발 시각" }) {
            formatted.joinToString(", ")
        } else {
            "출발 ${formatted.joinToString(", ")}"
        }
    }

    private fun formatRouteReminderText(reminders: List<ReminderInfo>, emptyText: String): String {
        if (reminders.isEmpty()) return emptyText
        return reminders.sortedBy { it.minutesBefore }.joinToString(", ") { reminder ->
            when {
                reminder.reminderType == "DEPARTURE" && reminder.minutesBefore == 0 -> "출발 시각"
                reminder.reminderType == "DEPARTURE" && reminder.minutesBefore < 60 -> "${reminder.minutesBefore}분 전"
                reminder.reminderType == "DEPARTURE" && reminder.minutesBefore < 1440 -> "${reminder.minutesBefore / 60}시간 전"
                reminder.reminderType == "DEPARTURE" -> "${reminder.minutesBefore / 1440}일 전"
                reminder.minutesBefore == 0 -> "일정 시작 시간"
                reminder.minutesBefore < 60 -> "${reminder.minutesBefore}분 전"
                reminder.minutesBefore < 1440 -> "${reminder.minutesBefore / 60}시간 전"
                else -> "${reminder.minutesBefore / 1440}일 전"
            }
        }
    }

    private fun formatRouteDepartureReminderText(reminders: List<ReminderInfo>): String {
        if (reminders.isEmpty()) return "출발 알림 안함"
        val formatted = reminders.sortedBy { it.minutesBefore }.map { reminder ->
            when {
                reminder.minutesBefore == 0 -> "출발 시각"
                reminder.minutesBefore < 60 -> "${reminder.minutesBefore}분 전"
                reminder.minutesBefore < 1440 -> "${reminder.minutesBefore / 60}시간 전"
                else -> "${reminder.minutesBefore / 1440}일 전"
            }
        }
        return if (formatted.any { it == "출발 시각" }) {
            formatted.joinToString(", ")
        } else {
            "출발 ${formatted.joinToString(", ")}"
        }
    }

    private fun resolveDisplayStartDate(schedule: Schedule): LocalDate {
        return when {
            schedule.repeatRule.isNullOrEmpty() -> parseDate(schedule.startDate)
            currentOccurrenceDate.isNotBlank() -> parseDate(currentOccurrenceDate)
            else -> parseDate(schedule.startDate)
        }
    }

    private fun resolveDisplayEndDate(schedule: Schedule, displayStartDate: LocalDate): LocalDate {
        if (schedule.repeatRule.isNullOrEmpty()) {
            return parseDate(schedule.endDate)
        }

        val originalStart = parseDate(schedule.startDate)
        val originalEnd = parseDate(schedule.endDate)
        val span = ChronoUnit.DAYS.between(originalStart, originalEnd).coerceAtLeast(0)
        return displayStartDate.plusDays(span)
    }

    private fun parseDate(raw: String): LocalDate {
        return LocalDate.parse(raw.take(10))
    }

    private fun formatDate(date: LocalDate): String {
        return date.format(DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN))
    }

    private fun showDeleteDialog(schedule: Schedule) {
        when {
            schedule.type == "ROUTE" -> {
                DeleteScheduleDialog(requireContext()).apply {
                    setOnConfirmListener {
                        viewModel.deleteSchedule(schedule.id, withRoute = true)
                        requireActivity().supportFragmentManager.popBackStack()
                    }
                }.show()
            }

            !schedule.repeatRule.isNullOrEmpty() -> {
                DeleteRepeatScheduleDialog(requireContext()).apply {
                    setOnOptionSelectedListener { option ->
                        when (option) {
                            "ONLY_THIS" -> viewModel.deleteOnlyThisOccurrence(schedule, parseDate(currentOccurrenceDate))
                            "ALL" -> viewModel.deleteSchedule(schedule.id, withRoute = false)
                        }
                        requireActivity().supportFragmentManager.popBackStack()
                    }
                }.show()
            }

            else -> {
                DeleteScheduleDialog(requireContext()).apply {
                    setOnConfirmListener {
                        viewModel.deleteSchedule(schedule.id, withRoute = false)
                        requireActivity().supportFragmentManager.popBackStack()
                    }
                }.show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        (requireActivity() as MainActivity).binding.mainOverlayFcv.visibility = View.GONE
        _binding = null
    }

    companion object {
        private const val ARG_SCHEDULE_ID = "schedule_id"
        private const val ARG_OCCURRENCE_DATE = "occurrence_date"
        private const val ARG_SCHEDULE_TYPE = "schedule_type"

        fun newInstance(scheduleId: Long, occurrenceDate: String, scheduleType: String): ScheduleDetailFragment {
            return ScheduleDetailFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_SCHEDULE_ID, scheduleId)
                    putString(ARG_OCCURRENCE_DATE, occurrenceDate)
                    putString(ARG_SCHEDULE_TYPE, scheduleType)
                }
            }
        }
    }
}

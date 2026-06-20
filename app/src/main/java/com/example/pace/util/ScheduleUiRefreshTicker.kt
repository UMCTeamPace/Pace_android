package com.example.pace.util

import android.os.Handler
import android.os.Looper
import com.example.pace.data.model.Schedule
import com.example.pace.data.model.response.RouteInfo

class ScheduleUiRefreshTicker(
    private val onRefresh: (ScheduleRefreshReason) -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private var scheduledReason: ScheduleRefreshReason? = null
    private val refreshRunnable = Runnable {
        scheduledReason?.let(onRefresh)
    }

    fun schedule(
        schedules: List<Schedule>,
        routeInfoMap: Map<Long, RouteInfo> = emptyMap(),
        includeCountdown: Boolean = true
    ) {
        cancel()
        val refresh = listOfNotNull(
            ScheduleItemStyleUtils.nextPastTimedRefreshDelayMillis(schedules)
                ?.let { ScheduledRefresh(it, ScheduleRefreshReason.PAST_STATUS) },
            ScheduleCountdownUtils.nextRouteArrivalRefreshDelayMillis(schedules, routeInfoMap)
                ?.let { ScheduledRefresh(it, ScheduleRefreshReason.PAST_STATUS) },
            if (includeCountdown) {
                ScheduleCountdownUtils.nextCountdownRefreshDelayMillis(schedules, routeInfoMap)
                    ?.let { ScheduledRefresh(it, ScheduleRefreshReason.COUNTDOWN) }
            } else {
                null
            }
        ).minWithOrNull(
            compareBy<ScheduledRefresh> { it.delayMillis }
                .thenBy { if (it.reason == ScheduleRefreshReason.PAST_STATUS) 0 else 1 }
        ) ?: return

        scheduledReason = refresh.reason
        handler.postDelayed(refreshRunnable, refresh.delayMillis)
    }

    fun cancel() {
        handler.removeCallbacks(refreshRunnable)
        scheduledReason = null
    }

    private data class ScheduledRefresh(
        val delayMillis: Long,
        val reason: ScheduleRefreshReason
    )
}

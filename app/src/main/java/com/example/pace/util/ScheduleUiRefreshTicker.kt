package com.example.pace.util

import android.os.Handler
import android.os.Looper
import com.example.pace.data.model.Schedule

class ScheduleUiRefreshTicker(
    private val onRefresh: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private val refreshRunnable = Runnable { onRefresh() }

    fun schedule(schedules: List<Schedule>) {
        cancel()
        val delayMillis = ScheduleItemStyleUtils.nextPastTimedRefreshDelayMillis(schedules) ?: return
        handler.postDelayed(refreshRunnable, delayMillis)
    }

    fun cancel() {
        handler.removeCallbacks(refreshRunnable)
    }
}
